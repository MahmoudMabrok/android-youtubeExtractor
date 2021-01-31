package me.echeung.youtubeextractor.internal

import android.content.Context
import android.util.Log
import me.echeung.youtubeextractor.YTFile
import me.echeung.youtubeextractor.YouTubeExtractor
import me.echeung.youtubeextractor.internal.cipher.CipherUtil
import me.echeung.youtubeextractor.internal.http.HttpClient
import me.echeung.youtubeextractor.internal.parser.VideoIdParser
import me.echeung.youtubeextractor.internal.parser.VideoMetadataParser
import me.echeung.youtubeextractor.internal.util.toList
import me.echeung.youtubeextractor.internal.util.urlDecode
import me.echeung.youtubeextractor.internal.util.urlEncode
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

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
        val files = if (metadata.isLive) {
            getLiveStreamFiles(streamInfo)
        } else {
            getNonLiveStreamFiles(streamInfo)
        }

        Log.d(TAG, "Video metadata: $metadata")
        Log.d(TAG, "Video files: $files")

        return YouTubeExtractor.Result(files, metadata)
    }

    private fun getStreamInfo(): JSONObject {
        val ytInfoUrl = "https://www.youtube.com/get_video_info?video_id=$videoId&eurl=" +
            "https://youtube.googleapis.com/v/$videoId".urlEncode()

        // This is basically a URL query parameter list (i.e. foo=bar&baz=baq&...)
        var data = ""
        http.get(ytInfoUrl) { data = it }
        data = data.urlDecode().replace("\\u0026", "&")

        // Extract the relevant JSON
        val matcher = "player_response=(\\{.*\\})".toPattern().matcher(data)
        matcher.find()
        val jsonStr = matcher.group(1)
        return JSONObject(jsonStr!!)
    }

    private suspend fun getNonLiveStreamFiles(streamInfo: JSONObject): Map<Int, YTFile>? {
        val streamingData = streamInfo.getJSONObject("streamingData")
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
        val formats = streamingData.optJSONArray("formats") ?: JSONArray()

        val encryptedSignatures = mutableListOf<Pair<Int, String>>()

        var files = (adaptiveFormats.toList() + formats.toList())
            .map {
                val obj = it as JSONObject
                val itag = obj.getInt("itag")
                val url: String? = obj.optString("url")
                val cipher: String? = obj.optString("signatureCipher")
                Triple(itag, url, cipher)
            }
            .map { (itag, url, cipher) ->
                var extractedUrl = url
                if (cipher != null) {
                    var matcher = CIPHER_URL_PATTERN.matcher(cipher)
                    if (matcher.find()) {
                        extractedUrl = matcher.group(1).urlDecode()
                        matcher = ENCIPHERED_SIGNATURE_PATTERN.matcher(cipher)
                        if (matcher.find()) {
                            var sig = matcher.group(1).urlDecode()
                            sig = sig.replace("\\u0026", "&")
                            sig = sig.split("&").toTypedArray()[0]
                            encryptedSignatures.add(itag to sig)
                        }
                    }
                }

                itag to extractedUrl
            }
            .filter { (itag, url) -> FORMAT_MAP[itag] != null && url?.contains("&source=yt_otf&") == false }
            .map { (itag, url) -> YTFile(FORMAT_MAP[itag]!!, url!!) }
            .associateBy { it.format.itag }

        if (encryptedSignatures.isNotEmpty()) {
            Log.d(TAG, "Decipher signatures: " + encryptedSignatures.size + ", files: " + files.size)

            val cipherClient = CipherUtil(http, contextRef)
            val signatures = cipherClient.decipherSignatures(videoId!!, encryptedSignatures)
            signatures ?: return null

            files = files.values
                .map {
                    val itag = it.format.itag
                    YTFile(FORMAT_MAP[itag]!!, "${it.url}&sig=${signatures[itag]}")
                }
                .associateBy { it.format.itag }
        }

        if (files.isEmpty()) {
            return null
        }

        return files
    }

    private fun getLiveStreamFiles(streamInfo: JSONObject): Map<Int, YTFile>? {
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
        const val TAG = "YouTubeExtractor"

        private val HLS_ITAG_PATTERN by lazy {
            "/itag/(\\d+?)/".toPattern()
        }
        private val CIPHER_URL_PATTERN by lazy {
            "url=(.+?)(\\\\\\\\u0026|\\z)".toPattern()
        }
        private val ENCIPHERED_SIGNATURE_PATTERN by lazy {
            "s=(.{10,}?)(\\\\\\\\u0026|\\z)".toPattern()
        }
    }
}
