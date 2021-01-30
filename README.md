Android based YouTube url extractor
=======================================================

**Fork of [HaarigerHarald/android-youtubeExtractor](https://github.com/HaarigerHarald/android-youtubeExtractor)**

This fork features:
- Kotlin coroutine instead of an `AsyncTask`
- Removed deprecated APIs

---

These are the urls to the YouTube video or audio files, so you can stream or download them.
It features an age verification circumvention and a signature deciphering method (mainly for vevo videos).

* Dependency: [js-evaluator-for-android](https://github.com/evgenyneu/js-evaluator-for-android)

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
    result?.files?.let {
        val downloadUrl = it[22].url
        // ...
    }
    result?.videoMetadata?.let {
        // ...
    }
}
```

The files SparseArray is a map of available media files for one YouTube video, accessible by their itag
value. For further infos about itags and their associated formats refer to: [Wikipedia - YouTube Quality and formats](http://en.wikipedia.org/wiki/YouTube#Quality_and_formats).

## Requirements

Android **5.0** (API version 21) and up for Webview Javascript execution see: [js-evaluator-for-android](https://github.com/evgenyneu/js-evaluator-for-android).
Not signature enciphered Videos may work on lower Android versions (untested).

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
