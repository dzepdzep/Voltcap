package com.shelf.launcher.data.ra

import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Why a RetroAchievements request failed — enough for the UI to say something honest. */
enum class RaErrorKind { NETWORK, AUTH, NOT_FOUND, RATE_LIMITED, SERVER, PARSE, UNKNOWN }

data class RaError(val kind: RaErrorKind, val message: String, val httpCode: Int? = null) {
    /** Worth trying again later (a bad key or a missing game won't fix itself). */
    val retryable: Boolean get() = kind != RaErrorKind.AUTH && kind != RaErrorKind.NOT_FOUND
}

/**
 * Outcome of a RetroAchievements call. Unlike the old "empty list on failure",
 * callers can tell "nothing there" from "couldn't ask", so a failed refresh
 * never overwrites good saved data and is never cached as an empty result.
 */
sealed class RaResult<out T> {
    data class Ok<T>(val value: T, val fetchedAt: Long = System.currentTimeMillis()) : RaResult<T>()
    data class Error(val error: RaError) : RaResult<Nothing>()

    val okValue: T? get() = (this as? Ok<T>)?.value
    val errorOrNull: RaError? get() = (this as? Error)?.error
}

/** Runs [block], turning any failure into [RaResult.Error] (cancellation still propagates). */
suspend fun <T> raCall(block: suspend () -> T): RaResult<T> = try {
    RaResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    RaResult.Error(classifyRaError(e))
}

fun classifyRaError(e: Throwable): RaError = when (e) {
    is HttpException -> when (val code = e.code()) {
        401, 403 -> RaError(RaErrorKind.AUTH, "RetroAchievements rejected the username or web API key.", code)
        404 -> RaError(RaErrorKind.NOT_FOUND, "RetroAchievements doesn't have that game.", code)
        429 -> RaError(RaErrorKind.RATE_LIMITED, "RetroAchievements is rate-limiting requests — try again in a minute.", code)
        in 500..599 -> RaError(RaErrorKind.SERVER, "RetroAchievements had a server problem (HTTP $code).", code)
        else -> RaError(RaErrorKind.UNKNOWN, "RetroAchievements answered with HTTP $code.", code)
    }
    is IOException -> RaError(RaErrorKind.NETWORK, "Couldn't reach RetroAchievements — check the connection.")
    is JsonParseException -> RaError(RaErrorKind.PARSE, "RetroAchievements sent an answer Voltcap couldn't read.")
    else -> RaError(RaErrorKind.UNKNOWN, "Unexpected error talking to RetroAchievements.")
}

/** Wording for the "couldn't refresh" line shown next to saved data. */
object RaStatusText {
    /**
     * [error] the last failed refresh (null = last refresh worked), [fetchedAt]
     * when the data being shown was fetched (null = unknown), [stale] older than
     * the refresh window. Returns null when there's nothing to say.
     */
    fun detailsNote(error: RaError?, fetchedAt: Long?, stale: Boolean, hasData: Boolean): String? {
        val when_ = fetchedAt?.let { " from " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }.orEmpty()
        return when {
            error != null && hasData -> "Couldn't refresh — showing saved data$when_. ${error.message}"
            error != null -> "Couldn't load achievements. ${error.message}"
            stale && hasData -> "Showing saved data$when_ — refreshing when online."
            else -> null
        }
    }
}
