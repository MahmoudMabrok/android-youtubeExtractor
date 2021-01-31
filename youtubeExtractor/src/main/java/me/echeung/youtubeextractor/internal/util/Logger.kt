package me.echeung.youtubeextractor.internal.util

import android.util.Log

class Logger(private val enabled: Boolean) {
    fun d(msg: String) {
        if (enabled) {
            Log.d(TAG, msg)
        }
    }

    fun e(msg: String) {
        if (enabled) {
            Log.e(TAG, msg)
        }
    }

    companion object {
        private const val TAG = "YouTubeExtractor"
    }
}
