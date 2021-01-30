package me.echeung.youtubeextractor.internal

import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.echeung.youtubeextractor.Video
import me.echeung.youtubeextractor.YouTubeExtractor
import me.echeung.youtubeextractor.internal.http.HttpClient
import me.echeung.youtubeextractor.internal.parser.VideoIdParser
import me.echeung.youtubeextractor.internal.parser.VideoMetadataParser
import java.net.URLDecoder
import java.net.URLEncoder

class Extractor {

    private val http = HttpClient()
    private var videoId: String? = null

    fun extract(urlOrId: String?): YouTubeExtractor.Result? {
        videoId = VideoIdParser().getVideoId(urlOrId)
        if (videoId == null) {
            Log.e(TAG, "Invalid YouTube link format: $urlOrId")
            return null
        }

        val streamInfo = getStreamInfo()
        val metadata = VideoMetadataParser().parseVideoMetadata(videoId!!, streamInfo)
        val videos = if (metadata.isLive) {
            getLiveStreamVideos(streamInfo)
        } else {
            getNonLiveStreamVideos(streamInfo)
        }

        return YouTubeExtractor.Result(videos, metadata)
    }

    private fun getStreamInfo(): JsonObject {
        val ytInfoUrl = "https://www.youtube.com/get_video_info?video_id=$videoId&eurl=" +
            URLEncoder.encode("https://youtube.googleapis.com/v/$videoId", "UTF-8")

        // This is basically a URL query parameter list (i.e. foo=bar&baz=baq&...)
        var data = ""
        http.get(ytInfoUrl) { data = it }
        data = URLDecoder.decode(data, "UTF-8")
        data = data.replace("\\u0026", "&")

        // Extract the relevant JSON
        val matcher = "player_response=(\\{.*\\})".toPattern().matcher(data)
        matcher.find()
        val jsonStr = matcher.group(1)
        return Json.decodeFromString(jsonStr)
    }

    private fun getNonLiveStreamVideos(streamInfo: JsonObject): Map<Int, Video>? {
        val streamingData = streamInfo["streamingData"]!!.jsonObject
        val adaptiveFormats = streamingData["adaptiveFormats"]!!.jsonArray

        val videos = adaptiveFormats
            .map {
                val obj = it.jsonObject
                val itag = obj["itag"]!!.jsonPrimitive.int
                val url = obj["url"]!!.jsonPrimitive.content
                Pair(itag, url)
            }
            .filter { (itag, url) -> FORMAT_MAP[itag] != null && !url.contains("&source=yt_otf&") }
            .map { (itag, url) -> Video(FORMAT_MAP[itag]!!, url) }
            .associateBy { it.format.itag }

        if (videos.isEmpty()) {
            return null
        }

        return videos
    }

    private fun getLiveStreamVideos(streamInfo: JsonObject): Map<Int, Video>? {
        val streamingData = streamInfo["streamingData"]!!.jsonObject
        val hlsManifestUrl = streamingData["hlsManifestUrl"]!!.jsonPrimitive.content

        val videos = mutableMapOf<Int, Video>()

        http.get(hlsManifestUrl) {
            if (it.startsWith("http")) {
                val matcher = patHlsItag.matcher(it)
                if (matcher.find()) {
                    val itag: Int = matcher.group(1).toInt()
                    val newFile = Video(FORMAT_MAP[itag]!!, it)
                    videos.put(itag, newFile)
                }
            }
        }

        if (videos.isEmpty()) {
            return null
        }

        return videos
    }

    companion object {
        private const val TAG = "YouTubeExtractor"

        private val patHlsItag by lazy { "/itag/(\\d+?)/".toPattern() }
    }
}
