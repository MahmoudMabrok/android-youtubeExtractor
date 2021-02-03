package me.echeung.youtubeextractor.internal.extractor

import me.echeung.youtubeextractor.YTFile
import org.json.JSONObject

interface Extractor {
    suspend fun getFiles(videoId: String, streamInfo: JSONObject): Map<Int, YTFile>?
}
