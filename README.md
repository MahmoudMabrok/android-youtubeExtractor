Android based YouTube url extractor
=======================================================

**Fork of [HaarigerHarald/android-youtubeExtractor](https://github.com/HaarigerHarald/android-youtubeExtractor)**

This fork features:
- Kotlin coroutine instead of an `AsyncTask`
- Removed deprecated APIs

---

These are the urls to the YouTube video or audio files, so you can stream or download them.

Requires Android **5.0** (API version 21) or higher.

## Gradle

To always build from the latest commit with all updates. Add the JitPack repository:

```java
repositories {
    maven { url "https://jitpack.io" }
}
```

And the dependency:

```java
implementation 'com.github.arkon:android-youtubeExtractor:[commitSha]'
```

## Usage

```kt
val youtubeLink = "http://youtube.com/watch?v=xxxx"

scope.launch {
    val result = YouTubeExtractor(context).extract(youtubeLink)
    result?.videos?.let {
        val downloadUrl = it[22].url
        // ...
    }
    result?.metadata?.let {
        // ...
    }
}
```

`videos` is a map of available media files for one YouTube video, accessible by their itag value.
For further info about itags and their associated formats refer to: [Wikipedia - YouTube Quality and formats](http://en.wikipedia.org/wiki/YouTube#Quality_and_formats).


## Limitations

Those videos aren't working:

* Everything private (private videos, bought movies, ...)
* Unavailable in your country
* RTMPE urls (very rare)


## Modules

* **youtubeExtractor:** The extractor android library.

* **sampleApp:** A simple example downloader App.

## License

Modified BSD license see [LICENSE](LICENSE) and 3rd party licenses depending on what you need
