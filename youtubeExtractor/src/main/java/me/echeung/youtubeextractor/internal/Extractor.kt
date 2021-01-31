package me.echeung.youtubeextractor.internal

import android.content.Context
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
import me.echeung.youtubeextractor.internal.cipher.CipherClient
import me.echeung.youtubeextractor.internal.http.HttpClient
import me.echeung.youtubeextractor.internal.parser.VideoIdParser
import me.echeung.youtubeextractor.internal.parser.VideoMetadataParser
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.net.URLEncoder

class Extractor(private val contextRef: WeakReference<Context>) {

    private val http = HttpClient()
    private var videoId: String? = null

    suspend fun extract(urlOrId: String?): YouTubeExtractor.Result? {
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

    private suspend fun getNonLiveStreamVideos(streamInfo: JsonObject): Map<Int, Video>? {
        val streamingData = streamInfo["streamingData"]!!.jsonObject
        val adaptiveFormats = streamingData["adaptiveFormats"]!!.jsonArray
        val encSignatures = mutableListOf<Pair<Int, String>>()

        var videos = adaptiveFormats
            .map {
                val obj = it.jsonObject
                val itag = obj["itag"]!!.jsonPrimitive.int
                val url = obj["url"]?.jsonPrimitive?.content
                val cipher = obj["signatureCipher"]?.jsonPrimitive?.content
                Triple(itag, url, cipher)
            }
            .map { (itag, url, cipher) ->
                var extractedUrl = url
                if (cipher != null) {
                    var matcher = patCipherUrl.matcher(cipher)
                    if (matcher.find()) {
                        extractedUrl = URLDecoder.decode(matcher.group(1), "UTF-8")
                        matcher = patEncSig.matcher(cipher)
                        if (matcher.find()) {
                            var sig = URLDecoder.decode(matcher.group(1), "UTF-8")
                            sig = sig.replace("\\u0026", "&")
                            sig = sig.split("&").toTypedArray()[0]
                            encSignatures.add(Pair(itag, sig))
                        }
                    }
                }

                Pair(itag, extractedUrl)
            }
            .filter { (itag, url) -> FORMAT_MAP[itag] != null && !url!!.contains("&source=yt_otf&") }
            .map { (itag, url) -> Video(FORMAT_MAP[itag]!!, url!!) }
            .associateBy { it.format.itag }

        if (encSignatures.isNotEmpty()) {
            Log.d(TAG, "Decipher signatures: " + encSignatures.size + ", videos: " + videos.size)

            val cipherClient = CipherClient(http, contextRef)

            // Same order as encSignatures
            val signatures = cipherClient.decipherSignature(videoId!!, encSignatures)
            signatures ?: return null

            val signaturesByItag = signatures.split("\n")
                .mapIndexed { index, signature -> Pair(encSignatures[index].first, signature) }
                .associateBy { it.first }

            videos = videos.values
                .map {
                    val itag = it.format.itag
                    Video(FORMAT_MAP[itag]!!, "${it.url}&sig=${signaturesByItag[itag]}")
                }
                .associateBy { it.format.itag }
        }

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
        const val TAG = "YouTubeExtractor"

        private val patHlsItag by lazy {
            "/itag/(\\d+?)/".toPattern()
        }
        private val patCipherUrl by lazy {
            "url=(.+?)(\\\\\\\\u0026|\\z)".toPattern()
        }
        private val patEncSig by lazy {
            "s=(.{10,}?)(\\\\\\\\u0026|\\z)".toPattern()
        }
    }
}
