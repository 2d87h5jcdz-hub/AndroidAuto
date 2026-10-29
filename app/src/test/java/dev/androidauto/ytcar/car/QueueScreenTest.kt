package dev.androidauto.ytcar.car

import androidx.car.app.model.Action
import androidx.car.app.model.ListTemplate
import androidx.car.app.testing.TestCarContext
import dev.androidauto.ytcar.data.AppGraph
import dev.androidauto.ytcar.data.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueScreenTest {

    private lateinit var carContext: TestCarContext
    private val queue = AppGraph.queue

    @Before fun setUp() {
        queue.clear()
        carContext = newCarContext()
    }

    private fun template(screen: QueueScreen) = screen.onGetTemplate() as ListTemplate

    @Test fun emptyQueueShowsMessage() {
        val screen = QueueScreen(carContext)
        startOnTopOfRoot(carContext, screen)
        val list = requireNotNull(template(screen).singleList)
        assertTrue(list.items.isEmpty())
        assertEquals("Fila vazia", list.noItemsMessage.toString())
        assertEquals(Action.BACK, template(screen).headerAction)
    }

    @Test fun rowsMarkCurrentAndShowChannel() {
        queue.add(Video("aaaaaaaaaaa", "Primeiro", channel = "Canal A"))
        queue.add(Video("bbbbbbbbbbb", "Segundo"))
        val screen = QueueScreen(carContext)
        startOnTopOfRoot(carContext, screen)
        val rows = template(screen).singleList!!.rows()
        assertEquals(listOf("▶ Primeiro", "Segundo"), rows.map { it.titleText() })
        assertEquals(listOf("Canal A"), rows[0].texts.map { it.toString() })
        assertTrue(rows[1].texts.isEmpty())
    }

    @Test fun clickingRowSelectsVideoAndGoesBack() {
        queue.add(Video("aaaaaaaaaaa", "Primeiro"))
        queue.add(Video("bbbbbbbbbbb", "Segundo"))
        val screen = QueueScreen(carContext)
        startOnTopOfRoot(carContext, screen)
        template(screen).singleList!!.rows()[1].click()
        assertEquals(1, queue.currentIndex())
        assertTrue(screen in carContext.screenManager().screensRemoved)
    }

    @Test fun queueChangeWhileVisibleRefreshesTemplate() {
        val screen = QueueScreen(carContext)
        val controller = startOnTopOfRoot(carContext, screen)
        val before = controller.templatesReturned.size
        queue.add(Video("ccccccccccc", "Novo pelo celular"))
        idleMain()
        val after = controller.templatesReturned
        assertTrue("esperava um novo template após mudar a fila", after.size > before)
        val rows = (after.last() as ListTemplate).singleList!!.rows()
        assertEquals("▶ Novo pelo celular", rows.single().titleText())
    }
}
