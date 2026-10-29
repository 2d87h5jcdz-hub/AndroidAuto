package dev.androidauto.ytcar.player

import android.webkit.JavascriptInterface

/**
 * Ponte JavaScript → Kotlin, exposta na página como `AndroidBridge`.
 * Os métodos rodam numa thread do WebView, não na main thread: o Listener
 * é responsável por voltar para a main antes de mexer em UI.
 */
class PlayerBridge(private val listener: Listener) {

    interface Listener {
        fun onPlayerReady()
        fun onPlayerState(state: Int)
        fun onPlayerTime(seconds: Double)
        fun onVideoData(id: String, title: String)
        fun onPlayerError(code: Int)
    }

    @JavascriptInterface
    fun onReady() = listener.onPlayerReady()

    @JavascriptInterface
    fun onState(state: Int) = listener.onPlayerState(state)

    @JavascriptInterface
    fun onTime(seconds: Double) = listener.onPlayerTime(seconds)

    @JavascriptInterface
    fun onVideoData(id: String?, title: String?) =
        listener.onVideoData(id.orEmpty(), title.orEmpty())

    @JavascriptInterface
    fun onError(code: Int) = listener.onPlayerError(code)

    companion object {
        const val NAME = "AndroidBridge"

        // Estados da YouTube IFrame API
        const val STATE_UNSTARTED = -1
        const val STATE_ENDED = 0
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
        const val STATE_BUFFERING = 3
        const val STATE_CUED = 5
    }
}
