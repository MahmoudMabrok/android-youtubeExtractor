package at.huber.youtubeExtractor

/**
 * See:
 * http://en.wikipedia.org/wiki/YouTube#Quality_and_formats
 * https://github.com/rg3/youtube-dl/blob/40a051fa9f48000f311f243c40e3cae588420738/youtube_dl/extractor/youtube.py#L379
 * https://github.com/jdf76/plugin.video.youtube/blob/bf9a361d44f4841192233ef8a4411dc74a538ec0/resources/lib/youtube_plugin/youtube/helper/video_info.py#L22
 */
class FormatMap {
    @kotlin.jvm.JvmField
    val FORMAT_MAP = mapOf(
        // Video and Audio
        Pair(5, Format(5, "flv", 240, Format.VCodec.H263, Format.ACodec.MP3, 64, false)),
        Pair(6, Format(6, "flv", 270, Format.VCodec.H263, Format.ACodec.MP3, 64, false)),
        Pair(17, Format(17, "3gp", 144, Format.VCodec.MPEG4, Format.ACodec.AAC, 24, false)),
        Pair(18, Format(18, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, 96, false)),
        Pair(22, Format(22, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, 192, false)),
        Pair(34, Format(34, "3gp", 360, Format.VCodec.H264, Format.ACodec.AAC, 128, false)),
        Pair(35, Format(35, "flv", 480, Format.VCodec.H264, Format.ACodec.AAC, 128, false)),
        Pair(36, Format(36, "3gp", 240, Format.VCodec.MPEG4, Format.ACodec.AAC, 32, false)),
        Pair(37, Format(37, "mp4", 1080, Format.VCodec.H264, Format.ACodec.AAC, 192, false)),
        Pair(38, Format(38, "mp4", 3072, Format.VCodec.H264, Format.ACodec.AAC, 192, false)),
        Pair(43, Format(43, "webm", 360, Format.VCodec.VP8, Format.ACodec.VORBIS, 128, false)),
        Pair(44, Format(44, "webm", 480, Format.VCodec.VP8, Format.ACodec.VORBIS, 128, false)),
        Pair(45, Format(45, "webm", 720, Format.VCodec.VP8, Format.ACodec.VORBIS, 192, false)),
        Pair(46, Format(46, "webm", 1080, Format.VCodec.VP8, Format.ACodec.VORBIS, 192, false)),
        Pair(59, Format(59, "mp4", 480, Format.VCodec.H264, Format.ACodec.AAC, 128, false)),
        Pair(78, Format(78, "mp4", 480, Format.VCodec.H264, Format.ACodec.AAC, 128, false)),

        // 3D Videos
        Pair(82, Format(82, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, 128, false)),
        Pair(83, Format(83, "mp4", 480, Format.VCodec.H264, Format.ACodec.AAC, 128, false)),
        Pair(84, Format(84, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, 192, false)),
        Pair(85, Format(85, "mp4", 1080, Format.VCodec.H264, Format.ACodec.AAC, 192, false)),
        Pair(100, Format(100, "webm", 360, Format.VCodec.VP8, Format.ACodec.VORBIS, 128, false)),
        Pair(101, Format(101, "webm", 480, Format.VCodec.VP8, Format.ACodec.VORBIS, 128, false)),
        Pair(102, Format(102, "webm", 720, Format.VCodec.VP8, Format.ACodec.VORBIS, 128, false)),

        // HLS Live Stream
        Pair(91, Format(91, "mp4", 144, Format.VCodec.H264, Format.ACodec.AAC, 48, false, true)),
        Pair(92, Format(92, "mp4", 240, Format.VCodec.H264, Format.ACodec.AAC, 48, false, true)),
        Pair(93, Format(93, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true)),
        Pair(94, Format(94, "mp4", 480, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true)),
        Pair(95, Format(95, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, 256, false, true)),
        Pair(96, Format(96, "mp4", 1080, Format.VCodec.H264, Format.ACodec.AAC, 256, false, true)),
        Pair(120, Format(120, "flv", 720, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true)),
        Pair(127, Format(127, "ts", 0, Format.VCodec.NONE, Format.ACodec.AAC, 96, false, true)),
        Pair(128, Format(128, "ts", 0, Format.VCodec.NONE, Format.ACodec.AAC, 96, false, true)),
        Pair(132, Format(132, "mp4", 240, Format.VCodec.H264, Format.ACodec.AAC, 256, false, true)),
        Pair(151, Format(151, "mp4", 72, Format.VCodec.H264, Format.ACodec.AAC, 256, false, true)),
        Pair(300, Format(300, "ts", 720, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true)),
        Pair(301, Format(301, "ts", 1080, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true)),

        // Dash Video
        Pair(133, Format(133, "mp4", 240, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(134, Format(134, "mp4", 360, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(135, Format(135, "mp4", 480, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(136, Format(136, "mp4", 720, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(137, Format(137, "mp4", 1080, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(138, Format(138, "mp4", 0, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(160, Format(160, "mp4", 144, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(212, Format(212, "mp4", 480, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(264, Format(264, "mp4", 1440, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(266, Format(266, "mp4", 2160, Format.VCodec.H264, Format.ACodec.NONE, true)),
        Pair(298, Format(298, "mp4", 720, Format.VCodec.H264, 60, Format.ACodec.NONE, true)),
        Pair(299, Format(299, "mp4", 1080, Format.VCodec.H264, 60, Format.ACodec.NONE, true)),

        // Dash Audio
        Pair(139, Format(139, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 48, true)),
        Pair(140, Format(140, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 128, true)),
        Pair(141, Format(141, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 256, true)),
        Pair(256, Format(256, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 0, true)),
        Pair(258, Format(258, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 0, true)),

        // WEBM Dash Video
        Pair(167, Format(167, "webm", 360, Format.VCodec.VP8, Format.ACodec.NONE, true)),
        Pair(168, Format(168, "webm", 480, Format.VCodec.VP8, Format.ACodec.NONE, true)),
        Pair(169, Format(169, "webm", 720, Format.VCodec.VP8, Format.ACodec.NONE, true)),
        Pair(170, Format(170, "webm", 1080, Format.VCodec.VP8, Format.ACodec.NONE, true)),
        Pair(218, Format(218, "webm", 480, Format.VCodec.VP8, Format.ACodec.NONE, true)),
        Pair(219, Format(219, "webm", 480, Format.VCodec.VP8, Format.ACodec.NONE, true)),
        Pair(278, Format(278, "webm", 144, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(242, Format(242, "webm", 240, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(243, Format(243, "webm", 360, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(244, Format(244, "webm", 480, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(245, Format(245, "webm", 480, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(246, Format(246, "webm", 480, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(247, Format(247, "webm", 720, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(248, Format(248, "webm", 1080, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(271, Format(271, "webm", 1440, Format.VCodec.VP9, Format.ACodec.NONE, true)),

        // itag 272 videos are either 3840x2160 (e.g. RtoitU2A-3E) or 7680x4320 (sLprVF6d7Ug)
        Pair(272, Format(272, "webm", 2160, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(313, Format(313, "webm", 2160, Format.VCodec.VP9, Format.ACodec.NONE, true)),
        Pair(302, Format(302, "webm", 720, Format.VCodec.VP9, 60, Format.ACodec.NONE, true)),
        Pair(303, Format(303, "webm", 1080, Format.VCodec.VP9, 60, Format.ACodec.NONE, true)),
        Pair(308, Format(308, "webm", 1440, Format.VCodec.VP9, 60, Format.ACodec.NONE, true)),
        Pair(315, Format(315, "webm", 2160, Format.VCodec.VP9, 60, Format.ACodec.NONE, true)),

        // WEBM Dash Audio
        Pair(171, Format(171, "webm", Format.VCodec.NONE, Format.ACodec.VORBIS, 128, true)),
        Pair(172, Format(172, "webm", Format.VCodec.NONE, Format.ACodec.VORBIS, 256, true)),
        Pair(249, Format(249, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 48, true)),
        Pair(250, Format(250, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 64, true)),
        Pair(251, Format(251, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 160, true)),
    )
}
