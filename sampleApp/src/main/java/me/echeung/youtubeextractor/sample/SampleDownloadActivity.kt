package me.echeung.youtubeextractor.sample

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.echeung.youtubeextractor.YTFile
import me.echeung.youtubeextractor.YouTubeExtractor
import me.echeung.youtubeextractor.sample.databinding.ActivitySampleDownloadBinding

class SampleDownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySampleDownloadBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySampleDownloadBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        // Check how it was started and if we can get the youtube link
        if (savedInstanceState == null && Intent.ACTION_SEND == intent.action && intent.type != null && "text/plain" == intent.type) {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT)
            try {
                getYoutubeDownloadUrl(url!!)
            } catch (e: Throwable) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            // val url = "https://www.youtube.com/watch?v=W8hTq_l7-AQ"
            // getYoutubeDownloadUrl(url)
            finish()
        }
    }

    private fun getYoutubeDownloadUrl(youtubeLink: String) {
        val extractor = YouTubeExtractor(this, withLogging = true)

        GlobalScope.launch(Dispatchers.IO) {
            val result = extractor.extract(youtubeLink)
            withContext(Dispatchers.Main) { binding.loading.isGone = true }
            if (result.files == null) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }

            result.files!!.values.forEach {
                withContext(Dispatchers.Main) { addButton(result.metadata.videoId, it) }
            }
        }
    }

    private fun addButton(videoId: String, file: YTFile) {
        var btnText = if (file.format.height == -1)
            "Audio ${file.format.audioBitrate} kbit/s"
        else
            "${file.format.height}p"
        btnText += when {
            file.format.isDashContainer -> " (DASH)"
            file.format.isHlsContent -> " (HLS)"
            else -> ""
        }

        val btn = Button(this).apply {
            text = btnText
            setOnClickListener {
                downloadFromUrl(file.url, videoId, "${videoId.take(55)}.${file.format.ext}")
            }
        }

        binding.mainLayout.addView(btn)
    }

    private fun downloadFromUrl(downloadUrl: String, downloadTitle: String, fileName: String) {
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(downloadTitle)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
