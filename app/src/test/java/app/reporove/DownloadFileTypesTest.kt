package app.reporove

import app.reporove.data.DownloadFileTypes
import org.junit.Assert.*
import org.junit.Test

class DownloadFileTypesTest {
    @Test fun genericOrWrongServerMimeStillOpensKnownReleaseFileTypes() {
        assertEquals("application/vnd.android.package-archive", DownloadFileTypes.mime("Reader.APK", "application/octet-stream"))
        assertEquals("application/vnd.android.package-archive", DownloadFileTypes.mime("reader.apk", "application/zip"))
        assertEquals("application/pdf", DownloadFileTypes.mime("manual.pdf", "binary/octet-stream"))
        assertEquals("application/zip", DownloadFileTypes.mime("source.zip", "application/octet-stream"))
        assertEquals("text/plain", DownloadFileTypes.mime("README.md", null))
    }
    @Test fun compoundArchivesAndPlatformTypesAreRecognizedWithoutGuessingUnknownFiles() {
        assertEquals("application/gzip", DownloadFileTypes.mime("project.tar.gz", null))
        assertEquals("image/png", DownloadFileTypes.mime("logo.png", "application/octet-stream") { if (it == "png") "image/png" else null })
        assertEquals("application/json", DownloadFileTypes.mime("data.unknown", "Application/JSON; charset=utf-8"))
        assertEquals("application/octet-stream", DownloadFileTypes.mime("unknown", "text/plain\ninvalid"))
        assertEquals("application/octet-stream", DownloadFileTypes.mime("asset.bin", null))
    }
}
