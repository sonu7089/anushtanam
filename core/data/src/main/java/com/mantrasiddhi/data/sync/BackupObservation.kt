package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import javax.inject.Inject

/** Keeps Room itself out of the presentation module. */
class BackupObservation @Inject constructor(private val database: MantraSiddhiDatabase) {
    fun state(uid: String) = database.backupDao().observeState(uid)
    fun pending(uid: String) = database.cloudSyncDao().observeCount(uid)
    fun conflicts(uid: String) = database.backupDao().observeConflicts(uid)
    fun entries(userId: Long) = database.japaDao().observeEntries(userId)
    fun lastAttempt(uid: String) = database.cloudSyncDao().observeLastAttemptAt(uid)
}
