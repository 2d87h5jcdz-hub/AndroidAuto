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
import androidx.lifecycle.LifecycleOwner
import dev.androidauto.ytcar.data.AppGraph
import dev.androidauto.ytcar.data.Video
import dev.androidauto.ytcar.data.YouTubeIds
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Busca no YouTube. O teclado do Android Auto só aparece com o carro parado.
 * Se o texto digitado for um link ou ID de vídeo, ele entra direto na fila.
 */
class SearchScreen(carContext: CarContext) : Screen(carContext) {

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

        YouTubeIds.extract(query)?.let { id ->
            play(Video(id = id, title = "Vídeo $id"))
            return
        }

        val search = AppGraph.search
        if (search == null) {
            message = NO_KEY_MESSAGE
            invalidate()
            return
        }

        loading = true
        message = null
        invalidate()
        executor.execute {
            val outcome = runCatching { search.search(query) }
            main.post {
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
        if (AppGraph.search == null) NO_KEY_MESSAGE else "Digite o que quer assistir."

    companion object {
        private const val TAG = "YtCarSearch"
        private const val NO_KEY_MESSAGE =
            "Busca sem chave da API (veja o README). Dá para colar um link aqui ou compartilhar pelo celular."
    }
}
