package app.reporove.data

import java.util.Locale

/** GitHub often serves assets as octet-stream. Recover the handler type from the filename. */
object DownloadFileTypes {
    fun mime(name: String, declared: String?, platform: (String) -> String? = { null }): String {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val known = when (extension) {
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "gz", "tgz" -> "application/gzip"
            "tar" -> "application/x-tar"
            "7z" -> "application/x-7z-compressed"
            "txt", "log", "md", "markdown", "kt", "java", "py", "js", "ts", "css", "yaml", "yml", "sh" -> "text/plain"
            else -> platform(extension)
        }
        return known ?: declared?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            ?.takeIf { it.matches(Regex("[a-z0-9.+-]+/[a-z0-9.+-]+")) && it !in setOf("application/octet-stream", "binary/octet-stream", "application/x-binary") }
            ?: "application/octet-stream"
    }
}
