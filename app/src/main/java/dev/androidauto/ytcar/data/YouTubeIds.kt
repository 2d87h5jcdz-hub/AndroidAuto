package dev.androidauto.ytcar.data

/**
 * Extrai o ID de 11 caracteres de um vídeo do YouTube a partir de um link,
 * de um texto compartilhado (ex.: "Título https://youtu.be/ID?si=...") ou do próprio ID.
 * Feito sem android.net.Uri para poder ser testado na JVM.
 */
object YouTubeIds {
    private const val ID_CHARS = "[A-Za-z0-9_-]"
    private val BARE_ID = Regex("^$ID_CHARS{11}$")

    private val PATTERNS = listOf(
        Regex("youtu\\.be/($ID_CHARS{11})(?!$ID_CHARS)"),
        Regex("youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#\\s]*&)?v=|embed/|shorts/|live/|v/)($ID_CHARS{11})(?!$ID_CHARS)"),
    )

    fun isValidId(id: String): Boolean = BARE_ID.matches(id)

    fun extract(input: String?): String? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (isValidId(text)) return text
        for (pattern in PATTERNS) {
            pattern.find(text)?.let { return it.groupValues[1] }
        }
        return null
    }
}
