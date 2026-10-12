// Voltcap ↔ Discord Social SDK bridge.
//
// Shows "Playing <game>" on the player's Discord profile. Uses the SDK's C
// API, looked up at run time from libdiscord_partner_sdk.so (which ships
// inside Discord's .aar), so the build only needs the header — no linking
// against the library. Without an account link, the SDK talks to the
// Discord app on the same device, which must be installed and signed in.
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <cstring>
#include <string>
#include "cdiscord.h"

#define TAG "VoltcapDiscord"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

void* sdk = nullptr;
Discord_Client client{};
bool clientReady = false;
bool authAvailable = false;
uint64_t appId = 0;

// Linking state, read by Kotlin: 0 not linked, 1 working, 2 linked, 3 failed.
int authState = 0;
std::string authError;
// Newest refresh token, handed to Kotlin once to save (then cleared).
std::string pendingRefreshToken;
bool hasPendingRefreshToken = false;
// The PKCE verifier of the sign-in in progress.
std::string codeVerifier;

template <typename T>
bool load(T& fn, const char* name) {
    fn = reinterpret_cast<T>(dlsym(sdk, name));
    if (!fn) LOGW("Missing %s in the Discord SDK", name);
    return fn != nullptr;
}

// Everything used, resolved once.
decltype(&Discord_RunCallbacks) RunCallbacks;
decltype(&Discord_Client_Init) ClientInit;
decltype(&Discord_Client_SetApplicationId) SetApplicationId;
decltype(&Discord_Client_UpdateRichPresence) UpdateRichPresence;
decltype(&Discord_Client_ClearRichPresence) ClearRichPresence;
decltype(&Discord_Client_GetStatus) GetStatus;
decltype(&Discord_Activity_Init) ActivityInit;
decltype(&Discord_Activity_Drop) ActivityDrop;
decltype(&Discord_Activity_SetType) ActivitySetType;
decltype(&Discord_Activity_SetDetails) ActivitySetDetails;
decltype(&Discord_Activity_SetState) ActivitySetState;
decltype(&Discord_Activity_SetStatusDisplayType) ActivitySetStatusDisplayType;
decltype(&Discord_Activity_SetAssets) ActivitySetAssets;
decltype(&Discord_Activity_SetTimestamps) ActivitySetTimestamps;
decltype(&Discord_ActivityAssets_Init) AssetsInit;
decltype(&Discord_ActivityAssets_Drop) AssetsDrop;
decltype(&Discord_ActivityAssets_SetLargeImage) AssetsSetLargeImage;
decltype(&Discord_ActivityAssets_SetLargeText) AssetsSetLargeText;
decltype(&Discord_ActivityAssets_SetSmallImage) AssetsSetSmallImage;
decltype(&Discord_ActivityAssets_SetSmallText) AssetsSetSmallText;
decltype(&Discord_ActivityTimestamps_Init) TimestampsInit;
decltype(&Discord_ActivityTimestamps_Drop) TimestampsDrop;
decltype(&Discord_ActivityTimestamps_SetStart) TimestampsSetStart;
decltype(&Discord_ClientResult_Successful) ResultSuccessful;
decltype(&Discord_ClientResult_Error) ResultError;
// Account linking ("Connect Discord") — mobile needs it for Rich Presence.
decltype(&Discord_Free) Free;
decltype(&Discord_Client_GetDefaultPresenceScopes) GetDefaultPresenceScopes;
decltype(&Discord_Client_CreateAuthorizationCodeVerifier) CreateVerifier;
decltype(&Discord_AuthorizationCodeVerifier_Challenge) VerifierChallenge;
decltype(&Discord_AuthorizationCodeVerifier_Verifier) VerifierVerifier;
decltype(&Discord_AuthorizationCodeVerifier_Drop) VerifierDrop;
decltype(&Discord_AuthorizationCodeChallenge_Drop) ChallengeDrop;
decltype(&Discord_AuthorizationArgs_Init) ArgsInit;
decltype(&Discord_AuthorizationArgs_Drop) ArgsDrop;
decltype(&Discord_AuthorizationArgs_SetClientId) ArgsSetClientId;
decltype(&Discord_AuthorizationArgs_SetScopes) ArgsSetScopes;
decltype(&Discord_AuthorizationArgs_SetCodeChallenge) ArgsSetCodeChallenge;
decltype(&Discord_Client_Authorize) Authorize;
decltype(&Discord_Client_GetToken) GetToken;
decltype(&Discord_Client_RefreshToken) RefreshToken;
decltype(&Discord_Client_UpdateToken) UpdateToken;
decltype(&Discord_Client_Connect) Connect;
decltype(&Discord_Client_IsAuthenticated) IsAuthenticated;

