package me.echeung.youtubeextractor.internal.parser

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.echeung.youtubeextractor.YTMetadata

class VideoMetadataParser {
    fun parseVideoMetadata(videoId: String, streamInfo: JsonObject): YTMetadata {
        val videoDetails = streamInfo["videoDetails"]!!.jsonObject

        return YTMetadata(
            videoId,
            title = videoDetails["title"]!!.jsonPrimitive.content,
            author = videoDetails["author"]!!.jsonPrimitive.content,
            channelId = videoDetails["channelId"]!!.jsonPrimitive.content,
            duration = videoDetails["lengthSeconds"]!!.jsonPrimitive.long,
            viewCount = videoDetails["viewCount"]!!.jsonPrimitive.long,
            isLive = videoDetails["lengthSeconds"]!!.jsonPrimitive.long == 0L,
            isLiveContent = videoDetails["isLiveContent"]!!.jsonPrimitive.content == "true",
            shortDescription = videoDetails["shortDescription"]!!.jsonPrimitive.content
        )
    }
}
