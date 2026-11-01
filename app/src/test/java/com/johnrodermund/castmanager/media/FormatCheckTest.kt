package com.johnrodermund.castmanager.media

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatCheckTest {
    private val h264 = "video/avc"
    private val hevc = "video/hevc"
    private val aac = "audio/mp4a-latm"
    private val ac3 = "audio/ac3"
    private val mp3 = "audio/mpeg"

    @Test
    fun `safe formats`() {
        listOf(
            MediaFormat("video/mp4", listOf(h264, aac)),
            MediaFormat("video/mp4", listOf(h264)),
            MediaFormat("video/mp4", listOf(h264, aac, "application/x-subrip")),
            MediaFormat("audio/mpeg", listOf(mp3)),
            MediaFormat("audio/mp4", listOf(aac)),
            MediaFormat("audio/aac", listOf(aac)),
        ).forEach { assertEquals(it.toString(), FormatRisk.Safe, FormatCheck.check(it)) }
    }

    @Test
    fun `risky formats`() {
        listOf(
            MediaFormat("video/mp4", listOf(hevc, aac)),
            MediaFormat("video/mp4", listOf(h264, ac3)),
            MediaFormat("video/mp4", listOf(h264, aac, ac3)),
            MediaFormat("video/x-matroska", listOf(h264, aac)),
            MediaFormat("audio/flac", listOf("audio/flac")),
            MediaFormat("audio/mp4", emptyList()),
            MediaFormat("video/mp4", emptyList()),
        ).forEach { assertEquals(it.toString(), FormatRisk.Risky, FormatCheck.check(it)) }
    }
}