bool resolveAll() {
    if (sdk) return true;
    sdk = dlopen("libdiscord_partner_sdk.so", RTLD_NOW);
    if (!sdk) {
        LOGW("Couldn't open the Discord SDK: %s", dlerror());
        return false;
    }
    bool ok = true;
    ok &= load(RunCallbacks, "Discord_RunCallbacks");
    ok &= load(ClientInit, "Discord_Client_Init");
    ok &= load(SetApplicationId, "Discord_Client_SetApplicationId");
    ok &= load(UpdateRichPresence, "Discord_Client_UpdateRichPresence");
    ok &= load(ClearRichPresence, "Discord_Client_ClearRichPresence");
    ok &= load(GetStatus, "Discord_Client_GetStatus");
    ok &= load(ActivityInit, "Discord_Activity_Init");
    ok &= load(ActivityDrop, "Discord_Activity_Drop");
    ok &= load(ActivitySetType, "Discord_Activity_SetType");
    ok &= load(ActivitySetDetails, "Discord_Activity_SetDetails");
    ok &= load(ActivitySetState, "Discord_Activity_SetState");
    load(ActivitySetStatusDisplayType, "Discord_Activity_SetStatusDisplayType"); // newer SDKs only
    ok &= load(ActivitySetAssets, "Discord_Activity_SetAssets");
    ok &= load(ActivitySetTimestamps, "Discord_Activity_SetTimestamps");
    ok &= load(AssetsInit, "Discord_ActivityAssets_Init");
    ok &= load(AssetsDrop, "Discord_ActivityAssets_Drop");
    ok &= load(AssetsSetLargeImage, "Discord_ActivityAssets_SetLargeImage");
    ok &= load(AssetsSetLargeText, "Discord_ActivityAssets_SetLargeText");
    ok &= load(AssetsSetSmallImage, "Discord_ActivityAssets_SetSmallImage");
    ok &= load(AssetsSetSmallText, "Discord_ActivityAssets_SetSmallText");
    ok &= load(TimestampsInit, "Discord_ActivityTimestamps_Init");
    ok &= load(TimestampsDrop, "Discord_ActivityTimestamps_Drop");
    ok &= load(TimestampsSetStart, "Discord_ActivityTimestamps_SetStart");
    ok &= load(ResultSuccessful, "Discord_ClientResult_Successful");
    ok &= load(ResultError, "Discord_ClientResult_Error");
    // Linking is optional: without it presence still tries the local app.
    authAvailable = true;
    authAvailable &= load(Free, "Discord_Free");
    authAvailable &= load(GetDefaultPresenceScopes, "Discord_Client_GetDefaultPresenceScopes");
    authAvailable &= load(CreateVerifier, "Discord_Client_CreateAuthorizationCodeVerifier");
    authAvailable &= load(VerifierChallenge, "Discord_AuthorizationCodeVerifier_Challenge");
    authAvailable &= load(VerifierVerifier, "Discord_AuthorizationCodeVerifier_Verifier");
    authAvailable &= load(VerifierDrop, "Discord_AuthorizationCodeVerifier_Drop");
    authAvailable &= load(ChallengeDrop, "Discord_AuthorizationCodeChallenge_Drop");
    authAvailable &= load(ArgsInit, "Discord_AuthorizationArgs_Init");
    authAvailable &= load(ArgsDrop, "Discord_AuthorizationArgs_Drop");
    authAvailable &= load(ArgsSetClientId, "Discord_AuthorizationArgs_SetClientId");
    authAvailable &= load(ArgsSetScopes, "Discord_AuthorizationArgs_SetScopes");
    authAvailable &= load(ArgsSetCodeChallenge, "Discord_AuthorizationArgs_SetCodeChallenge");
    authAvailable &= load(Authorize, "Discord_Client_Authorize");
    authAvailable &= load(GetToken, "Discord_Client_GetToken");
    authAvailable &= load(RefreshToken, "Discord_Client_RefreshToken");
    authAvailable &= load(UpdateToken, "Discord_Client_UpdateToken");
    authAvailable &= load(Connect, "Discord_Client_Connect");
    authAvailable &= load(IsAuthenticated, "Discord_Client_IsAuthenticated");
    if (!ok) {
        dlclose(sdk);
        sdk = nullptr;
    }
    return ok;
}

