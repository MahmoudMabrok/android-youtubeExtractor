package me.echeung.youtubeextractor

data class Format(
    /**
     * An identifier used by youtube for different formats.
     */
    val itag: Int,
    /**
     * The file extension and container format like "mp4"
     */
    val ext: String,
    /**
     * The pixel height of the video stream or -1 for audio files.
     */
    val height: Int,
    /**
     * Get the frames per second
     */
    val fps: Int,
    val videoCodec: VideoCodec? = null,
    val audioCodec: AudioCodec? = null,
    /**
     * Audio bitrate in kbit/s or -1 if there is no audio track.
     */
    val audioBitrate: Int,
    val isDashContainer: Boolean,
    val isHlsContent: Boolean
) {
    enum class VideoCodec {
        H263, H264, MPEG4, VP8, VP9, NONE
    }

    enum class AudioCodec {
        MP3, AAC, VORBIS, OPUS, NONE
    }

    internal constructor(
        itag: Int,
        ext: String,
        height: Int,
        videoCodec: VideoCodec?,
        audioCodec: AudioCodec?,
        isDashContainer: Boolean
    ) : this(
        itag = itag,
        ext = ext,
        height = height,
        isDashContainer = isDashContainer,
        fps = 30,
        audioBitrate = -1,
        isHlsContent = false
    )

    internal constructor(
        itag: Int,
        ext: String,
        videoCodec: VideoCodec?,
        audioCodec: AudioCodec?,
        audioBitrate: Int,
        isDashContainer: Boolean
    ) : this(
        itag = itag,
        ext = ext,
        audioBitrate = audioBitrate,
        isDashContainer = isDashContainer,
        height = -1,
        fps = 30,
        isHlsContent = false
    )

    internal constructor(
        itag: Int,
        ext: String,
        height: Int,
        videoCodec: VideoCodec?,
        audioCodec: AudioCodec?,
        audioBitrate: Int,
        isDashContainer: Boolean
    ) : this(
        itag = itag,
        ext = ext,
        height = height,
        audioBitrate = audioBitrate,
        isDashContainer = isDashContainer,
        fps = 30,
        isHlsContent = false
    )

    internal constructor(
        itag: Int,
        ext: String,
        height: Int,
        videoCodec: VideoCodec?,
        audioCodec: AudioCodec?,
        audioBitrate: Int,
        isDashContainer: Boolean,
        isHlsContent: Boolean
    ) : this(
        itag = itag,
        ext = ext,
        height = height,
        audioBitrate = audioBitrate,
        isDashContainer = isDashContainer,
        isHlsContent = isHlsContent,
        fps = 30
    )

    internal constructor(
        itag: Int,
        ext: String,
        height: Int,
        videoCodec: VideoCodec?,
        fps: Int,
        audioCodec: AudioCodec?,
        isDashContainer: Boolean
    ) : this(
        itag = itag,
        ext = ext,
        height = height,
        fps = fps,
        isDashContainer = isDashContainer,
        audioBitrate = -1,
        isHlsContent = false
    )
}
