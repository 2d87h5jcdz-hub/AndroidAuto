package dev.androidauto.ytcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSearchParseTest {

    @Test fun parsesItemsAndDecodesEntities() {
        val json = """
            {"items":[
              {"id":{"kind":"youtube#video","videoId":"dQw4w9WgXcQ"},
               "snippet":{"title":"Rock &amp; Roll &#39;Live&#39;","channelTitle":"Canal &quot;X&quot;"}},
              {"id":{"kind":"youtube#channel","channelId":"abc"},"snippet":{"title":"canal"}},
              {"id":{"videoId":"invalido"},"snippet":{"title":"ruim"}}
            ]}
        """.trimIndent()
        val videos = YouTubeSearch.parse(json)
        assertEquals(1, videos.size)
        assertEquals("dQw4w9WgXcQ", videos[0].id)
        assertEquals("Rock & Roll 'Live'", videos[0].title)
        assertEquals("Canal \"X\"", videos[0].channel)
    }

    @Test fun emptyResponse() {
        assertTrue(YouTubeSearch.parse("{}").isEmpty())
    }
}
