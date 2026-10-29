package dev.androidauto.ytcar.car

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import dev.androidauto.ytcar.data.AppGraph
import dev.androidauto.ytcar.data.Video
import dev.androidauto.ytcar.data.YouTubeIds
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Busca no YouTube. O teclado do Android Auto só aparece com o carro parado.
 * Se o texto digitado for um link de vídeo, ele entra direto na fila.
 *
 * [search] é a função de busca (bloqueante); null quando não há chave da API.
 * É um parâmetro para os testes poderem trocar a busca real por uma falsa.
 */
class SearchScreen(
    carContext: CarContext,
    private val search: ((String) -> List<Video>)? = AppGraph.search?.let { api -> { q -> api.search(q) } },
) : Screen(carContext) {

    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var results: List<Video> = emptyList()
    private var loading = false
    private var message: String? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                executor.shutdownNow()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) = submit(searchText)
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Buscar no YouTube")
            .setShowKeyboardByDefault(results.isEmpty() && !loading)

        if (loading) {
            builder.setLoading(true)
        } else {
            val list = ItemList.Builder()
            if (results.isEmpty()) {
                list.setNoItemsMessage(message ?: defaultMessage())
            }
            results.forEach { video ->
                val row = Row.Builder()
                    .setTitle(video.title)
                    .setOnClickListener { play(video) }
                if (video.channel.isNotBlank()) row.addText(video.channel)
                list.addItem(row.build())
            }
            builder.setItemList(list.build())
        }
        return builder.build()
    }

    private fun submit(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return

        // Links vão direto para a fila. Um ID "puro" só é aceito sem chave da API: com chave,
        // uma palavra de 11 letras (ex.: "programming") é busca, não ID de vídeo.
        val id = YouTubeIds.extractFromLink(query)
            ?: query.takeIf { search == null && YouTubeIds.isValidId(it) }
        if (id != null) {
            play(Video(id = id, title = "Vídeo $id"))
            return
        }

        val searchFn = search
        if (searchFn == null) {
            message = NO_KEY_MESSAGE
            invalidate()
            return
        }

        loading = true
        message = null
        invalidate()
        executor.execute {
            val outcome = runCatching { searchFn(query) }
            main.post {
                // A tela pode ter sido fechada enquanto a busca rodava.
                if (lifecycle.currentState == Lifecycle.State.DESTROYED) return@post
                loading = false
                outcome
                    .onSuccess { found ->
                        results = found
                        if (found.isEmpty()) message = "Nada encontrado para \"$query\"."
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Busca falhou", error)
                        results = emptyList()
                        message = "Erro na busca: ${error.message ?: "sem detalhes"}"
                    }
                invalidate()
            }
        }
    }

    private fun play(video: Video) {
        AppGraph.queue.add(video, playNow = true)
        screenManager.pop()
    }

    private fun defaultMessage(): String =
        if (search == null) NO_KEY_MESSAGE else "Digite o que quer assistir."

    companion object {
        private const val TAG = "YtCarSearch"
        internal const val NO_KEY_MESSAGE =
            "Busca sem chave da API (veja o README). Dá para colar um link aqui ou compartilhar pelo celular."
    }
}
