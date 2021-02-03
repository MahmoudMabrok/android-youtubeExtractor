package me.echeung.youtubeextractor.internal.extractor

import me.echeung.youtubeextractor.YTFile
import me.echeung.youtubeextractor.internal.CipherUtil
import me.echeung.youtubeextractor.internal.FORMAT_MAP
import me.echeung.youtubeextractor.internal.util.Logger
import me.echeung.youtubeextractor.internal.util.toList
import me.echeung.youtubeextractor.internal.util.urlDecode
import org.json.JSONArray
import org.json.JSONObject

class NonLiveStreamExtractor(
    private val cipherUtil: CipherUtil,
    private val log: Logger
) : Extractor {

    override suspend fun getFiles(videoId: String, streamInfo: JSONObject): Map<Int, YTFile> {
        val streamingData = streamInfo.getJSONObject("streamingData")
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
        val formats = streamingData.optJSONArray("formats") ?: JSONArray()

        val encryptedSignatures = mutableListOf<Pair<Int, String>>()

        var files = (adaptiveFormats.toList() + formats.toList())
            .map {
                val obj = it as JSONObject
                val itag = obj.getInt("itag")
                val url: String? = obj.optString("url")
                val cipher: String? = obj.optString("signatureCipher")
                Triple(itag, url, cipher)
            }
            .map { (itag, url, cipher) ->
                var extractedUrl = url
                if (cipher != null) {
                    var matcher = CIPHER_URL_PATTERN.matcher(cipher)
                    if (matcher.find()) {
                        extractedUrl = matcher.group(1).urlDecode()
                        matcher = ENCIPHERED_SIGNATURE_PATTERN.matcher(cipher)
                        if (matcher.find()) {
                            var sig = matcher.group(1).urlDecode()
                            sig = sig.replace("\\u0026", "&")
                            sig = sig.split("&").toTypedArray()[0]
                            encryptedSignatures.add(itag to sig)
                        }
                    }
                }

                itag to extractedUrl
            }
            .filter { (itag, url) -> FORMAT_MAP[itag] != null && url?.contains("&source=yt_otf&") == false }
            .map { (itag, url) -> YTFile(FORMAT_MAP[itag]!!, url!!) }
            .associateBy { it.format.itag }

        if (encryptedSignatures.isNotEmpty()) {
            log.d("Decipher signatures: " + encryptedSignatures.size + ", files: " + files.size)

            val signatures = cipherUtil.decipherSignatures(videoId, encryptedSignatures)
            signatures ?: return emptyMap()

            files = files.values
                .map {
                    val itag = it.format.itag
                    YTFile(FORMAT_MAP[itag]!!, "${it.url}&sig=${signatures[itag]}")
                }
                .associateBy { it.format.itag }
        }

        return files
    }

    companion object {
        private val CIPHER_URL_PATTERN by lazy {
            "url=(.+?)(\\\\\\\\u0026|\\z)".toPattern()
        }
        private val ENCIPHERED_SIGNATURE_PATTERN by lazy {
            "s=(.{10,}?)(\\\\\\\\u0026|\\z)".toPattern()
        }
    }
}

