package com.discord.socialsdk;

// COMPILATION ONLY. Activated solely by GitHub Actions with
// -PvoltcapCiWithoutDiscord=true. This is NOT the real Discord SDK and
// MUST NEVER be bundled in a release or installed as a functional build.
public final class DiscordSocialSdkInit {
    private DiscordSocialSdkInit() {}

    public static void setEngineActivity(android.app.Activity activity) {
        throw new UnsupportedOperationException("CI compile stub only");
    }
}
