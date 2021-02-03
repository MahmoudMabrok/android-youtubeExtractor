package me.echeung.youtubeextractor.internal.extractor

import me.echeung.youtubeextractor.YTFile
import me.echeung.youtubeextractor.internal.FORMAT_MAP
import me.echeung.youtubeextractor.internal.http.HttpClient
import org.json.JSONObject

class LiveStreamExtractor(private val http: HttpClient) : Extractor {

    override suspend fun getFiles(videoId: String, streamInfo: JSONObject): Map<Int, YTFile>? {
        val streamingData = streamInfo.getJSONObject("streamingData")
        val hlsManifestUrl = streamingData.getString("hlsManifestUrl")

        val files = mutableMapOf<Int, YTFile>()

        http.get(hlsManifestUrl) {
            if (it.startsWith("http")) {
                val matcher = HLS_ITAG_PATTERN.matcher(it)
                if (matcher.find()) {
                    val itag: Int = matcher.group(1).toInt()
                    files[itag] = YTFile(FORMAT_MAP[itag]!!, it)
                }
            }
        }

        if (files.isEmpty()) {
            return null
        }

        return files
    }

    companion object {
        private val HLS_ITAG_PATTERN by lazy {
            "/itag/(\\d+?)/".toPattern()
        }
    }
}
