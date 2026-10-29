package dev.androidauto.ytcar.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca de vídeos pela YouTube Data API v3 (precisa de uma chave própria, veja o README).
 * Chamada bloqueante: use fora da main thread.
 */
class YouTubeSearch(private val apiKey: String) {

    @Throws(IOException::class)
    fun search(query: String, maxResults: Int = 10): List<Video> {
        val q = URLEncoder.encode(query, "UTF-8")
        val key = URLEncoder.encode(apiKey, "UTF-8")
        val url = URL(
            "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&videoEmbeddable=true&videoSyndicated=true" +
                "&maxResults=$maxResults&q=$q&key=$key",
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("YouTube API respondeu HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Converte a resposta JSON de search.list em vídeos. Público para testes. */
        fun parse(json: String): List<Video> {
            val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
            val result = mutableListOf<Video>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                if (!YouTubeIds.isValidId(id)) continue
                val snippet = item.optJSONObject("snippet")
                val title = decodeEntities(snippet?.optString("title").orEmpty()).ifBlank { id }
                val channel = decodeEntities(snippet?.optString("channelTitle").orEmpty())
                result.add(Video(id = id, title = title, channel = channel))
            }
            return result
        }

        /** A API devolve títulos com entidades HTML (&amp;, &#39;...). */
        internal fun decodeEntities(text: String): String =
            text.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
    }
}
