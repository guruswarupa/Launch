package com.guruswarupa.launch.ui.document

import java.io.File

class ConvertedDocument(
    val html: String,
    val kind: Kind,
    val alternateHtml: String? = null,
    val alternateLabel: String? = null
) {
    enum class Kind { TEXT, CODE, WORD, SHEET, SLIDES, WEB }
}

class UnsupportedLegacyFormatException(message: String) : Exception(message)

object DocumentConverter {

    private val WORD = setOf("docx", "docm", "dotx", "dotm")
    private val SHEET = setOf("xlsx", "xlsm", "xltx", "xltm")
    private val SLIDES = setOf("pptx", "pptm", "potx", "potm", "ppsx", "ppsm")
    private val ODF = setOf("odt", "ods", "odp", "ott", "ots", "otp", "fodt")
    private val LEGACY = setOf("doc", "xls", "ppt", "wps", "pages", "numbers", "key")
    private val DELIMITED = setOf("csv", "tsv", "tab")
    private val MARKDOWN = setOf("md", "markdown", "mdown")
    private val HTML = setOf("html", "htm", "xhtml")
    private val CODE = setOf(
        "json", "geojson", "xml", "svg", "css", "scss", "less", "js", "mjs", "jsx", "ts", "tsx", "vue", "kt", "kts", "java", "py", "c", "cpp", "cc", "h", "hpp",
        "cs", "php", "go", "rs", "swift", "dart", "rb", "sh", "bash", "zsh", "bat", "ps1", "sql", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
        "gradle", "lua", "r", "pl", "scala", "groovy", "tex", "diff", "patch", "gitignore", "env", "lock", "plist", "xhtml", "rss", "atom", "xsd", "xsl", "xslt", "wsdl", "csproj", "iml", "proto", "graphql", "tf", "makefile", "dockerfile", "ipynb"
    )
    private val PLAIN = setOf("txt", "text", "log", "nfo", "me", "readme", "srt", "vtt", "lrc", "ass", "asc", "list", "out", "err")

    val SUPPORTED: Set<String> = WORD + SHEET + SLIDES + ODF + DELIMITED + MARKDOWN + HTML + CODE + PLAIN + setOf("rtf", "pdf")

    fun isSupported(extension: String): Boolean = true

    fun isLegacyOffice(extension: String): Boolean = extension.lowercase() in LEGACY

    fun convert(file: File, fileName: String): ConvertedDocument {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val title = fileName
        return when {
            extension in WORD -> ConvertedDocument(DocxConverter.convert(file, title), ConvertedDocument.Kind.WORD)
            extension in SHEET -> ConvertedDocument(XlsxConverter.convert(file, title), ConvertedDocument.Kind.SHEET)
            extension in SLIDES -> ConvertedDocument(PptxConverter.convert(file, title), ConvertedDocument.Kind.SLIDES)
            extension in ODF -> {
                val html = OdfConverter.convert(file, title)
                val kind = when (extension) {
                    "ods", "ots" -> ConvertedDocument.Kind.SHEET
                    "odp", "otp" -> ConvertedDocument.Kind.SLIDES
                    else -> ConvertedDocument.Kind.WORD
                }
                ConvertedDocument(html, kind)
            }
            extension == "rtf" -> ConvertedDocument(RtfConverter.convert(file, title), ConvertedDocument.Kind.WORD)
            extension in DELIMITED -> ConvertedDocument(
                TextConverters.csv(file, title, if (extension == "tsv" || extension == "tab") '\t' else null),
                ConvertedDocument.Kind.SHEET
            )
            extension in MARKDOWN -> ConvertedDocument(
                TextConverters.markdown(file, title),
                ConvertedDocument.Kind.TEXT,
                TextConverters.code(file, "md", title),
                "Source"
            )
            extension in HTML -> ConvertedDocument(
                TextConverters.htmlRendered(file, title),
                ConvertedDocument.Kind.WEB,
                TextConverters.htmlSource(file, title),
                "Source"
            )
            extension in CODE -> ConvertedDocument(TextConverters.code(file, extension, title), ConvertedDocument.Kind.CODE)
            extension in LEGACY -> ConvertedDocument(BinaryConverters.extractLegacyText(file, title), ConvertedDocument.Kind.TEXT)
            extension in BinaryConverters.IMAGE_EXTENSIONS -> {
                val html = BinaryConverters.image(file, title)
                if (extension == "svg") {
                    ConvertedDocument(html, ConvertedDocument.Kind.WEB, TextConverters.code(file, "svg", title), "Source")
                } else {
                    ConvertedDocument(html, ConvertedDocument.Kind.WEB)
                }
            }
            extension in BinaryConverters.ARCHIVE_EXTENSIONS -> {
                val html = BinaryConverters.archive(file, title)
                ConvertedDocument(html ?: BinaryConverters.hex(file, title), ConvertedDocument.Kind.WEB)
            }
            extension in BinaryConverters.MEDIA_EXTENSIONS ->
                ConvertedDocument(
                    BinaryConverters.info(file, title, "Media file", "Audio and video play best in a media app. Use Open with to play this file."),
                    ConvertedDocument.Kind.WEB
                )
            extension in PLAIN || extension.isEmpty() || !TextConverters.looksBinary(file) ->
                ConvertedDocument(TextConverters.plain(file, title), ConvertedDocument.Kind.TEXT)
            else -> {
                val archive = if (extension != "pdf") BinaryConverters.archive(file, title) else null
                if (archive != null) ConvertedDocument(archive, ConvertedDocument.Kind.WEB)
                else ConvertedDocument(BinaryConverters.hex(file, title), ConvertedDocument.Kind.WEB)
            }
        }
    }
}
