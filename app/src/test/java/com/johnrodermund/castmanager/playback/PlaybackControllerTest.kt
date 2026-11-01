package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastMediaStatus
import com.johnrodermund.castmanager.media.FormatRisk
import com.johnrodermund.castmanager.media.MediaFile
import com.johnrodermund.castmanager.media.MediaServer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    private val castSession = FakeCastSession()
    private var localNetworkAllowed = true
    private var hostAddress: String? = "127.0.0.1"

    private val film = mediaFile("film.mp4", "video/mp4")
    private val song = mediaFile("song.mp3", "audio/mpeg")

    private fun mediaFile(name: String, contentType: String) =
        MediaFile(name, contentType, 4) { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }

    private fun runControllerTest(body: suspend TestScope.(PlaybackController) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val server = MediaServer(hostAddress = { hostAddress })
            body(PlaybackController(castSession, server, { localNetworkAllowed }, backgroundScope))
        }

    private suspend fun PlaybackController.awaitPlayback(status: PlaybackStatus) {
        state.first { it.playback == status }
    }

    private suspend fun responseCode(url: String): Int = withContext(Dispatchers.IO) {
        (URI(url).toURL().openConnection() as HttpURLConnection).responseCode
    }

    private suspend fun awaitNotServed(url: String) = withContext(Dispatchers.IO) {
        while (true) {
            try {
                responseCode(url)
                delay(20)
            } catch (_: IOException) {
                break
            }
        }
    }

    @Test
    fun `nothing plays with no Cast Session and no Media File`() = runControllerTest { controller ->
        assertEquals(PlaybackState(), controller.state.value)
        assertEquals(0, castSession.loadCount)
    }

    @Test
    fun `connecting with no Media File plays nothing`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        assertEquals(PlaybackState("Moonrise TV"), controller.state.value)
        assertEquals(0, castSession.loadCount)
    }

    @Test
    fun `choosing a Media File with no Cast Session waits for one`() = runControllerTest { controller ->
        controller.chooseMediaFile(film)

        assertEquals(PlaybackState(mediaFileName = "film.mp4"), controller.state.value)
        assertEquals(0, castSession.loadCount)
    }

    @Test
    fun `Playback starts when connecting after choosing`() = runControllerTest { controller ->
        controller.chooseMediaFile(film)

        castSession.connect("Moonrise TV")

        val media = castSession.awaitLoad()
        assertEquals("film.mp4", media.title)
        assertEquals("video/mp4", media.contentType)
        assertEquals(200, responseCode(media.url))
    }

    @Test
    fun `Playback starts when choosing after connecting`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        controller.chooseMediaFile(film)

        val media = castSession.awaitLoad()
        assertEquals("film.mp4", media.title)
        assertEquals(200, responseCode(media.url))
    }

    @Test
    fun `the state shows Loading until the Cast Device reports playing`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)
        castSession.awaitLoad()

        assertEquals(PlaybackState("Moonrise TV", "film.mp4", PlaybackStatus.Loading), controller.state.value)

        castSession.report(CastMediaStatus.Playing)
        controller.awaitPlayback(PlaybackStatus.Playing)

        assertEquals(PlaybackState("Moonrise TV", "film.mp4", PlaybackStatus.Playing), controller.state.value)
    }

    @Test
    fun `a new Media File replaces the current one`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)
        val first = castSession.awaitLoad()
        castSession.report(CastMediaStatus.Playing)
        controller.awaitPlayback(PlaybackStatus.Playing)

        controller.chooseMediaFile(song)

        val second = castSession.awaitLoad(number = 2)
        assertEquals("song.mp3", second.title)
        assertNotEquals(first.url, second.url)
        assertEquals(200, responseCode(second.url))
        awaitNotServed(first.url)
    }

    @Test
    fun `the end of the file ends Playback and the Cast Session stays`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)
        val media = castSession.awaitLoad()
        castSession.report(CastMediaStatus.Playing)

        castSession.report(CastMediaStatus.Finished)
        controller.state.first { it.mediaFileName == null }

        assertEquals(PlaybackState("Moonrise TV"), controller.state.value)
        awaitNotServed(media.url)
    }

    @Test
    fun `a failure ends Playback and shows it`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)
        val media = castSession.awaitLoad()

        castSession.report(CastMediaStatus.Failed)
        controller.awaitPlayback(PlaybackStatus.Failed)

        assertEquals(PlaybackState("Moonrise TV", null, PlaybackStatus.Failed), controller.state.value)
        awaitNotServed(media.url)
    }

    @Test
    fun `disconnecting ends Playback and forgets the Media File`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)
        val media = castSession.awaitLoad()
        castSession.report(CastMediaStatus.Playing)
        controller.awaitPlayback(PlaybackStatus.Playing)

        castSession.disconnect()
        controller.state.first { it == PlaybackState() }

        awaitNotServed(media.url)

        castSession.connect("Moonrise TV")
        controller.state.first { it.connectedDevice != null }
        assertEquals(1, castSession.loadCount)
    }

    @Test
    fun `a status that arrives after disconnecting is ignored`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)
        castSession.awaitLoad()

        castSession.disconnect()
        controller.state.first { it == PlaybackState() }
        castSession.report(CastMediaStatus.Playing)
        withContext(Dispatchers.Default) { delay(50) }

        assertEquals(PlaybackState(), controller.state.value)
    }

    @Test
    fun `an unreadable Media File shows a failure`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        controller.onMediaFileUnreadable()

        assertEquals(PlaybackState("Moonrise TV", null, PlaybackStatus.Unreadable), controller.state.value)
    }

    @Test
    fun `with no Wi-Fi address Playback says so`() = runControllerTest { controller ->
        hostAddress = null
        castSession.connect("Moonrise TV")

        controller.chooseMediaFile(film)
        controller.awaitPlayback(PlaybackStatus.NotOnWifi)

        assertEquals(0, castSession.loadCount)
    }

    @Test
    fun `the local network permission is only needed once both are set`() = runControllerTest { controller ->
        localNetworkAllowed = false

        controller.chooseMediaFile(film)
        assertEquals(PlaybackStatus.None, controller.state.value.playback)

        castSession.connect("Moonrise TV")
        assertEquals(PlaybackStatus.NeedsLocalNetwork, controller.state.value.playback)
        assertEquals(0, castSession.loadCount)
    }

    @Test
    fun `allowing the local network starts Playback`() = runControllerTest { controller ->
        localNetworkAllowed = false
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)

        localNetworkAllowed = true
        controller.onLocalNetworkAnswer(allowed = true)

        assertEquals("film.mp4", castSession.awaitLoad().title)
    }

    @Test
    fun `denying the local network explains and serves nothing`() = runControllerTest { controller ->
        localNetworkAllowed = false
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)

        controller.onLocalNetworkAnswer(allowed = false)

        assertEquals(PlaybackStatus.LocalNetworkDenied, controller.state.value.playback)
        assertEquals(0, castSession.loadCount)
    }

    @Test
    fun `choosing again after denying asks again`() = runControllerTest { controller ->
        localNetworkAllowed = false
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film)
        controller.onLocalNetworkAnswer(allowed = false)

        controller.chooseMediaFile(film)

        assertEquals(PlaybackStatus.NeedsLocalNetwork, controller.state.value.playback)
    }

    private suspend fun startPlaying(controller: PlaybackController): String {
        castSession.connect("Moonrise TV")
        controller.state.first { it.connectedDevice != null }
        controller.chooseMediaFile(film)
        val media = castSession.awaitLoad()
        castSession.report(CastMediaStatus.Playing)
        controller.awaitPlayback(PlaybackStatus.Playing)
        return media.url
    }

    @Test
    fun `the state shows position and duration`() = runControllerTest { controller ->
        startPlaying(controller)

        castSession.report(CastMediaStatus.Progress(5_000, 60_000))
        controller.state.first { it.positionMillis == 5_000L }

        assertEquals(60_000L, controller.state.value.durationMillis)
    }

    @Test
    fun `pausing and playing follow the Cast Device`() = runControllerTest { controller ->
        startPlaying(controller)

        controller.pause()
        castSession.report(CastMediaStatus.Paused)
        controller.awaitPlayback(PlaybackStatus.Paused)
        controller.play()
        castSession.report(CastMediaStatus.Playing)
        controller.awaitPlayback(PlaybackStatus.Playing)

        assertEquals(listOf("pause", "play"), castSession.commands)
    }

    @Test
    fun `seeking moves the Cast Device and the position`() = runControllerTest { controller ->
        startPlaying(controller)

        controller.seekTo(30_000)

        assertEquals(listOf("seek 30000"), castSession.commands)
        assertEquals(30_000L, controller.state.value.positionMillis)
    }

    @Test
    fun `Stop ends Playback, stops serving and keeps the Cast Session`() = runControllerTest { controller ->
        val url = startPlaying(controller)

        controller.stop()

        assertEquals(listOf("stop"), castSession.commands)
        assertEquals(PlaybackState("Moonrise TV"), controller.state.value)
        awaitNotServed(url)
    }

    @Test
    fun `the end of the file clears the position like Stop`() = runControllerTest { controller ->
        startPlaying(controller)
        castSession.report(CastMediaStatus.Progress(59_000, 60_000))
        controller.state.first { it.positionMillis == 59_000L }

        castSession.report(CastMediaStatus.Finished)
        controller.state.first { it.mediaFileName == null }

        assertEquals(PlaybackState("Moonrise TV"), controller.state.value)
    }

    @Test
    fun `volume follows the Cast Device`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        castSession.volume.value = 0.4f

        controller.state.first { it.volume == 0.4f }
    }

    @Test
    fun `changing the volume stays between 0 and 1`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        controller.changeVolume(0.5f)
        controller.state.first { it.volume == 1f }
        assertEquals(1f, castSession.volume.value)

        controller.setVolume(0.1f)
        controller.state.first { it.volume == 0.1f }
        controller.changeVolume(-0.5f)
        controller.state.first { it.volume == 0f }
    }

    @Test
    fun `a safe file casts with no warning`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        controller.chooseMediaFile(film, FormatRisk.Safe)

        assertEquals("film.mp4", castSession.awaitLoad().title)
    }

    @Test
    fun `a risky file waits for an answer`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        controller.chooseMediaFile(film, FormatRisk.Risky)

        assertEquals(PlaybackStatus.RiskyFormat, controller.state.value.playback)
        assertEquals(0, castSession.loadCount)
    }

    @Test
    fun `Cast anyway starts Playback`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film, FormatRisk.Risky)

        controller.castAnyway()

        assertEquals("film.mp4", castSession.awaitLoad().title)
    }

    @Test
    fun `cancelling a risky file returns to the empty state`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film, FormatRisk.Risky)

        controller.cancelMediaFile()

        assertEquals(PlaybackState("Moonrise TV"), controller.state.value)
        assertEquals(0, castSession.loadCount)
    }

    @Test
    fun `the warning comes before the local network permission`() = runControllerTest { controller ->
        localNetworkAllowed = false
        castSession.connect("Moonrise TV")
        controller.chooseMediaFile(film, FormatRisk.Risky)
        assertEquals(PlaybackStatus.RiskyFormat, controller.state.value.playback)

        controller.castAnyway()

        assertEquals(PlaybackStatus.NeedsLocalNetwork, controller.state.value.playback)
    }

    @Test
    fun `a risky file replacing Playback stops it while waiting`() = runControllerTest { controller ->
        val url = startPlaying(controller)

        controller.chooseMediaFile(song, FormatRisk.Risky)

        assertEquals(listOf("stop"), castSession.commands)
        assertEquals(PlaybackStatus.RiskyFormat, controller.state.value.playback)
        awaitNotServed(url)
    }

    @Test
    fun `an unreadable file ends the current Playback`() = runControllerTest { controller ->
        val url = startPlaying(controller)

        controller.onMediaFileUnreadable()

        assertEquals(PlaybackState("Moonrise TV", null, PlaybackStatus.Unreadable), controller.state.value)
        awaitNotServed(url)
    }
}
