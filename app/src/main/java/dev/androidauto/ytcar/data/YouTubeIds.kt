package dev.androidauto.ytcar.data

/**
 * Extrai o ID de 11 caracteres de um vídeo do YouTube a partir de um link,
 * de um texto compartilhado (ex.: "Título https://youtu.be/ID?si=...") ou do próprio ID.
 * Feito sem android.net.Uri para poder ser testado na JVM.
 */
object YouTubeIds {
    private const val ID_CHARS = "[A-Za-z0-9_-]"
    private val BARE_ID = Regex("^$ID_CHARS{11}$")

    // (?<![A-Za-z0-9-]) evita aceitar domínios que só terminam com o nome
    // (ex.: "notyoutube.com"); subdomínios como www./m./music. continuam valendo.
    // O host é case-insensitive (o teclado do carro costuma digitar "Youtu.be"),
    // mas o ID não: ele diferencia maiúsculas de minúsculas.
    private const val HOST_START = "(?<![A-Za-z0-9-])"
    private val PATTERNS = listOf(
        Regex("$HOST_START(?i:youtu\\.be)/($ID_CHARS{11})(?!$ID_CHARS)"),
        Regex(
            "$HOST_START(?i:youtube(?:-nocookie)?\\.com)/" +
                "(?:watch\\?(?:[^#\\s]*&)?v=|embed/|shorts/|live/|v/)($ID_CHARS{11})(?!$ID_CHARS)",
        ),
    )

    fun isValidId(id: String): Boolean = BARE_ID.matches(id)

    /** Aceita link, texto com link ou o ID puro. */
    fun extract(input: String?): String? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (isValidId(text)) return text
        return extractFromLink(text)
    }

    /**
     * Só aceita links. Útil na busca, onde uma palavra de 11 letras
     * (ex.: "programming") não deve ser confundida com um ID.
     */
    fun extractFromLink(input: String?): String? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null
        for (pattern in PATTERNS) {
            pattern.find(text)?.let { return it.groupValues[1] }
        }
        return null
    }
}
