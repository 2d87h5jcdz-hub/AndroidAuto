package dev.androidauto.ytcar.phone

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import dev.androidauto.ytcar.R
import dev.androidauto.ytcar.data.AppGraph
import dev.androidauto.ytcar.data.Video
import dev.androidauto.ytcar.data.VideoQueue
import dev.androidauto.ytcar.data.YouTubeIds

/**
 * App do lado do celular: cola um link ou recebe "Compartilhar" do app do YouTube
 * e joga o vídeo na fila que o Android Auto toca.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var queueView: TextView
    private val queue = AppGraph.queue
    private val listener = VideoQueue.Listener { runOnUiThread { renderQueue() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.input)
        status = findViewById(R.id.status)
        queueView = findViewById(R.id.queue)

        findViewById<Button>(R.id.add).setOnClickListener {
            if (addFromText(input.text?.toString())) input.text?.clear()
        }
        findViewById<Button>(R.id.clear).setOnClickListener {
            queue.clear()
            status.text = "Fila limpa."
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        queue.addListener(listener)
        renderQueue()
    }

    override fun onStop() {
        queue.removeListener(listener)
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            addFromText(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    private fun addFromText(text: String?): Boolean {
        val id = YouTubeIds.extract(text)
        if (id == null) {
            status.text = "Não achei um link do YouTube nesse texto."
            return false
        }
        queue.add(Video(id = id, title = "Vídeo $id"))
        status.text = "Adicionado: $id. Abra o YT Car no Android Auto."
        return true
    }

    private fun renderQueue() {
        val items = queue.snapshot()
        val current = queue.currentIndex()
        queueView.text = if (items.isEmpty()) {
            "Fila vazia."
        } else {
            items.mapIndexed { i, v -> (if (i == current) "▶ " else "  ") + v.title }
                .joinToString("\n")
        }
    }
}
