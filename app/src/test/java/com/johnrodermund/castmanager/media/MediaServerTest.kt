package com.johnrodermund.castmanager.media

import java.io.ByteArrayInputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaServerTest {
    private val bytes = ByteArray(300_000) { (it % 251).toByte() }
    private val file = MediaFile("clip.mp4", "video/mp4", bytes.size.toLong()) { ByteArrayInputStream(bytes) }
    private val server = MediaServer(hostAddress = { "127.0.0.1" })

    @After
    fun stopServer() = runBlocking { server.stop() }

    private fun startServer(media: MediaFile = file): String = runBlocking { server.start(media)!! }

    private fun request(url: String, method: String = "GET", range: String? = null): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            range?.let { setRequestProperty("Range", it) }
        }

    @Test
    fun `a full request returns the whole file`() {
        val response = request(startServer())

        assertEquals(200, response.responseCode)
        assertEquals("300000", response.getHeaderField("Content-Length"))
        assertEquals("bytes", response.getHeaderField("Accept-Ranges"))
        assertArrayEquals(bytes, response.inputStream.readBytes())
    }

    @Test
    fun `a Range request returns only those bytes`() {
        val response = request(startServer(), range = "bytes=1000-1999")

        assertEquals(206, response.responseCode)
        assertEquals("bytes 1000-1999/300000", response.getHeaderField("Content-Range"))
        assertArrayEquals(bytes.copyOfRange(1000, 2000), response.inputStream.readBytes())
    }

    @Test
    fun `an open ended Range request returns the rest of the file`() {
        val response = request(startServer(), range = "bytes=299000-")

        assertEquals(206, response.responseCode)
        assertArrayEquals(bytes.copyOfRange(299_000, 300_000), response.inputStream.readBytes())
    }

    @Test
    fun `a suffix Range request returns the end of the file`() {
        val response = request(startServer(), range = "bytes=-500")

        assertEquals(206, response.responseCode)
        assertArrayEquals(bytes.copyOfRange(299_500, 300_000), response.inputStream.readBytes())
    }

    @Test
    fun `a Range past the end of the file is refused`() {
        val response = request(startServer(), range = "bytes=300000-")

        assertEquals(416, response.responseCode)
    }

    @Test
    fun `the response has the file's content type`() {
        val audio = MediaFile("song.mp3", "audio/mpeg", 3) { ByteArrayInputStream(ByteArray(3)) }

        assertEquals("audio/mpeg", request(startServer(audio)).contentType)
    }

    @Test
    fun `a HEAD request describes the file with no body`() {
        val response = request(startServer(), method = "HEAD")

        assertEquals(200, response.responseCode)
        assertEquals("300000", response.getHeaderField("Content-Length"))
        assertEquals("video/mp4", response.contentType)
    }

    @Test
    fun `a wrong URL returns not found`() {
        val url = startServer()

        assertEquals(404, request(url.dropLast(1) + "x").responseCode)
        assertEquals(404, request(url.substringBeforeLast("/") + "/").responseCode)
    }

    @Test
    fun `nothing is served once stopped`() {
        val url = startServer()

        runBlocking { server.stop() }

        assertThrows(ConnectException::class.java) { request(url).responseCode }
    }

    @Test
    fun `a new file replaces the old one at a new URL`() {
        val first = startServer()

        val second = startServer()

        assertNotEquals(first, second)
        assertThrows(ConnectException::class.java) { request(first).responseCode }
        assertEquals(200, request(second).responseCode)
    }

    @Test
    fun `each start asks for the current address`() {
        var address = "127.0.0.1"
        val moving = MediaServer(hostAddress = { address })

        runBlocking {
            moving.start(file)
            address = "localhost"
            assert(moving.start(file)!!.startsWith("http://localhost:"))
            moving.stop()
        }
    }

    @Test
    fun `with no address nothing is served`() {
        val offline = MediaServer(hostAddress = { null })

        assertNull(runBlocking { offline.start(file) })
    }
}