/** A Java string as UTF-8; empty when null. */
std::string utf8(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

/** Borrowed view for the C API (valid while [s] lives). */
Discord_String view(std::string& s) {
    return Discord_String{reinterpret_cast<uint8_t*>(s.data()), s.size()};
}

void onPresenceResult(Discord_ClientResult* result, void*) {
    if (ResultSuccessful(result)) {
        LOGI("Rich presence updated");
    } else {
        Discord_String error{};
        ResultError(result, &error);
        LOGW("Rich presence failed: %.*s", static_cast<int>(error.size), error.ptr);
    }
}

std::string fromDiscord(Discord_String s) {
    return std::string(reinterpret_cast<const char*>(s.ptr), s.size);
}

void failAuth(Discord_ClientResult* result, const char* where) {
    Discord_String error{};
    ResultError(result, &error);
    authError = std::string(where) + ": " + fromDiscord(error);
    if (error.ptr) Free(error.ptr);
    LOGW("%s", authError.c_str());
    authState = 3;
}

void onTokenUpdated(Discord_ClientResult* result, void*) {
    if (!ResultSuccessful(result)) { failAuth(result, "UpdateToken"); return; }
    Connect(&client);
    authState = 2;
    LOGI("Discord account linked, connecting");
}

void onToken(Discord_ClientResult* result, Discord_String accessToken, Discord_String refreshToken,
             Discord_AuthorizationTokenType tokenType, int32_t, Discord_String, void*) {
    if (!ResultSuccessful(result)) { failAuth(result, "GetToken"); return; }
    pendingRefreshToken = fromDiscord(refreshToken);
    hasPendingRefreshToken = !pendingRefreshToken.empty();
    std::string access = fromDiscord(accessToken);
    UpdateToken(&client, tokenType, view(access), onTokenUpdated, nullptr, nullptr);
}

void onAuthorized(Discord_ClientResult* result, Discord_String code, Discord_String redirectUri, void*) {
    if (!ResultSuccessful(result)) { failAuth(result, "Authorize"); return; }
    std::string c = fromDiscord(code), r = fromDiscord(redirectUri);
    GetToken(&client, appId, view(c), view(codeVerifier), view(r), onToken, nullptr, nullptr);
}

}  // namespace

