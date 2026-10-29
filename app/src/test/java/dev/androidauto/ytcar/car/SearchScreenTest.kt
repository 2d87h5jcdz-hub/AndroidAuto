package dev.androidauto.ytcar.car

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.SearchTemplate
import androidx.car.app.testing.TestCarContext
import dev.androidauto.ytcar.data.AppGraph
import dev.androidauto.ytcar.data.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {

    private lateinit var carContext: TestCarContext
    private val queue = AppGraph.queue
    private val id = "dQw4w9WgXcQ"

    @Before fun setUp() {
        queue.clear()
        carContext = newCarContext()
    }

    private fun template(screen: SearchScreen) = screen.onGetTemplate() as SearchTemplate

    private fun submit(screen: SearchScreen, text: String) =
        template(screen).searchCallbackDelegate.sendSearchSubmitted(text, object : OnDoneCallback {})

    /** Espera a busca (que roda em outra thread) terminar e voltar para a main. */
    private fun awaitNotLoading(screen: SearchScreen): SearchTemplate {
        repeat(200) {
            idleMain()
            val t = template(screen)
            if (!t.isLoading) return t
            Thread.sleep(10)
        }
        error("a busca não terminou")
    }

    @Test fun withoutKeyShowsHintAndKeyboard() {
        val screen = SearchScreen(carContext, search = null)
        startOnTopOfRoot(carContext, screen)
        val t = template(screen)
        assertTrue(t.isShowKeyboardByDefault)
        assertEquals(SearchScreen.NO_KEY_MESSAGE, t.itemList!!.noItemsMessage.toString())
    }

    @Test fun linkGoesStraightToQueueAndBack() {
        val screen = SearchScreen(carContext, search = { error("não devia buscar") })
        startOnTopOfRoot(carContext, screen)
        submit(screen, "https://youtu.be/$id?si=abc")
        assertEquals(id, queue.current()?.id)
        assertTrue(screen in carContext.screenManager().screensRemoved)
    }

    @Test fun bareIdIsAcceptedWithoutKey() {
        val screen = SearchScreen(carContext, search = null)
        startOnTopOfRoot(carContext, screen)
        submit(screen, id)
        assertEquals(id, queue.current()?.id)
    }

    @Test fun elevenLetterWordIsSearchedNotTreatedAsId() {
        val queries = CopyOnWriteArrayList<String>()
        val screen = SearchScreen(carContext, search = { q -> queries.add(q); emptyList() })
        startOnTopOfRoot(carContext, screen)
        submit(screen, "programming")
        val t = awaitNotLoading(screen)
        assertEquals(listOf("programming"), queries)
        assertNull(queue.current())
        assertEquals("Nada encontrado para \"programming\".", t.itemList!!.noItemsMessage.toString())
    }

    @Test fun resultsBecomeRowsAndClickPlays() {
        val found = listOf(Video(id, "Resultado", "Canal"), Video("aaaaaaaaaaa", "Outro"))
        val screen = SearchScreen(carContext, search = { found })
        startOnTopOfRoot(carContext, screen)
        submit(screen, "musica")
        val t = awaitNotLoading(screen)
        assertFalse(t.isShowKeyboardByDefault)
        val rows = t.itemList!!.rows()
        assertEquals(listOf("Resultado", "Outro"), rows.map { it.titleText() })
        assertEquals("Canal", rows[0].texts.single().toString())
        rows[1].click()
        assertEquals("aaaaaaaaaaa", queue.current()?.id)
        assertTrue(screen in carContext.screenManager().screensRemoved)
    }

    @Test fun searchErrorIsShown() {
        val screen = SearchScreen(carContext, search = { throw java.io.IOException("HTTP 403") })
        startOnTopOfRoot(carContext, screen)
        submit(screen, "qualquer coisa")
        val t = awaitNotLoading(screen)
        assertEquals("Erro na busca: HTTP 403", t.itemList!!.noItemsMessage.toString())
    }

    @Test fun blankSubmitIsIgnored() {
        val screen = SearchScreen(carContext, search = { error("não devia buscar") })
        startOnTopOfRoot(carContext, screen)
        submit(screen, "   ")
        assertFalse(template(screen).isLoading)
        assertNull(queue.current())
    }
}
