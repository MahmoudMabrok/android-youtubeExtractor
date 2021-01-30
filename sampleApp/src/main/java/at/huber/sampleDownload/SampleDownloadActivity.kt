package at.huber.sampleDownload

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.SparseArray
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import at.huber.youtubeExtractor.VideoMeta
import at.huber.youtubeExtractor.YouTubeExtractor
import at.huber.youtubeExtractor.YtFile

class SampleDownloadActivity : AppCompatActivity() {

    private var mainLayout: LinearLayout? = null
    private var mainProgressBar: ProgressBar? = null

    private var youtubeLink: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample_download)
        mainLayout = findViewById(R.id.main_layout)
        mainProgressBar = findViewById(R.id.prgrBar)

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

    @SuppressLint("StaticFieldLeak")
    private fun getYoutubeDownloadUrl(youtubeLink: String?) {
        object : YouTubeExtractor(this) {
            public override fun onExtractionComplete(
                ytFiles: SparseArray<YtFile>,
                vMeta: VideoMeta
            ) {
                mainProgressBar!!.visibility = View.GONE
                if (ytFiles == null) {
                    // Something went wrong we got no urls. Always check this.
                    finish()
                    return
                }
                // Iterate over itags
                var i = 0
                var itag: Int
                while (i < ytFiles.size()) {
                    itag = ytFiles.keyAt(i)
                    // ytFile represents one file with its url and meta data
                    val ytFile = ytFiles[itag]

                    // Just add videos in a decent format => height -1 = audio
                    if (ytFile.format.height == -1 || ytFile.format.height >= 360) {
                        addButtonToMainLayout(vMeta.title, ytFile)
                    }
                    i++
                }
            }
        }.extract(youtubeLink, true, false)
    }

    private fun addButtonToMainLayout(videoTitle: String, ytfile: YtFile) {
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
        mainLayout!!.addView(btn)
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
