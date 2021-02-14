package me.echeung.youtubeextractor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.echeung.youtubeextractor.exception.InvalidLinkException
import me.echeung.youtubeextractor.internal.CipherUtil
import me.echeung.youtubeextractor.internal.StreamInfoFetcher
import me.echeung.youtubeextractor.internal.VideoIdParser
import me.echeung.youtubeextractor.internal.VideoMetadataParser
import me.echeung.youtubeextractor.internal.extractor.LiveStreamExtractor
import me.echeung.youtubeextractor.internal.extractor.NonLiveStreamExtractor
import me.echeung.youtubeextractor.internal.util.HttpClient
import me.echeung.youtubeextractor.internal.util.Logger
import java.lang.ref.WeakReference

class YouTubeExtractor(private val context: Context, withLogging: Boolean = false) {

    private val log = Logger(withLogging)
    private val http = HttpClient(log)

    /**
     * Gets the video ID from a YouTube URL.
     *
     * @param urlOrId The YouTube page URL or video ID.
     * @throws InvalidLinkException if an invalid video is passed.
     */
    @Throws(InvalidLinkException::class)
    fun getVideoId(urlOrId: String): String {
        return VideoIdParser.getVideoId(urlOrId)
            ?: throw InvalidLinkException("Invalid YouTube link format: $urlOrId")
    }

    /**
     * Gets the stream info (title, description, etc.).
     *
     * @param urlOrId The YouTube page URL or video ID.
     * @throws InvalidLinkException if an invalid video is passed.
     */
    @Throws(InvalidLinkException::class)
    suspend fun getStreamInfo(urlOrId: String): YTMetadata = withContext(Dispatchers.IO) {
        val videoId = getVideoId(urlOrId)
        val streamInfo = StreamInfoFetcher(http).getStreamInfo(videoId)
        VideoMetadataParser.parseVideoMetadata(videoId, streamInfo)
    }

    /**
     * Extract the video links and metadata.
     *
     * @param urlOrId The YouTube page URL or video ID.
     * @throws InvalidLinkException if an invalid video is passed.
     */
    @Throws(InvalidLinkException::class)
    suspend fun extract(urlOrId: String): Result = withContext(Dispatchers.IO) {
        val videoId = getVideoId(urlOrId)
        val streamInfo = StreamInfoFetcher(http).getStreamInfo(videoId)
        val metadata = VideoMetadataParser.parseVideoMetadata(videoId, streamInfo)

        val files = if (metadata.isLive) {
            LiveStreamExtractor(http).getFiles(videoId, streamInfo)
        } else {
            val cipherUtil = CipherUtil(WeakReference(context), http, log)
            NonLiveStreamExtractor(cipherUtil, log).getFiles(videoId, streamInfo)
        }

        log.d("Video metadata: $metadata")
        log.d("Video files: $files")

        Result(files.takeIf { it.isNotEmpty() }, metadata)
    }

    class Result(
        val files: Map<Int, YTFile>?,
        val metadata: YTMetadata
    )
}
