package com.dominiqueherbrigpersonalteam.lademonitor.data.remote

import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import android.util.Log
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/** Kotlin port of the iOS `APIError` enum. [message] is always a user-facing, localized string. */
sealed class ApiException(message: String) : Exception(message) {
    object NotConfigured : ApiException(
        LademonitorApp.appContext.getString(R.string.api_error_not_configured)
    )

    object InvalidResponse : ApiException(
        LademonitorApp.appContext.getString(R.string.api_error_invalid_response)
    )

    class Server(val statusCode: Int, val serverMessage: String) :
        ApiException(
            LademonitorApp.appContext.getString(R.string.api_error_server, statusCode, serverMessage)
        )

    /**
     * The response arrived but did not fit the app's model. The technical cause (Moshi adapter,
     * class and field names) goes to the log and stays out of the message: it means nothing to the
     * user and reads like internals. It is kept as [cause] for debugging.
     */
    class Decoding(cause: Throwable) :
        ApiException(LademonitorApp.appContext.getString(R.string.api_error_decoding)) {
        init {
            initCause(cause)
            Log.w(TAG, "Antwort konnte nicht dekodiert werden", cause)
        }
    }

    /** No connection to the server. Same reasoning as [Decoding]: no raw exception text. */
    class Network(cause: Throwable) :
        ApiException(
            LademonitorApp.appContext.getString(
                if (cause is SocketTimeoutException || cause is InterruptedIOException) {
                    R.string.api_error_network_timeout
                } else {
                    R.string.api_error_network
                }
            )
        ) {
        init {
            initCause(cause)
            Log.w(TAG, "Verbindung fehlgeschlagen", cause)
        }
    }

    /**
     * The text to show the user. For a server error it is the server's own `detail` (already
     * written for people, e.g. "wrong password"), except for internal errors or a body that is
     * not a message at all (HTML error page of a reverse proxy).
     */
    val userMessage: String
        get() = when (this) {
            is Server -> if (statusCode >= 500 || serverMessage.isBlank() || serverMessage.trimStart().startsWith("<")) {
                LademonitorApp.appContext.getString(R.string.api_error_server_generic)
            } else {
                serverMessage
            }
            else -> message.orEmpty()
        }

    private companion object {
        const val TAG = "ApiException"
    }
}

/** User-facing text for any error from an API call; never a raw exception or class name. */
fun Throwable.userFacingMessage(): String = when (this) {
    is ApiException -> userMessage
    else -> {
        Log.w("ApiException", "Unerwarteter Fehler", this)
        LademonitorApp.appContext.getString(R.string.api_error_unexpected)
    }
}
