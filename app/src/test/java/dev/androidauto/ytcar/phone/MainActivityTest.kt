package dev.androidauto.ytcar.phone

import android.content.Intent
import android.widget.TextView
import dev.androidauto.ytcar.R
import dev.androidauto.ytcar.data.AppGraph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private val queue = AppGraph.queue
    private val id = "dQw4w9WgXcQ"
    private var controller: ActivityController<MainActivity>? = null

    @Before fun setUp() = queue.clear()

    @After fun tearDown() {
        // Fecha a Activity para ela tirar o listener da fila compartilhada.
        controller?.pause()?.stop()?.destroy()
    }

    private fun share(text: String?) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun launch(intent: Intent): MainActivity {
        val c = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        controller = c
        return c.get()
    }

    @Test fun shareFromYouTubeAddsToQueue() {
        val activity = launch(share("Olha https://youtu.be/$id?si=x"))
        assertEquals(id, queue.current()?.id)
        val queueText = activity.findViewById<TextView>(R.id.queue).text.toString()
        assertTrue(queueText.contains(id))
    }

    @Test fun shareWithoutLinkShowsError() {
        val activity = launch(share("só texto"))
        assertNull(queue.current())
        val status = activity.findViewById<TextView>(R.id.status).text.toString()
        assertTrue(status.startsWith("Não achei"))
    }

    @Test fun shareWithNoExtraDoesNotCrash() {
        launch(share(null))
        assertNull(queue.current())
    }

    @Test fun newIntentWhileOpenAddsSecondVideo() {
        launch(share("https://youtu.be/$id"))
        controller!!.newIntent(share("https://www.youtube.com/watch?v=aaaaaaaaaaa"))
        assertEquals(2, queue.snapshot().size)
        assertEquals(id, queue.current()?.id)
    }

    @Test fun launcherIntentShowsEmptyQueue() {
        val activity = launch(Intent(Intent.ACTION_MAIN))
        assertEquals("Fila vazia.", activity.findViewById<TextView>(R.id.queue).text.toString())
    }
}
