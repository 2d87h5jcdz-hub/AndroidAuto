package dev.androidauto.ytcar.data

import dev.androidauto.ytcar.BuildConfig

/** "Injeção de dependência" mínima: objetos compartilhados pelo processo inteiro. */
object AppGraph {
    val queue = VideoQueue()

    val search: YouTubeSearch? by lazy {
        BuildConfig.YT_API_KEY.takeIf { it.isNotBlank() }?.let { YouTubeSearch(it) }
    }
}
