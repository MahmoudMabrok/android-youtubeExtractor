package me.echeung.youtubeextractor.internal.http

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HttpClient {

    fun get(url: String, lineReader: (String) -> Unit) {
        Log.d("YT:HttpClient", "Getting info from: $url")
        val getUrl = URL(url)
        val urlConnection = getUrl.openConnection() as HttpURLConnection
        urlConnection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            BufferedReader(InputStreamReader(urlConnection.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lineReader(line!!)
                }
            }
        } finally {
            urlConnection.disconnect()
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36"
    }
}
