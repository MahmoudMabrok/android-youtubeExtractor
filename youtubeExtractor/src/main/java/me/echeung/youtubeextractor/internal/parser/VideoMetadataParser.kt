package me.echeung.youtubeextractor.internal.parser

import me.echeung.youtubeextractor.YTMetadata
import org.json.JSONObject

class VideoMetadataParser {
    fun parseVideoMetadata(videoId: String, streamInfo: JSONObject): YTMetadata {
        val videoDetails = streamInfo.getJSONObject("videoDetails")

        return YTMetadata(
            videoId,
            title = videoDetails.getString("title"),
            author = videoDetails.getString("author"),
            channelId = videoDetails.getString("channelId"),
            duration = videoDetails.getString("lengthSeconds").toLong(),
            viewCount = videoDetails.getString("viewCount").toLong(),
            isLive = videoDetails.getString("lengthSeconds").toLong() == 0L,
            isLiveContent = videoDetails.getBoolean("isLiveContent"),
            shortDescription = videoDetails.getString("shortDescription")
        )
    }
}
