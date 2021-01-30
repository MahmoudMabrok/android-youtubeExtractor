package me.echeung.youtubeextractor

import android.content.Context
import android.util.SparseArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.echeung.youtubeextractor.internal.Extractor
import java.lang.ref.WeakReference

class YouTubeExtractor(context: Context) {

    private val extractor: Extractor

    init {
        val refContext = WeakReference(context)
        val cacheDirPath = context.cacheDir.absolutePath
        extractor = Extractor(refContext, cacheDirPath)
    }

    /**
     * Extract the video links and metadata.
     *
     * @param urlOrId The YouTube page URL or video ID.
     */
    suspend fun extract(urlOrId: String?): Result? {
        return withContext(Dispatchers.IO) {
            extractor.getYtFiles(urlOrId)
        }
    }

    class Result(
        val files: SparseArray<YtFile?>?,
        val videoMetadata: VideoMetadata?
    )
}
