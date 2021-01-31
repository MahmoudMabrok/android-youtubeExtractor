package me.echeung.youtubeextractor.internal.util

import org.json.JSONArray

fun JSONArray.toList(): List<Any> {
    val list = ArrayList<Any>();
    for (i in 1 until this.length()) {
        list.add(get(i))
    }
    return list
}
