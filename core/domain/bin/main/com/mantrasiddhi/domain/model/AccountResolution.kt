package com.mantrasiddhi.domain.model

/** Identity is known before its profile/role is resolved. Never treat that interval as sign-out. */
sealed interface AccountResolution {
    data class Unresolved(val uid: String? = null) : AccountResolution
    data object SignedOut : AccountResolution
    data class Resolved(val account: AuthAccount) : AccountResolution
}
