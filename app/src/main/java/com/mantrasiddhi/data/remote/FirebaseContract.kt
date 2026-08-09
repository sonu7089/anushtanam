/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

/**
 * Firestore collection names, field keys, and identity conventions for the Phase B
 * cloud backend. Kept in one place so the client and the security rules / Cloud
 * Function stay in lockstep.
 */
object FirebaseContract {
    // Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_CHILD_CODES = "childCodes"
    const val COLLECTION_LINKS = "links"
    const val COLLECTION_GOALS = "goals"
    /** One doc per uid mirroring the user's active SELF-target (restore on reinstall). */
    const val COLLECTION_SELF_TARGETS = "selfTargets"
    const val COLLECTION_PROGRESS = "progress"
    /** One doc per uid mirroring the sadhak's Purashcharana sankalpa (strictly private). */
    const val COLLECTION_PURASHCHARANA = "purashcharana"

    // users/{uid}
    const val FIELD_ROLE = "role"
    const val FIELD_DISPLAY_NAME = "displayName"
    const val FIELD_LINK_CODE = "linkCode"
    const val FIELD_FCM_TOKEN = "fcmToken"
    const val FIELD_CREATED_AT = "createdAt"

    // childCodes/{code}
    const val FIELD_UID = "uid"

    // links/{parentUid}_{childUid}
    const val FIELD_PARENT_UID = "parentUid"
    const val FIELD_CHILD_UID = "childUid"
    const val FIELD_CHILD_NAME = "childName"
    const val FIELD_PARENT_NAME = "parentName"
    const val FIELD_STATUS = "status"
    const val FIELD_UPDATED_AT = "updatedAt"

    // goals/{autoId}
    const val FIELD_REWARD_TYPE = "rewardType"
    const val FIELD_REWARD_DESCRIPTION = "rewardDescription"
    const val FIELD_TARGET_COUNT = "targetRecitationCount"
    const val FIELD_CURRENT_COUNT = "currentRecitationCount"
    const val FIELD_IS_COMPLETED = "isCompleted"

    // progress/{uid} — compact per-user progress summary (see ProgressSummary).
    const val FIELD_TOTAL_SESSIONS = "totalSessions"
    const val FIELD_CORRECT_SESSIONS = "correctSessions"
    const val FIELD_SCORE_SUM = "scoreSum"
    const val FIELD_BEST_SCORE = "bestScore"
    const val FIELD_DAILY = "daily"
    // daily.{yyyy-MM-dd} entry keys (short on purpose — the map dominates doc size).
    const val FIELD_DAY_SESSIONS = "n"
    const val FIELD_DAY_CORRECT = "c"
    const val FIELD_DAY_SCORE_SUM = "s"

    // purashcharana/{uid} — the sankalpa mirror. The count is written ABSOLUTE (never as
    // an increment), so these keys always describe the whole record; isCompleted and
    // updatedAt above are reused rather than duplicated.
    const val FIELD_AKSHARA_COUNT = "mantraAksharaCount"
    const val FIELD_TARGET_JAPA_COUNT = "targetJapaCount"
    const val FIELD_CURRENT_JAPA_COUNT = "currentJapaCount"
    const val FIELD_STARTED_AT = "startedAtMillis"
    const val FIELD_COMPLETED_AT = "completedAtMillis"
    const val FIELD_IS_ACTIVE = "isActive"

    /** Synthetic email domain for child accounts (no real mailbox). */
    const val CHILD_EMAIL_DOMAIN = "@child.anushtanam.app"

    /** Length of the numeric child ID / link code. */
    const val CODE_LENGTH = 12

    const val PIN_LENGTH = 6

    /** Builds the synthetic Firebase email for a child [code]: `c<code>@child.anushtanam.app`. */
    fun childEmail(code: String): String = "c$code$CHILD_EMAIL_DOMAIN"

    /** Builds the `links` document id for a (parent, child) pair. */
    fun linkId(parentUid: String, childUid: String): String = "${parentUid}_$childUid"

    /** True when [email] is a synthetic child email. */
    fun isChildEmail(email: String?): Boolean = email?.endsWith(CHILD_EMAIL_DOMAIN) == true

    /** Extracts the 12-digit code from a synthetic child [email], or null. */
    fun codeFromChildEmail(email: String?): String? {
        if (!isChildEmail(email)) return null
        val local = email!!.substringBefore(CHILD_EMAIL_DOMAIN)
        val code = local.removePrefix("c")
        return code.takeIf { it.length == CODE_LENGTH && it.all(Char::isDigit) }
    }
}
