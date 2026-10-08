package app.reporove.core.storage

import android.content.Context
import java.io.File
import java.security.KeyStore

/** Upgrade cleanup for a retired 0.5 feature; unrelated login credentials stay intact. */
object RetiredFeatureCleanup {
    fun run(context: Context) {
        context.getSharedPreferences("translation-secrets", Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteSharedPreferences("translation-secrets")
        File(context.filesDir, "translations").deleteRecursively()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("reporove.translation.v1") } }
    }
}
