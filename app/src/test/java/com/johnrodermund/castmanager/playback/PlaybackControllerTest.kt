package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastMediaStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    private val castSession = FakeCastSession()

    private fun runControllerTest(body: suspend TestScope.(PlaybackController) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            body(PlaybackController(castSession, backgroundScope))
        }

    @Test
    fun `nothing is connected or playing with no Cast Session`() = runControllerTest { controller ->
        assertEquals(PlaybackState(), controller.state.value)
        assertEquals(emptyList<Any>(), castSession.loaded)
    }

    @Test
    fun `connecting shows the Cast Device and loads the sample`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        assertEquals(PlaybackState("Moonrise TV", PlaybackStatus.Loading), controller.state.value)
        assertEquals(listOf(SampleMedia), castSession.loaded)
    }

    @Test
    fun `Playback starts when the Cast Device reports playing`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        castSession.report(CastMediaStatus.Playing)

        assertEquals(PlaybackState("Moonrise TV", PlaybackStatus.Playing), controller.state.value)
    }

    @Test
    fun `Playback fails when the Cast Device can't play the sample`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        castSession.report(CastMediaStatus.Failed)

        assertEquals(PlaybackState("Moonrise TV", PlaybackStatus.Failed), controller.state.value)
    }

    @Test
    fun `Playback ends at the end of the file and the Cast Session stays`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        castSession.report(CastMediaStatus.Playing)

        castSession.report(CastMediaStatus.Finished)

        assertEquals(PlaybackState("Moonrise TV", PlaybackStatus.None), controller.state.value)
    }

    @Test
    fun `disconnecting clears the Cast Device and Playback`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        castSession.report(CastMediaStatus.Playing)

        castSession.disconnect()

        assertEquals(PlaybackState(), controller.state.value)
    }

    @Test
    fun `a status that arrives after disconnecting is ignored`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")

        castSession.disconnect()
        castSession.report(CastMediaStatus.Playing)

        assertEquals(PlaybackState(), controller.state.value)
    }

    @Test
    fun `a new Cast Session loads the sample again`() = runControllerTest { controller ->
        castSession.connect("Moonrise TV")
        castSession.report(CastMediaStatus.Playing)
        castSession.disconnect()

        castSession.connect("Kitchen display")

        assertEquals(PlaybackState("Kitchen display", PlaybackStatus.Loading), controller.state.value)
        assertEquals(listOf(SampleMedia, SampleMedia), castSession.loaded)
    }
}
