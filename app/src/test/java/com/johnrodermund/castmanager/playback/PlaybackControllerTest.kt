package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastMediaStatus
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

        assertEquals(PlaybackState("Moonrise TV", null, PlaybackStatus.Failed), controller.state.value)
    }

    @Test
    fun `with no Wi-Fi address Playback fails`() = runControllerTest { controller ->
        hostAddress = null
        castSession.connect("Moonrise TV")

        controller.chooseMediaFile(film)
        controller.awaitPlayback(PlaybackStatus.Failed)

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
}
