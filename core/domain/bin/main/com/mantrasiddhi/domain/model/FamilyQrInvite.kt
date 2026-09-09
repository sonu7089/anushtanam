package com.mantrasiddhi.domain.model

data class FamilyQrInvite(val token: String, val expiresAtMillis: Long, val used: Boolean = false) {
    val payload: String get() = PREFIX + token

    companion object {
        const val LIFETIME_MILLIS = 5 * 60 * 1000L
        private const val PREFIX = "anushtanam://family-link/v1/"
        fun tokenFrom(payload: String): String? = payload.removePrefix(PREFIX)
            .takeIf { payload.startsWith(PREFIX) && it.matches(Regex("[a-f0-9]{64}")) }
    }
}

enum class FamilyLinkProblem { INVALID_CREDENTIALS, INVALID_QR, QR_UNAVAILABLE, OFFLINE, TOO_MANY_ATTEMPTS, WRONG_ACCOUNT, UNAVAILABLE }

class FamilyLinkFailure(val problem: FamilyLinkProblem, cause: Throwable? = null) : Exception(problem.name, cause)
