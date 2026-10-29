package dev.androidauto.ytcar.player

import android.annotation.SuppressLint
import android.app.Presentation
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import dev.androidauto.ytcar.data.VideoQueue
import dev.androidauto.ytcar.data.YouTubeIds
import org.json.JSONObject

/**
 * O coração do app.
 *
 * O Android Auto entrega a apps de navegação uma Surface "em branco" para desenhar o mapa.
 * Aqui criamos um VirtualDisplay privado em cima dessa Surface e mostramos nele uma
 * Presentation contendo um WebView com o player oficial do YouTube (IFrame API).
 * Resultado: o vídeo aparece na central no lugar do mapa.
 */
class SurfaceRenderer(
    private val carContext: CarContext,
    private val queue: VideoQueue,
    private val onPlaybackChanged: () -> Unit,
) : SurfaceCallback, PlayerBridge.Listener {

    private val main = Handler(Looper.getMainLooper())

    private var virtualDisplay: VirtualDisplay? = null

    /** Surface atual recebida do host. A documentação exige chamar release() em cada uma. */
    private var surface: Surface? = null
    private var presentation: Presentation? = null
    private var webView: WebView? = null
    private var pageReady = false

    /** ID carregado no player agora. */
    private var loadedId: String? = null

    /** Posição salva para retomar quando a Surface é recriada (ex.: voltar da busca). */
    private var resumeId: String? = null
    private var resumeSeconds = 0.0

    var isPlaying = false
        private set

    var lastError: Int? = null
        private set

    /** Depois de destroy() nada mais pode mexer na fila nem na tela. */
    private var destroyed = false

    /** Só para testes: o WebView montado na Surface atual. */
    internal val currentWebView: WebView?
        get() = webView

    private val queueListener = VideoQueue.Listener { main.post { if (!destroyed) syncWithQueue() } }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        queue.addListener(queueListener)
    }

    // ---------------------------------------------------------------- SurfaceCallback

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val newSurface = surfaceContainer.surface
        if (destroyed) {
            newSurface?.release()
            return
        }
        if (newSurface == null) {
            Log.w(TAG, "Surface nula, ignorando")
            return
        }
        // O host pode chamar de novo só porque o tamanho/DPI mudou. Nesse caso troca a Surface
        // do display existente em vez de recriar o WebView (o vídeo não reinicia).
        val existing = virtualDisplay
        if (existing != null && webView != null) {
            try {
                existing.surface = newSurface
                existing.resize(surfaceContainer.width, surfaceContainer.height, surfaceContainer.dpi)
                if (surface !== newSurface) releaseSurface()
                surface = newSurface
                Log.i(TAG, "Surface trocada ${surfaceContainer.width}x${surfaceContainer.height}")
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Não deu para reaproveitar o display, recriando", t)
            }
        }
        releaseDisplay()
        surface = newSurface
        try {
            val dm = carContext.getSystemService(DisplayManager::class.java)
            // Sem VIRTUAL_DISPLAY_FLAG_PUBLIC o display é PRIVADO, só deste app, e não precisa de
            // permissão especial. OWN_CONTENT_ONLY é o mesmo flag do exemplo oficial do
            // Navigation SDK para Android Auto (nunca espelha outra tela).
            val vd = dm.createVirtualDisplay(
                "YtCarDisplay",
                surfaceContainer.width,
                surfaceContainer.height,
                surfaceContainer.dpi,
                newSurface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
            virtualDisplay = vd

            val pres = Presentation(carContext, vd.display)
            val wv = createWebView(pres)
            pres.setContentView(
                wv,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            pres.show()
            presentation = pres
            webView = wv
            loadPage(wv)
            Log.i(TAG, "Surface pronta ${surfaceContainer.width}x${surfaceContainer.height} @${surfaceContainer.dpi}dpi")
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao montar o display virtual", t)
            releaseDisplay()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        releaseDisplay()
    }

    /** Toque na tela do carro (Car API 5+). Repassa para o WebView como um toque real. */
    override fun onClick(x: Float, y: Float) {
        val wv = webView ?: return
        val down = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        wv.dispatchTouchEvent(downEvent)
        downEvent.recycle()
        main.postDelayed({
            val upEvent = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            webView?.dispatchTouchEvent(upEvent)
            upEvent.recycle()
        }, TAP_DURATION_MS)
    }

    // ---------------------------------------------------------------- Controles

    fun togglePlay() {
        if (queue.current() == null) return
        evaluate("ytToggle()")
    }

    fun seekBy(seconds: Int) = evaluate("ytSeekBy($seconds)")

    /** Libera tudo. Chamado quando a tela do carro é destruída. */
    fun destroy() {
        destroyed = true
        queue.removeListener(queueListener)
        releaseDisplay(notify = false)
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(null) }
        main.removeCallbacksAndMessages(null)
    }

    // ---------------------------------------------------------------- PlayerBridge.Listener (thread do WebView)

    override fun onPlayerReady() {
        main.post {
            if (destroyed) return@post
            pageReady = true
            syncWithQueue(force = true)
        }
    }

    override fun onPlayerState(state: Int) {
        main.post {
            if (destroyed) return@post
            val playing = state == PlayerBridge.STATE_PLAYING
            if (playing) lastError = null
            if (playing != isPlaying) {
                isPlaying = playing
                onPlaybackChanged()
            }
            if (state == PlayerBridge.STATE_ENDED) {
                resumeId = null
                queue.next()
            }
        }
    }

    override fun onPlayerTime(seconds: Double) {
        main.post {
            resumeId = loadedId
            resumeSeconds = seconds
        }
    }

    override fun onVideoData(id: String, title: String) {
        if (!destroyed && YouTubeIds.isValidId(id)) queue.updateTitle(id, title)
    }

    override fun onPlayerError(code: Int) {
        Log.w(TAG, "Erro do player do YouTube: $code")
        main.post {
            if (destroyed) return@post
            lastError = code
            onPlaybackChanged()
        }
    }

    // ---------------------------------------------------------------- Interno

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(pres: Presentation): WebView =
        WebView(pres.context).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // A página vem dos assets via loadDataWithBaseURL; não precisa ler arquivos locais
            // (o padrão era true antes do Android 11).
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(PlayerBridge(this@SurfaceRenderer), PlayerBridge.NAME)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                // Não deixa o WebView navegar para fora do player (ex.: clicar no logo do YouTube).
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame
            }
        }

    private fun loadPage(wv: WebView) {
        pageReady = false
        loadedId = null
        val html = carContext.assets.open("player.html").bufferedReader().use { it.readText() }
        // O YouTube exige um Referer/origem HTTPS válido para embeds; usamos o nome do pacote,
        // como recomenda a documentação de players embutidos em WebView.
        wv.loadDataWithBaseURL("https://${carContext.packageName}/", html, "text/html", "UTF-8", null)
    }

    /** Garante que o player mostra o vídeo atual da fila. */
    private fun syncWithQueue(force: Boolean = false) {
        if (!pageReady) return
        val current = queue.current()
        if (current == null) {
            loadedId = null
            evaluate("ytMessage(${JSONObject.quote(EMPTY_MESSAGE)})")
            return
        }
        if (!force && current.id == loadedId) return
        val start = if (current.id == resumeId) resumeSeconds.toInt() else 0
        loadedId = current.id
        // O ID já foi validado (11 caracteres seguros), então pode ir direto para o JS.
        evaluate("ytLoad(${JSONObject.quote(current.id)}, $start)")
    }

    private fun evaluate(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    private fun releaseDisplay(notify: Boolean = true) {
        pageReady = false
        loadedId = null
        if (isPlaying) {
            isPlaying = false
            if (notify) onPlaybackChanged()
        }
        val wv = webView
        webView = null
        wv?.stopLoading()
        // WebView.destroy() deve ser chamado depois de tirar o WebView da hierarquia de views:
        // por isso a Presentation é fechada antes.
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
        wv?.let {
            it.removeJavascriptInterface(PlayerBridge.NAME)
            it.destroy()
        }
        virtualDisplay?.release()
        virtualDisplay = null
        releaseSurface()
    }

    private fun releaseSurface() {
        surface?.let { runCatching { it.release() } }
        surface = null
    }

    companion object {
        private const val TAG = "YtCarRenderer"
        private const val TAP_DURATION_MS = 60L
        private const val EMPTY_MESSAGE =
            "Fila vazia. Toque em Buscar ou, no celular, compartilhe um vídeo do YouTube com o YT Car."
    }
}