extern "C" {

/** Opens Discord's sign-in (the Discord app or a browser tab). */
JNIEXPORT jboolean JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeAuthorize(JNIEnv*, jobject) {
    if (!clientReady || !authAvailable) return JNI_FALSE;
    authState = 1;
    authError.clear();

    Discord_AuthorizationCodeVerifier verifier{};
    CreateVerifier(&client, &verifier);
    Discord_String v{};
    VerifierVerifier(&verifier, &v);
    codeVerifier = fromDiscord(v);
    if (v.ptr) Free(v.ptr);
    Discord_AuthorizationCodeChallenge challenge{};
    VerifierChallenge(&verifier, &challenge);

    Discord_String scopes{};
    GetDefaultPresenceScopes(&scopes);

    Discord_AuthorizationArgs args{};
    ArgsInit(&args);
    ArgsSetClientId(&args, appId);
    ArgsSetScopes(&args, scopes);
    ArgsSetCodeChallenge(&args, &challenge);
    Authorize(&client, &args, onAuthorized, nullptr, nullptr);

    ArgsDrop(&args);
    if (scopes.ptr) Free(scopes.ptr);
    ChallengeDrop(&challenge);
    VerifierDrop(&verifier);
    return JNI_TRUE;
}

/** Signs back in with a saved refresh token (no screen). */
JNIEXPORT jboolean JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeRefresh(JNIEnv* env, jobject, jstring jToken) {
    if (!clientReady || !authAvailable) return JNI_FALSE;
    std::string token = utf8(env, jToken);
    if (token.empty()) return JNI_FALSE;
    authState = 1;
    RefreshToken(&client, appId, view(token), onToken, nullptr, nullptr);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeAuthState(JNIEnv*, jobject) {
    return authState;
}

JNIEXPORT jstring JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeAuthError(JNIEnv* env, jobject) {
    return env->NewStringUTF(authError.c_str());
}

/** The newest refresh token once (then null), for Kotlin to save. */
JNIEXPORT jstring JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeTakeRefreshToken(JNIEnv* env, jobject) {
    if (!hasPendingRefreshToken) return nullptr;
    hasPendingRefreshToken = false;
    return env->NewStringUTF(pendingRefreshToken.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeInit(JNIEnv*, jobject, jlong applicationId) {
    if (clientReady) return JNI_TRUE;
    if (!resolveAll()) return JNI_FALSE;
    ClientInit(&client);
    appId = static_cast<uint64_t>(applicationId);
    SetApplicationId(&client, appId);
    clientReady = true;
    LOGI("Discord client ready");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeRunCallbacks(JNIEnv*, jobject) {
    if (clientReady) RunCallbacks();
}

JNIEXPORT jint JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeStatus(JNIEnv*, jobject) {
    return clientReady ? static_cast<jint>(GetStatus(&client)) : -1;
}

JNIEXPORT void JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeSetPlaying(
    JNIEnv* env, jobject, jstring jDetails, jstring jState, jlong startSeconds,
    jstring jLargeImage, jstring jLargeText, jstring jSmallImage, jstring jSmallText) {
    if (!clientReady) return;
    std::string details = utf8(env, jDetails);
    std::string state = utf8(env, jState);
    std::string largeImage = utf8(env, jLargeImage);
    std::string largeText = utf8(env, jLargeText);
    std::string smallImage = utf8(env, jSmallImage);
    std::string smallText = utf8(env, jSmallText);

    Discord_Activity activity{};
    ActivityInit(&activity);
    ActivitySetType(&activity, Discord_ActivityTypes_Playing);
    Discord_String d = view(details);
    ActivitySetDetails(&activity, &d);
    Discord_String st = view(state);
    if (!state.empty()) ActivitySetState(&activity, &st);
    if (ActivitySetStatusDisplayType) {
        // "Playing GoldenEye 007" in the member list, not "Playing Voltcap".
        Discord_StatusDisplayTypes type = Discord_StatusDisplayTypes_Details;
        ActivitySetStatusDisplayType(&activity, &type);
    }

    Discord_ActivityAssets assets{};
    AssetsInit(&assets);
    Discord_String li = view(largeImage), lt = view(largeText), si = view(smallImage), stx = view(smallText);
    if (!largeImage.empty()) AssetsSetLargeImage(&assets, &li);
    if (!largeText.empty()) AssetsSetLargeText(&assets, &lt);
    if (!smallImage.empty()) AssetsSetSmallImage(&assets, &si);
    if (!smallText.empty()) AssetsSetSmallText(&assets, &stx);
    ActivitySetAssets(&activity, &assets);

    Discord_ActivityTimestamps timestamps{};
    TimestampsInit(&timestamps);
    if (startSeconds > 0) TimestampsSetStart(&timestamps, static_cast<uint64_t>(startSeconds));
    ActivitySetTimestamps(&activity, &timestamps);

    UpdateRichPresence(&client, &activity, onPresenceResult, nullptr, nullptr);

    TimestampsDrop(&timestamps);
    AssetsDrop(&assets);
    ActivityDrop(&activity);
}

JNIEXPORT void JNICALL
Java_com_shelf_launcher_data_DiscordPresence_nativeClear(JNIEnv*, jobject) {
    if (clientReady) ClearRichPresence(&client);
}

}  // extern "C"
