package app.reporove

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.reporove.ui.RepoRoveApp

class MainActivity : ComponentActivity() {
    private val digestDay = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        digestDay.value = savedInstanceState?.getString(app.reporove.core.trending.TrendingNotifications.EXTRA_DAY)
        if (digestDay.value == null) readDigestIntent(intent)
        setContent { RepoRoveApp((application as RepoRoveApplication).container, window, digestDay.value) { digestDay.value = null } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readDigestIntent(intent) }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString(app.reporove.core.trending.TrendingNotifications.EXTRA_DAY, digestDay.value); super.onSaveInstanceState(outState) }
    private fun readDigestIntent(intent: Intent?) {
        val day = intent?.getStringExtra(app.reporove.core.trending.TrendingNotifications.EXTRA_DAY)
        if (day != null && app.reporove.data.TrendingRepository.validDay(day)) digestDay.value = day
        intent?.removeExtra(app.reporove.core.trending.TrendingNotifications.EXTRA_DAY)
    }
}
