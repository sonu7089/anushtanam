/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * A place the solar timings are computed for. [zoneId] is an IANA timezone id;
 * when it is blank the device's current timezone is used (the case for a region
 * resolved from GPS).
 */
data class SandhyaRegion(
    val id: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String
) {
    companion object {
        /** Id used for a region resolved from the device's coarse location. */
        const val GPS_ID = "__gps__"
    }
}

/**
 * Curated, offline list of places (each with coordinates and timezone) the user
 * can pick from. India-first, with a handful of global cities for the diaspora.
 * Keeping this in-app avoids any geocoding dependency or network call.
 */
object SandhyaRegions {

    /** Shown first and used until the user chooses otherwise. */
    val DEFAULT: SandhyaRegion =
        SandhyaRegion("new_delhi", "New Delhi", 28.6139, 77.2090, "Asia/Kolkata")

    val all: List<SandhyaRegion> = listOf(
        DEFAULT,
        SandhyaRegion("mumbai", "Mumbai", 19.0760, 72.8777, "Asia/Kolkata"),
        SandhyaRegion("kolkata", "Kolkata", 22.5726, 88.3639, "Asia/Kolkata"),
        SandhyaRegion("chennai", "Chennai", 13.0827, 80.2707, "Asia/Kolkata"),
        SandhyaRegion("bengaluru", "Bengaluru", 12.9716, 77.5946, "Asia/Kolkata"),
        SandhyaRegion("hyderabad", "Hyderabad", 17.3850, 78.4867, "Asia/Kolkata"),
        SandhyaRegion("pune", "Pune", 18.5204, 73.8567, "Asia/Kolkata"),
        SandhyaRegion("ahmedabad", "Ahmedabad", 23.0225, 72.5714, "Asia/Kolkata"),
        SandhyaRegion("jaipur", "Jaipur", 26.9124, 75.7873, "Asia/Kolkata"),
        SandhyaRegion("lucknow", "Lucknow", 26.8467, 80.9462, "Asia/Kolkata"),
        SandhyaRegion("kanpur", "Kanpur", 26.4499, 80.3319, "Asia/Kolkata"),
        SandhyaRegion("varanasi", "Varanasi (Kashi)", 25.3176, 82.9739, "Asia/Kolkata"),
        SandhyaRegion("prayagraj", "Prayagraj", 25.4358, 81.8463, "Asia/Kolkata"),
        SandhyaRegion("surat", "Surat", 21.1702, 72.8311, "Asia/Kolkata"),
        SandhyaRegion("nagpur", "Nagpur", 21.1458, 79.0882, "Asia/Kolkata"),
        SandhyaRegion("patna", "Patna", 25.5941, 85.1376, "Asia/Kolkata"),
        SandhyaRegion("indore", "Indore", 22.7196, 75.8577, "Asia/Kolkata"),
        SandhyaRegion("bhopal", "Bhopal", 23.2599, 77.4126, "Asia/Kolkata"),
        SandhyaRegion("chandigarh", "Chandigarh", 30.7333, 76.7794, "Asia/Kolkata"),
        SandhyaRegion("amritsar", "Amritsar", 31.6340, 74.8723, "Asia/Kolkata"),
        SandhyaRegion("guwahati", "Guwahati", 26.1445, 91.7362, "Asia/Kolkata"),
        SandhyaRegion("bhubaneswar", "Bhubaneswar", 20.2961, 85.8245, "Asia/Kolkata"),
        SandhyaRegion("haridwar", "Haridwar", 29.9457, 78.1642, "Asia/Kolkata"),
        SandhyaRegion("ujjain", "Ujjain", 23.1765, 75.7885, "Asia/Kolkata"),
        SandhyaRegion("tirupati", "Tirupati", 13.6288, 79.4192, "Asia/Kolkata"),
        SandhyaRegion("puri", "Puri", 19.8135, 85.8312, "Asia/Kolkata"),
        SandhyaRegion("dwarka", "Dwarka", 22.2403, 68.9686, "Asia/Kolkata"),
        SandhyaRegion("rameswaram", "Rameswaram", 9.2876, 79.3129, "Asia/Kolkata"),
        SandhyaRegion("kochi", "Kochi", 9.9312, 76.2673, "Asia/Kolkata"),
        SandhyaRegion("thiruvananthapuram", "Thiruvananthapuram", 8.5241, 76.9366, "Asia/Kolkata"),
        // Diaspora / global
        SandhyaRegion("kathmandu", "Kathmandu", 27.7172, 85.3240, "Asia/Kathmandu"),
        SandhyaRegion("dubai", "Dubai", 25.2048, 55.2708, "Asia/Dubai"),
        SandhyaRegion("singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        SandhyaRegion("london", "London", 51.5074, -0.1278, "Europe/London"),
        SandhyaRegion("new_york", "New York", 40.7128, -74.0060, "America/New_York"),
        SandhyaRegion("toronto", "Toronto", 43.6532, -79.3832, "America/Toronto"),
        SandhyaRegion("los_angeles", "Los Angeles", 34.0522, -118.2437, "America/Los_Angeles"),
        SandhyaRegion("sydney", "Sydney", -33.8688, 151.2093, "Australia/Sydney")
    )

    fun byId(id: String?): SandhyaRegion? = all.firstOrNull { it.id == id }
}
