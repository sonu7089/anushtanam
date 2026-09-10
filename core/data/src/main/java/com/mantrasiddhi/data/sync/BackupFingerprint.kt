package com.mantrasiddhi.data.sync

import org.json.JSONObject

/** Firebase maps can return fields in a different order from local records. */
internal object BackupFingerprint {
    fun encode(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }.joinToString(",", "{", "}") {
            JSONObject.quote(it.key.toString()) + ":" + encode(it.value)
        }
        is List<*> -> value.joinToString(",", "[", "]") { encode(it) }
        is Number -> JSONObject.numberToString(value)
        is Boolean -> value.toString()
        is String -> JSONObject.quote(value)
        else -> error("Unsupported backup value")
    }
}
