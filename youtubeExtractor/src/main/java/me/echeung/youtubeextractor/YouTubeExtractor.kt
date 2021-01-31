package me.echeung.youtubeextractor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.echeung.youtubeextractor.internal.Extractor
import java.lang.ref.WeakReference

class YouTubeExtractor(context: Context) {

    private val extractor: Extractor = Extractor(WeakReference(context))

    /**
     * Extract the video links and metadata.
     *
     * @param urlOrId The YouTube page URL or video ID.
     */
    suspend fun extract(urlOrId: String?): Result? {
        return withContext(Dispatchers.IO) {
            extractor.extract(urlOrId)
        }
    }

    class Result(
        val videos: Map<Int, Video>?,
        val metadata: Metadata
    )
}
