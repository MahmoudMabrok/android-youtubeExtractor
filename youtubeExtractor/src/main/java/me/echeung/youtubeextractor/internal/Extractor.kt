package me.echeung.youtubeextractor.internal

import android.content.Context
import me.echeung.youtubeextractor.YouTubeExtractor
import me.echeung.youtubeextractor.internal.cipher.CipherUtil
import me.echeung.youtubeextractor.internal.extractor.LiveStreamExtractor
import me.echeung.youtubeextractor.internal.extractor.NonLiveStreamExtractor
import me.echeung.youtubeextractor.internal.http.HttpClient
import me.echeung.youtubeextractor.internal.parser.VideoIdParser
import me.echeung.youtubeextractor.internal.parser.VideoMetadataParser
import me.echeung.youtubeextractor.internal.util.Logger
import me.echeung.youtubeextractor.internal.util.urlDecode
import me.echeung.youtubeextractor.internal.util.urlEncode
import org.json.JSONObject
import java.lang.ref.WeakReference

class Extractor(contextRef: WeakReference<Context>, withLogging: Boolean) {

    private val log = Logger(withLogging)
    private val http = HttpClient(log)
    private val cipherUtil = CipherUtil(contextRef, http, log)
    private var videoId: String? = null

    suspend fun extract(urlOrId: String?): YouTubeExtractor.Result? {
        videoId = VideoIdParser.getVideoId(urlOrId)
        if (videoId == null) {
            log.e("Invalid YouTube link format: $urlOrId")
            return null
        }

        val streamInfo = getStreamInfo()
        val metadata = VideoMetadataParser.parseVideoMetadata(videoId!!, streamInfo)
        val files = if (metadata.isLive) {
            LiveStreamExtractor(http).getFiles(videoId!!, streamInfo)
        } else {
            NonLiveStreamExtractor(http, cipherUtil, log).getFiles(videoId!!, streamInfo)
        }

        log.d("Video metadata: $metadata")
        log.d("Video files: $files")

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
}
