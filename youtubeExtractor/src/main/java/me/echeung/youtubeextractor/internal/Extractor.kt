package me.echeung.youtubeextractor.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseArray
import com.evgenii.jsevaluator.JsEvaluator
import com.evgenii.jsevaluator.interfaces.JsCallback
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.echeung.youtubeextractor.Video
import me.echeung.youtubeextractor.YouTubeExtractor
import me.echeung.youtubeextractor.internal.http.HttpClient
import me.echeung.youtubeextractor.internal.parser.VideoIdParser
import me.echeung.youtubeextractor.internal.parser.VideoMetadataParser
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class Extractor(private val refContext: WeakReference<Context>, private val cacheDirPath: String) {

    private val http = HttpClient()
    private var videoId: String? = null

    @Volatile
    private var decipheredSignature: String? = null
    private val lock: Lock = ReentrantLock()
    private val jsExecuting = lock.newCondition()

    fun extract(urlOrId: String?): YouTubeExtractor.Result? {
        videoId = VideoIdParser().getVideoId(urlOrId)
        if (videoId == null) {
            Log.e(LOG_TAG, "Invalid YouTube link format: $urlOrId")
            return null
        }

        val streamInfo = getStreamInfo()
        val metadata = VideoMetadataParser().parseVideoMetadata(videoId!!, streamInfo)
        val videos = if (metadata.isLive) {
            getLiveStreamVideos(streamInfo)
        } else {
            getNonLiveStreamVideos(streamInfo)
        }

        return YouTubeExtractor.Result(videos, metadata)
    }

    private fun getStreamInfo(): JsonObject {
        val ytInfoUrl = "https://www.youtube.com/get_video_info?video_id=$videoId&eurl=" +
            URLEncoder.encode("https://youtube.googleapis.com/v/$videoId", "UTF-8")

        // This is basically a URL query parameter list (i.e. foo=bar&baz=baq&...)
        var data = ""
        http.get(ytInfoUrl) { data = it }
        data = URLDecoder.decode(data, "UTF-8")
        data = data.replace("\\u0026", "&")

        // Extract the relevant JSON
        val matcher = "player_response=(\\{.*\\})".toPattern().matcher(data)
        matcher.find()
        val jsonStr = matcher.group(1)
        return Json.decodeFromString(jsonStr)
    }

    private fun getNonLiveStreamVideos(streamInfo: JsonObject): Map<Int, Video>? {
        // var mat: Matcher
        // val curJsFileName: String
        // var encSignatures: SparseArray<String?>? = null
        //
        // // "use_cipher_signature" disappeared, we check whether at least one ciphered signature
        // // exists int the stream_map.
        // var sigEnc = true
        // var statusFail = false
        // if (!patCipher.matcher(streamMap).find()) {
        //     sigEnc = false
        //     if (!patStatusOk.matcher(streamMap).find()) {
        //         statusFail = true
        //     }
        // }
        //
        // // Some videos are using a ciphered signature we need to get the
        // // deciphering js-file from the youtubepage.
        // if (sigEnc || statusFail) {
        //     // Get the video directly from the youtubepage
        //     if (decipherJsFileName == null || decipherFunctions == null || decipherFunctionName == null) {
        //         readDecipherFunctFromCache()
        //     }
        //     val sbStreamMap = StringBuilder()
        //     http.get("https://youtube.com/watch?v=$videoId") { line: String ->
        //         sbStreamMap.append(line.replace("\\\"", "\""))
        //     }
        //     streamMap = sbStreamMap.toString()
        //     encSignatures = SparseArray()
        //     mat = patDecryptionJsFile.matcher(streamMap)
        //     if (!mat.find()) mat = patDecryptionJsFileWithoutSlash.matcher(streamMap)
        //     if (mat.find()) {
        //         curJsFileName = mat.group(0).replace("\\/", "/")
        //         if (decipherJsFileName == null || decipherJsFileName != curJsFileName) {
        //             decipherFunctions = null
        //             decipherFunctionName = null
        //         }
        //         decipherJsFileName = curJsFileName
        //     }
        // }
        // val ytFiles = SparseArray<Video>()
        // mat = if (sigEnc) {
        //     patCipher.matcher(streamMap)
        // } else {
        //     patUrl.matcher(streamMap)
        // }
        // while (mat.find()) {
        //     var sig: String? = null
        //     var url: String
        //     if (sigEnc) {
        //         val cipher = mat.group(1)
        //         var mat2 = patCipherUrl.matcher(cipher)
        //         if (mat2.find()) {
        //             url = URLDecoder.decode(mat2.group(1), "UTF-8")
        //             mat2 = patEncSig.matcher(cipher)
        //             if (mat2.find()) {
        //                 sig = URLDecoder.decode(mat2.group(1), "UTF-8")
        //                 // fix issue #165
        //                 sig = sig.replace("\\u0026", "&")
        //                 sig = sig.split("&").toTypedArray()[0]
        //             } else {
        //                 continue
        //             }
        //         } else {
        //             continue
        //         }
        //     } else {
        //         url = mat.group(1)
        //     }
        //     val mat2 = patItag.matcher(url)
        //     if (!mat2.find()) continue
        //     val itag = mat2.group(1).toInt()
        //     if (FORMAT_MAP[itag] == null) {
        //         Log.d(LOG_TAG, "Itag not in list:$itag")
        //         continue
        //     }
        //
        //     // Unsupported
        //     if (url.contains("&source=yt_otf&")) continue
        //     Log.d(LOG_TAG, "Itag found:$itag")
        //     if (sig != null) {
        //         encSignatures!!.append(itag, sig)
        //     }
        //     val newVideo = Video(FORMAT_MAP[itag]!!, url)
        //     ytFiles.put(itag, newVideo)
        // }
        // if (encSignatures != null) {
        //     Log.d(
        //         LOG_TAG,
        //         "Decipher signatures: " + encSignatures.size() + ", videos: " + ytFiles.size()
        //     )
        //     decipheredSignature = null
        //     if (decipherSignature(encSignatures)) {
        //         lock.lock()
        //         try {
        //             jsExecuting.await(7, TimeUnit.SECONDS)
        //         } finally {
        //             lock.unlock()
        //         }
        //     }
        //     val signature: String? = decipheredSignature
        //     if (signature == null) {
        //         return null
        //     } else {
        //         val sigs = signature.split("\n").toTypedArray()
        //         var i = 0
        //         while (i < encSignatures.size() && i < sigs.size) {
        //             val key = encSignatures.keyAt(i)
        //             var url = ytFiles[key].url
        //             url += "&sig=" + sigs[i]
        //             ytFiles.put(key, Video(FORMAT_MAP[key]!!, url))
        //             i++
        //         }
        //     }
        // }
        // if (ytFiles.size() == 0) {
        //     return null
        // }
        // return ytFiles
        return null
    }

    private fun getLiveStreamVideos(streamInfo: JsonObject): Map<Int, Video>? {
        val streamingData = streamInfo["streamingData"]!!.jsonObject
        val hlsManifestUrl = streamingData["hlsManifestUrl"]!!.jsonPrimitive.content

        val videos = mutableMapOf<Int, Video>()

        http.get(hlsManifestUrl) {
            if (it.startsWith("http")) {
                val matcher = patHlsItag.matcher(it)
                if (matcher.find()) {
                    val itag: Int = matcher.group(1).toInt()
                    val newFile = Video(FORMAT_MAP[itag]!!, it)
                    videos.put(itag, newFile)
                }
            }
        }

        if (videos.isEmpty()) {
            return null
        }

        return videos
    }

    private fun decipherSignature(encSignatures: SparseArray<String?>): Boolean {
        // Assume the functions don't change that much
        if (decipherFunctionName == null || decipherFunctions == null) {
            val decipherFunctUrl = "https://youtube.com$decipherJsFileName"
            val javascriptFile: String
            val sb = StringBuilder("")
            http.get(decipherFunctUrl) {
                sb.append(it)
                sb.append(" ")
            }
            javascriptFile = sb.toString()
            Log.d(LOG_TAG, "Decipher FunctURL: $decipherFunctUrl")
            var mat = patSignatureDecFunction.matcher(javascriptFile)
            if (mat.find()) {
                decipherFunctionName = mat.group(1)
                Log.d(LOG_TAG, "Decipher Functname: $decipherFunctionName")
                val patMainVariable = ("(var |\\s|,|;)" + decipherFunctionName!!.replace("$", "\\$") +
                        "(=function\\((.{1,3})\\)\\{)").toPattern()
                var mainDecipherFunct: String
                mat = patMainVariable.matcher(javascriptFile)
                if (mat.find()) {
                    mainDecipherFunct = "var " + decipherFunctionName + mat.group(2)
                } else {
                    val patMainFunction = ("function " + decipherFunctionName!!.replace("$", "\\$") +
                            "(\\((.{1,3})\\)\\{)").toPattern()
                    mat = patMainFunction.matcher(javascriptFile)
                    if (!mat.find()) return false
                    mainDecipherFunct = "function " + decipherFunctionName + mat.group(2)
                }
                var startIndex = mat.end()
                var braces = 1
                var i = startIndex
                while (i < javascriptFile.length) {
                    if (braces == 0 && startIndex + 5 < i) {
                        mainDecipherFunct += javascriptFile.substring(startIndex, i) + ";"
                        break
                    }
                    if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
                    i++
                }
                decipherFunctions = mainDecipherFunct
                // Search the main function for extra functions and variables
                // needed for deciphering
                // Search for variables
                mat = patVariableFunction.matcher(mainDecipherFunct)
                while (mat.find()) {
                    val variableDef = "var " + mat.group(2) + "={"
                    if (decipherFunctions!!.contains(variableDef)) {
                        continue
                    }
                    startIndex = javascriptFile.indexOf(variableDef) + variableDef.length
                    var braces = 1
                    var i = startIndex
                    while (i < javascriptFile.length) {
                        if (braces == 0) {
                            decipherFunctions += variableDef + javascriptFile.substring(
                                startIndex,
                                i
                            ) + ";"
                            break
                        }
                        if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
                        i++
                    }
                }
                // Search for functions
                mat = patFunction.matcher(mainDecipherFunct)
                while (mat.find()) {
                    val functionDef = "function " + mat.group(2) + "("
                    if (decipherFunctions!!.contains(functionDef)) {
                        continue
                    }
                    startIndex = javascriptFile.indexOf(functionDef) + functionDef.length
                    var braces = 0
                    var i = startIndex
                    while (i < javascriptFile.length) {
                        if (braces == 0 && startIndex + 5 < i) {
                            decipherFunctions += functionDef + javascriptFile.substring(
                                startIndex,
                                i
                            ) + ";"
                            break
                        }
                        if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
                        i++
                    }
                }
                Log.d(LOG_TAG, "Decipher Function: $decipherFunctions")
                decipherViaWebView(encSignatures)
                writeDeciperFunctToCache()
            } else {
                return false
            }
        } else {
            decipherViaWebView(encSignatures)
        }
        return true
    }

    private fun readDecipherFunctFromCache() {
        val cacheFile = File("$cacheDirPath/$CACHE_FILE_NAME")
        // The cached functions are valid for 2 weeks
        if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 1209600000) {
            try {
                BufferedReader(
                    InputStreamReader(
                        FileInputStream(cacheFile),
                        StandardCharsets.UTF_8
                    )
                ).use { reader ->
                    decipherJsFileName = reader.readLine()
                    decipherFunctionName = reader.readLine()
                    decipherFunctions = reader.readLine()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun writeDeciperFunctToCache() {
        val cacheFile = File("$cacheDirPath/$CACHE_FILE_NAME")
        try {
            BufferedWriter(
                OutputStreamWriter(
                    FileOutputStream(cacheFile),
                    StandardCharsets.UTF_8
                )
            ).use { writer ->
                writer.write(decipherJsFileName)
                writer.write(decipherFunctionName)
                writer.write(decipherFunctions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun decipherViaWebView(encSignatures: SparseArray<String?>) {
        val context = refContext.get() ?: return
        val stb = StringBuilder("$decipherFunctions function decipher(")
        stb.append("){return ")
        for (i in 0 until encSignatures.size()) {
            val key = encSignatures.keyAt(i)
            if (i < encSignatures.size() - 1) stb.append(decipherFunctionName).append("('").append(
                encSignatures[key]
            ).append("')+\"\\n\"+") else stb.append(decipherFunctionName).append("('").append(
                encSignatures[key]
            ).append("')")
        }
        stb.append("};decipher();")
        Handler(Looper.getMainLooper()).post {
            JsEvaluator(context).evaluate(stb.toString(), object : JsCallback {
                override fun onResult(result: String) {
                    lock.lock()
                    try {
                        decipheredSignature = result
                        jsExecuting.signal()
                    } finally {
                        lock.unlock()
                    }
                }

                override fun onError(errorMessage: String) {
                    lock.lock()
                    try {
                        Log.e(LOG_TAG, errorMessage)
                        jsExecuting.signal()
                    } finally {
                        lock.unlock()
                    }
                }
            })
        }
    }

    companion object {
        private const val LOG_TAG = "YouTubeExtractor"
        private const val CACHE_FILE_NAME = "decipher_js_funct"

        private var decipherJsFileName: String? = null
        private var decipherFunctions: String? = null
        private var decipherFunctionName: String? = null

        private val patHlsManifestUrl = "(.*?)^https(.*?)(?=\")".toPattern()
        private val patHlsvp = "hlsManifestUrl%22%3A%22(.+?)(&|\\z)".toPattern()

        private val patStatusOk = "status=ok(&|,|\\z)".toPattern()
        private val patHlsItag = "/itag/(\\d+?)/".toPattern()
        private val patItag = "itag=([0-9]+?)(&|\\z)".toPattern()
        private val patEncSig = "s=(.{10,}?)(\\\\\\\\u0026|\\z)".toPattern()
        private val patUrl = "\"url\"\\s*:\\s*\"(.+?)\"".toPattern()

        private val patCipher = "\"signatureCipher\"\\s*:\\s*\"(.+?)\"".toPattern()
        private val patCipherUrl = "url=(.+?)(\\\\\\\\u0026|\\z)".toPattern()

        private val patVariableFunction =
            "([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(".toPattern()
        private val patFunction = "([{; =])([a-zA-Z$\\_][a-zA-Z0-9$]{0,2})\\(".toPattern()

        private val patDecryptionJsFile = "\\\\/s\\\\/player\\\\/([^\"]+?)\\.js".toPattern()
        private val patDecryptionJsFileWithoutSlash = "/s/player/([^\"]+?).js".toPattern()
        private val patSignatureDecFunction =
            "(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)".toPattern()
    }
}
