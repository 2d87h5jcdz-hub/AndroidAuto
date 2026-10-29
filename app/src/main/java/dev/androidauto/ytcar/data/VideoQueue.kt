package dev.androidauto.ytcar.data

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Fila de reprodução em memória, compartilhada entre o app do celular e o Android Auto
 * (os dois rodam no mesmo processo). Thread-safe; os listeners são chamados fora do lock,
 * na thread de quem alterou a fila.
 */
class VideoQueue {

    fun interface Listener {
        fun onQueueChanged()
    }

    private val lock = Any()
    private val items = mutableListOf<Video>()
    private var index = -1
    private val listeners = CopyOnWriteArraySet<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Vídeo que deve estar tocando agora (ou null se a fila está vazia). */
    fun current(): Video? = synchronized(lock) { items.getOrNull(index) }

    fun currentIndex(): Int = synchronized(lock) { index }

    fun snapshot(): List<Video> = synchronized(lock) { items.toList() }

    /**
     * Adiciona um vídeo. Se ele já estiver na fila, não duplica.
     * Vira o atual se [playNow] for true ou se nada estiver tocando.
     */
    fun add(video: Video, playNow: Boolean = false) {
        synchronized(lock) {
            var pos = items.indexOfFirst { it.id == video.id }
            if (pos < 0) {
                items.add(video)
                pos = items.lastIndex
            }
            if (playNow || index < 0) index = pos
        }
        notifyChanged()
    }

    /** Avança para o próximo vídeo. Retorna o novo atual, ou null se já estava no último. */
    fun next(): Video? {
        val result = synchronized(lock) {
            if (index + 1 < items.size) {
                index++
                items[index]
            } else {
                null
            }
        }
        if (result != null) notifyChanged()
        return result
    }

    fun previous(): Video? {
        val result = synchronized(lock) {
            if (index > 0) {
                index--
                items[index]
            } else {
                null
            }
        }
        if (result != null) notifyChanged()
        return result
    }

    fun select(position: Int): Video? {
        val result = synchronized(lock) {
            if (position in items.indices) {
                index = position
                items[position]
            } else {
                null
            }
        }
        if (result != null) notifyChanged()
        return result
    }

    /** Atualiza o título quando o player descobre o nome real do vídeo. */
    fun updateTitle(id: String, title: String) {
        if (title.isBlank()) return
        val changed = synchronized(lock) {
            val pos = items.indexOfFirst { it.id == id }
            if (pos >= 0 && items[pos].title != title) {
                items[pos] = items[pos].copy(title = title)
                true
            } else {
                false
            }
        }
        if (changed) notifyChanged()
    }

    fun clear() {
        synchronized(lock) {
            items.clear()
            index = -1
        }
        notifyChanged()
    }

    private fun notifyChanged() {
        listeners.forEach { it.onQueueChanged() }
    }
}
