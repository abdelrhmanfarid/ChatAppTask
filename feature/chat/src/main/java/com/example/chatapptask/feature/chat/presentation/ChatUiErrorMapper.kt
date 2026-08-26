package com.example.chatapptask.feature.chat.presentation

import androidx.annotation.StringRes
import com.example.chatapptask.feature.chat.R
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Presentation-boundary mapping from technical failures to user-facing string resources.
 * Never expose [Throwable.message] or infrastructure details to the UI.
 */
internal object ChatUiErrorMapper {
    @StringRes
    fun messageResId(
        throwable: Throwable,
        context: ChatErrorContext = ChatErrorContext.GENERIC,
    ): Int {
        val chain = throwable.causeChain()
        if (chain.any(::isOfflineFailure)) {
            return R.string.chat_error_offline
        }
        if (chain.any(::isTimeoutFailure)) {
            return R.string.chat_error_timeout
        }
        return when (context) {
            ChatErrorContext.SYNC,
            ChatErrorContext.LOAD_OLDER,
            ChatErrorContext.REALTIME,
            -> R.string.chat_error_sync

            ChatErrorContext.SEND,
            ChatErrorContext.RETRY,
            ChatErrorContext.IDENTITY,
            ChatErrorContext.GENERIC,
            -> R.string.chat_error_unexpected
        }
    }

    private fun isOfflineFailure(throwable: Throwable): Boolean {
        when (throwable) {
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException,
            -> return true
        }
        val message = throwable.message?.lowercase().orEmpty()
        return "unable to resolve host" in message ||
            "failed to connect" in message ||
            "network is unreachable" in message ||
            "no address associated with hostname" in message ||
            "software caused connection abort" in message
    }

    private fun isTimeoutFailure(throwable: Throwable): Boolean {
        if (throwable is SocketTimeoutException) return true
        val simpleName = throwable::class.simpleName.orEmpty()
        if (simpleName.contains("Timeout", ignoreCase = true)) return true
        val message = throwable.message?.lowercase().orEmpty()
        return "timed out" in message ||
            "timeout" in message ||
            "request timeout" in message
    }

    private fun Throwable.causeChain(): List<Throwable> {
        val chain = ArrayList<Throwable>()
        var current: Throwable? = this
        val seen = HashSet<Throwable>()
        while (current != null && seen.add(current)) {
            chain += current
            current = current.cause
        }
        return chain
    }
}

internal enum class ChatErrorContext {
    SYNC,
    LOAD_OLDER,
    REALTIME,
    SEND,
    RETRY,
    IDENTITY,
    GENERIC,
}
