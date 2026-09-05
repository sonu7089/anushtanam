/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val profileType: String, // PARENT, CHILD, INDIVIDUAL
    val parentId: Long?,
    val avatarIndex: Int
) {
    fun toDomain(): UserProfile {
        return UserProfile(
            id = id,
            name = name,
            profileType = ProfileType.valueOf(profileType),
            parentId = parentId,
            avatarIndex = avatarIndex
        )
    }

    companion object {
        fun fromDomain(domain: UserProfile): UserProfileEntity {
            return UserProfileEntity(
                id = domain.id,
                name = domain.name,
                profileType = domain.profileType.name,
                parentId = domain.parentId,
                avatarIndex = domain.avatarIndex
            )
        }
    }
}
