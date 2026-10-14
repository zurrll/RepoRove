package app.reporove

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in production requests: cover both requests used by the release detail page. */
@RunWith(AndroidJUnit4::class)
class ReleaseApiSmokeTest {
    @Test fun releaseDetailsAndRepositoryUseRealGitHubData() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveApi") == "true")
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RepoRoveApplication
        val repository = application.container.repository
        repository.logout()
        val started = SystemClock.elapsedRealtime()
        println("RELEASE_API stage=list")
        val listed = repository.releases("termux/termux-app", 1, true).data.first()
        println("RELEASE_API stage=detail elapsed_ms=${SystemClock.elapsedRealtime() - started}")
        val detail = repository.release("termux/termux-app", listed.id, true).data
        assertEquals(listed.id, detail.id)
        assertEquals(listed.tag, detail.tag)
        assertTrue(detail.assets.isNotEmpty())
        println("RELEASE_API stage=repository elapsed_ms=${SystemClock.elapsedRealtime() - started}")
        val repo = repository.repository("termux/termux-app", true).data
        assertEquals("termux/termux-app", repo.fullName)
        println("RELEASE_API stage=complete elapsed_ms=${SystemClock.elapsedRealtime() - started}")
    }
}
