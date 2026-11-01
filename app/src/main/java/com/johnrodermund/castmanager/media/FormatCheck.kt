package com.johnrodermund.castmanager.media

enum class FormatRisk { Safe, Risky }

/** A Media File's container and the MIME type of each of its tracks. */
data class MediaFormat(val container: String, val tracks: List<String>)

/** Conservative and not device-specific: anything this can't recognise is risky. */
object FormatCheck {
    private val videoContainers = setOf("video/mp4")
    private val audioContainers = setOf("audio/mp4", "audio/x-m4a", "audio/mpeg", "audio/mp3", "audio/aac", "audio/aac-adts")
    private const val H264 = "video/avc"
    private const val AAC = "audio/mp4a-latm"
    private const val MP3 = "audio/mpeg"

    fun check(format: MediaFormat): FormatRisk {
        val video = format.tracks.filter { it.startsWith("video/") }
        val audio = format.tracks.filter { it.startsWith("audio/") }
        val safe = if (video.isEmpty()) {
            format.container in audioContainers && audio.isNotEmpty() && audio.all { it == AAC || it == MP3 }
        } else {
            format.container in videoContainers && video.all { it == H264 } && audio.all { it == AAC }
        }
        return if (safe) FormatRisk.Safe else FormatRisk.Risky
    }
}
