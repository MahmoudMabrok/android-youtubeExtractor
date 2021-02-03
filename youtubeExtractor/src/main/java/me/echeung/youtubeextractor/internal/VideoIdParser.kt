package me.echeung.youtubeextractor.internal

object VideoIdParser {

    fun getVideoId(url: String?): String? {
        if (url == null) {
            return null
        }

        var matcher = PAGE_LINK_PATTERN.matcher(url)
        if (matcher.find()) {
            return matcher.group(3)
        }

        matcher = SHORT_LINK_PATTERN.matcher(url)
        if (matcher.find()) {
            return matcher.group(3)
        }
        if (url.matches(GRAPH_REGEX)) {
            return url
        }

        return null
    }

    private val PAGE_LINK_PATTERN by lazy {
        "(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)( |\\z|&)".toPattern()
    }

    private val SHORT_LINK_PATTERN by lazy {
        "(http|https)://(www\\.|)youtu.be/(.+?)( |\\z|&)".toPattern()
    }

    private val GRAPH_REGEX by lazy {
        "\\p{Graph}+?".toRegex()
    }
}
