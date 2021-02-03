package me.echeung.youtubeextractor.internal.util

import org.json.JSONArray
import java.net.URLDecoder
import java.net.URLEncoder

fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>();
    for (i in 1 until length()) {
        list.add(get(i))
    }
    return list
}

fun String.urlEncode(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, Charsets.UTF_8.name())
}
