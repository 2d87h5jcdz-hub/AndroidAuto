package dev.androidauto.ytcar.car

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.car.app.SurfaceContainer
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestAppManager
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import dev.androidauto.ytcar.data.AppGraph
import dev.androidauto.ytcar.data.Video
import dev.androidauto.ytcar.player.SurfaceRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerScreenTest {

    private lateinit var carContext: TestCarContext
    private val queue = AppGraph.queue

    @Before fun setUp() {
        queue.clear()
        carContext = newCarContext()
    }

    private fun start(): Pair<PlayerScreen, ScreenController> {
        val screen = PlayerScreen(carContext)
        return screen to ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
    }

    private fun template(screen: PlayerScreen) = screen.onGetTemplate() as NavigationTemplate

    @Test fun navigationTemplateHasFourTextActions() {
        val (screen, _) = start()
        val actions = requireNotNull(template(screen).actionStrip).actions
        assertEquals(listOf("Tocar", "Próximo", "Buscar", "Fila"), actions.map { it.title.toString() })
    }

    @Test fun registersRendererAsSurfaceCallback() {
        start()
        val callback = carContext.getCarService(TestAppManager::class.java).surfaceCallback
        assertTrue(callback is SurfaceRenderer)
    }

    @Test fun destroyUnregistersSurfaceCallback() {
        val (_, controller) = start()
        controller.moveToState(Lifecycle.State.DESTROYED)
        assertNull(carContext.getCarService(TestAppManager::class.java).surfaceCallback)
    }

    @Test fun nextButtonAdvancesQueue() {
        queue.add(Video("aaaaaaaaaaa", "A"))
        queue.add(Video("bbbbbbbbbbb", "B"))
        val (screen, _) = start()
        template(screen).actionStrip!!.actions[1].click()
        assertEquals("bbbbbbbbbbb", queue.current()?.id)
    }

    @Test fun searchAndQueueButtonsPushScreens() {
        val (screen, _) = start()
        val actions = template(screen).actionStrip!!.actions
        actions[2].click()
        assertTrue(carContext.screenManager().screensPushed.last() is SearchScreen)
        actions[3].click()
        assertTrue(carContext.screenManager().screensPushed.last() is QueueScreen)
    }

    @Test fun playWithEmptyQueueDoesNothing() {
        val (screen, _) = start()
        template(screen).actionStrip!!.actions[0].click()
        assertEquals("Tocar", template(screen).actionStrip!!.actions[0].title.toString())
    }

    @Test fun nullSurfaceIsIgnored() {
        start()
        val renderer = carContext.getCarService(TestAppManager::class.java).surfaceCallback as SurfaceRenderer
        renderer.onSurfaceAvailable(SurfaceContainer(null, 800, 480, 160))
        assertNull(renderer.currentWebView)
    }

    /**
     * Com uma Surface de verdade o renderer tenta montar VirtualDisplay + WebView. O Robolectric
     * não reproduz isso fielmente, então aqui só garantimos que nada estoura e que destroy()
     * sempre deixa tudo liberado.
     */
    @Test fun realSurfaceThenDestroyLeavesNothingBehind() {
        val (_, controller) = start()
        val renderer = carContext.getCarService(TestAppManager::class.java).surfaceCallback as SurfaceRenderer
        val surface = Surface(SurfaceTexture(0))
        renderer.onSurfaceAvailable(SurfaceContainer(surface, 800, 480, 160))
        idleMain()
        controller.moveToState(Lifecycle.State.DESTROYED)
        assertNull(renderer.currentWebView)
        // Depois de destroy, uma Surface atrasada do host é só liberada.
        renderer.onSurfaceAvailable(SurfaceContainer(Surface(SurfaceTexture(1)), 800, 480, 160))
        assertNull(renderer.currentWebView)
    }
}
