package dev.androidauto.ytcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeIdsTest {

    private val id = "dQw4w9WgXcQ"

    @Test fun bareId() = assertEquals(id, YouTubeIds.extract(id))

    @Test fun shortLinkWithTracking() =
        assertEquals(id, YouTubeIds.extract("https://youtu.be/$id?si=AbCdEf123"))

    @Test fun watchLink() =
        assertEquals(id, YouTubeIds.extract("https://www.youtube.com/watch?v=$id"))

    @Test fun watchLinkWithOtherParamsFirst() =
        assertEquals(id, YouTubeIds.extract("https://m.youtube.com/watch?feature=share&v=$id&t=42"))

    @Test fun shorts() =
        assertEquals(id, YouTubeIds.extract("https://youtube.com/shorts/$id?feature=share"))

    @Test fun embedAndLive() {
        assertEquals(id, YouTubeIds.extract("https://www.youtube.com/embed/$id"))
        assertEquals(id, YouTubeIds.extract("https://www.youtube.com/live/$id"))
        assertEquals(id, YouTubeIds.extract("https://www.youtube-nocookie.com/embed/$id"))
    }

    @Test fun sharedTextFromYouTubeApp() =
        assertEquals(id, YouTubeIds.extract("Olha esse vídeo! https://youtu.be/$id?si=x"))

    @Test fun trimsWhitespace() = assertEquals(id, YouTubeIds.extract("  $id \n"))

    @Test fun rejectsGarbage() {
        assertNull(YouTubeIds.extract(null))
        assertNull(YouTubeIds.extract(""))
        assertNull(YouTubeIds.extract("receita de bolo"))
        assertNull(YouTubeIds.extract("https://example.com/watch?v=$id"))
        assertNull(YouTubeIds.extract("https://youtu.be/curto"))
    }

    @Test fun rejectsIdLongerThanEleven() =
        assertNull(YouTubeIds.extract("https://youtu.be/${id}X"))
}
