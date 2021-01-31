package me.echeung.youtubeextractor.internal.util

import org.json.JSONArray
import java.net.URLDecoder
import java.net.URLEncoder

fun JSONArray.toList(): List<Any> {
    val list = ArrayList<Any>();
    for (i in 1 until this.length()) {
        list.add(get(i))
    }
    return list
}

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, "UTF-8")
}
