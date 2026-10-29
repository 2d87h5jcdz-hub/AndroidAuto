package dev.androidauto.ytcar.data

/** Um vídeo do YouTube na fila. O título pode ser provisório até o player informar o real. */
data class Video(
    val id: String,
    val title: String,
    val channel: String = "",
)
