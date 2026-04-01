package com.aikeyboard.app.cache

import com.tencent.mmkv.MMKV
import java.security.MessageDigest

class ResultCache {

    private val mmkv = MMKV.mmkvWithID("ai_results", MMKV.MULTI_PROCESS_MODE)

    fun get(task: String, input: String): String? {
        val key = sha256("$task:$input")
        return mmkv.decodeString(key)
    }

    fun put(task: String, input: String, result: String) {
        val key = sha256("$task:$input")
        mmkv.encode(key, result)
    }

    fun clear() {
        mmkv.clearAll()
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}