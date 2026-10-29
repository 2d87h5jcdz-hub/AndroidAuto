package dev.androidauto.ytcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQueueTest {

    private fun v(n: Int) = Video(id = "video%05d__".format(n).take(11), title = "Vídeo $n")

    @Test fun firstAddedBecomesCurrent() {
        val q = VideoQueue()
        q.add(v(1))
        q.add(v(2))
        assertEquals(v(1), q.current())
        assertEquals(2, q.snapshot().size)
    }

    @Test fun playNowSwitchesCurrent() {
        val q = VideoQueue()
        q.add(v(1))
        q.add(v(2), playNow = true)
        assertEquals(v(2), q.current())
    }

    @Test fun noDuplicates() {
        val q = VideoQueue()
        q.add(v(1))
        q.add(v(2))
        q.add(v(1), playNow = true)
        assertEquals(2, q.snapshot().size)
        assertEquals(0, q.currentIndex())
    }

    @Test fun nextAndPreviousStopAtEdges() {
        val q = VideoQueue()
        q.add(v(1)); q.add(v(2))
        assertEquals(v(2), q.next())
        assertNull(q.next())
        assertEquals(v(1), q.previous())
        assertNull(q.previous())
    }

    @Test fun selectOutOfRangeIsIgnored() {
        val q = VideoQueue()
        q.add(v(1))
        assertNull(q.select(5))
        assertEquals(0, q.currentIndex())
    }

    @Test fun updateTitleNotifiesOnlyOnChange() {
        val q = VideoQueue()
        var calls = 0
        q.add(v(1))
        q.addListener { calls++ }
        q.updateTitle(v(1).id, "Título real")
        q.updateTitle(v(1).id, "Título real")
        q.updateTitle(v(1).id, "  ")
        assertEquals(1, calls)
        assertEquals("Título real", q.current()?.title)
    }

    @Test fun clearResets() {
        val q = VideoQueue()
        q.add(v(1))
        q.clear()
        assertNull(q.current())
        assertEquals(-1, q.currentIndex())
    }

    @Test fun concurrentAddsKeepQueueConsistent() {
        val q = VideoQueue()
        val notifications = java.util.concurrent.atomic.AtomicInteger()
        q.addListener { notifications.incrementAndGet(); q.snapshot() } // listener pode ler a fila
        val threads = (0 until 8).map { t ->
            Thread { repeat(50) { i -> q.add(v(t * 50 + i)); q.next() } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val items = q.snapshot()
        assertEquals(400, items.size)
        assertEquals(400, items.map { it.id }.toSet().size)
        assertEquals(true, q.currentIndex() in items.indices)
        assertEquals(true, notifications.get() >= 400)
    }

    @Test fun removedListenerIsNotCalled() {
        val q = VideoQueue()
        var calls = 0
        val l = VideoQueue.Listener { calls++ }
        q.addListener(l)
        q.add(v(1))
        q.removeListener(l)
        q.add(v(2))
        assertEquals(1, calls)
    }
}
