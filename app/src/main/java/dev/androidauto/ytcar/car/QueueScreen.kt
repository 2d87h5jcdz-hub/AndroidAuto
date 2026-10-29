package dev.androidauto.ytcar.car

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.androidauto.ytcar.data.AppGraph
import dev.androidauto.ytcar.data.VideoQueue

/** Lista da fila. Tocar num item troca o vídeo atual. */
class QueueScreen(carContext: CarContext) : Screen(carContext) {

    private val queue = AppGraph.queue
    private val main = Handler(Looper.getMainLooper())

    // A fila muda por fora (celular, título real vindo do player): redesenha a lista.
    // invalidate() precisa rodar na main thread.
    private val listener = VideoQueue.Listener { main.post { invalidate() } }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = queue.addListener(listener)
            override fun onStop(owner: LifecycleOwner) {
                queue.removeListener(listener)
                main.removeCallbacksAndMessages(null)
            }
        })
    }

    override fun onGetTemplate(): Template {
        val items = queue.snapshot()
        val current = queue.currentIndex()
        val list = ItemList.Builder()
        if (items.isEmpty()) {
            list.setNoItemsMessage("Fila vazia")
        }
        items.forEachIndexed { index, video ->
            val prefix = if (index == current) "▶ " else ""
            val row = Row.Builder()
                .setTitle(prefix + video.title)
                .setOnClickListener {
                    queue.select(index)
                    screenManager.pop()
                }
            if (video.channel.isNotBlank()) row.addText(video.channel)
            list.addItem(row.build())
        }
        return ListTemplate.Builder()
            .setTitle("Fila")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
