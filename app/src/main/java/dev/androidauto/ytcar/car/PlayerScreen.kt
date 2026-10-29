package dev.androidauto.ytcar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.androidauto.ytcar.data.AppGraph
import dev.androidauto.ytcar.player.SurfaceRenderer

/**
 * Tela principal. Usa o NavigationTemplate só pela Surface que ele oferece:
 * o "mapa" é o vídeo, e a barra de ações tem os controles.
 */
class PlayerScreen(carContext: CarContext) : Screen(carContext) {

    private val queue = AppGraph.queue
    private val renderer = SurfaceRenderer(carContext, queue) { invalidate() }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                renderer.destroy()
            }
        })
    }

    override fun onGetTemplate(): Template {
        // NavigationTemplate aceita no máximo 4 ações na barra.
        val actions = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(if (renderer.isPlaying) "Pausar" else "Tocar")
                    .setOnClickListener { renderer.togglePlay() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle("Próximo")
                    .setOnClickListener { queue.next() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle("Buscar")
                    .setOnClickListener { screenManager.push(SearchScreen(carContext)) }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle("Fila")
                    .setOnClickListener { screenManager.push(QueueScreen(carContext)) }
                    .build(),
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actions)
            .build()
    }
}
