package app.reporove

import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.storage.RetiredFeatureCleanup
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import org.junit.Test
import org.junit.Assert.*

class UpgradeCleanupTest {
    @Test fun removedFeatureErasesLegacyConfigurationCacheAndKeyOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("translation-secrets", 0)
        prefs.edit().putString("configuration", "legacy-encrypted-configuration").commit()
        val cache = File(context.filesDir, "translations").apply { mkdirs() }
        File(cache, "legacy-document.json").writeText("legacy cached text")
        val alias = "reporove.translation.v1"
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        val unrelated = context.getSharedPreferences("cleanup-test-unrelated", 0)
        unrelated.edit().putString("account", "keep").commit()
        try {
            RetiredFeatureCleanup.run(context)
            RetiredFeatureCleanup.run(context)
            assertFalse(cache.exists())
            assertTrue(context.getSharedPreferences("translation-secrets", 0).all.isEmpty())
            assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
            assertEquals("keep", unrelated.getString("account", null))
        } finally { context.deleteSharedPreferences("cleanup-test-unrelated") }
    }
}
