package me.echeung.youtubeextractor.sample

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.echeung.youtubeextractor.Video
import me.echeung.youtubeextractor.YouTubeExtractor
import me.echeung.youtubeextractor.sample.databinding.ActivitySampleDownloadBinding

class SampleDownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySampleDownloadBinding

    private var youtubeLink: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySampleDownloadBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        // Check how it was started and if we can get the youtube link
        if (savedInstanceState == null && Intent.ACTION_SEND == intent.action && intent.type != null && "text/plain" == intent.type) {
            val ytLink = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (ytLink != null
                && (ytLink.contains("://youtu.be/") || ytLink.contains("youtube.com/watch?v="))
            ) {
                youtubeLink = ytLink
                // We have a valid link
                getYoutubeDownloadUrl(youtubeLink)
            } else {
                Toast.makeText(this, R.string.error_no_yt_link, Toast.LENGTH_LONG).show()
                finish()
            }
        } else if (savedInstanceState != null && youtubeLink != null) {
            getYoutubeDownloadUrl(youtubeLink)
        } else {
            finish()
        }
    }

    private fun getYoutubeDownloadUrl(youtubeLink: String?) {
        GlobalScope.launch(Dispatchers.IO) {
            val result = YouTubeExtractor().extract(youtubeLink)
            withContext(Dispatchers.Main) { binding.loading.isGone = true }
            if (result?.videos == null) {
                // Something went wrong we got no urls. Always check this.
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }

            result.videos!!.values.forEach {
                withContext(Dispatchers.Main) { addButtonToMainLayout(result.metadata!!.title, it) }
            }
        }
    }

    private fun addButtonToMainLayout(videoTitle: String, ytfile: Video) {
        // Display some buttons and let the user choose the format
        var btnText = if (ytfile.format.height == -1) "Audio " +
            ytfile.format.audioBitrate + " kbit/s" else ytfile.format.height.toString() + "p"
        btnText += if (ytfile.format.isDashContainer) " dash" else ""
        val btn = Button(this)
        btn.text = btnText
        btn.setOnClickListener { v: View? ->
            var filename: String = if (videoTitle.length > 55) {
                videoTitle.substring(0, 55) + "." + ytfile.format.ext
            } else {
                videoTitle + "." + ytfile.format.ext
            }
            filename = filename.replace("[\\\\><\"|*?%:#/]".toRegex(), "")
            downloadFromUrl(ytfile.url, videoTitle, filename)
            finish()
        }
        binding.mainLayout.addView(btn)
    }

    private fun downloadFromUrl(youtubeDlUrl: String, downloadTitle: String, fileName: String) {
        val uri = Uri.parse(youtubeDlUrl)
        val request = DownloadManager.Request(uri)
        request.setTitle(downloadTitle)
        request.allowScanningByMediaScanner()
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
