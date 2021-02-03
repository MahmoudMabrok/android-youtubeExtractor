package me.echeung.youtubeextractor.internal

import me.echeung.youtubeextractor.internal.util.HttpClient
import me.echeung.youtubeextractor.internal.util.urlDecode
import me.echeung.youtubeextractor.internal.util.urlEncode
import org.json.JSONObject

class StreamInfoFetcher(private val http: HttpClient) {

    fun getStreamInfo(videoId: String): JSONObject {
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
