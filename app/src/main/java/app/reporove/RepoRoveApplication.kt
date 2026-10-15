package app.reporove

import android.app.Application
import app.reporove.core.storage.*
import app.reporove.core.network.GitHubApi
import app.reporove.core.network.createApi
import app.reporove.data.*
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RepoRoveApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application, val deviceLogin: DeviceLogin = DeviceLogin(BuildConfig.GITHUB_OAUTH_CLIENT_ID), val trending: TrendingRepository = TrendingRepository(File(application.filesDir, "trending")), apiFactory: (String?) -> GitHubApi = { createApi(it) }) {
    val catalogs = app.reporove.core.model.Catalogs(application)
    val local = LocalStore(application)
    val repository = GitHubRepository(Credentials(application), local, ResponseCache(File(application.cacheDir, "github-responses")), refreshTokens = deviceLogin::refresh, apiFactory = apiFactory)
    val offline = app.reporove.core.offline.OfflineStore(File(application.filesDir, "offline-repositories"), repository)
    val trendingDigests = TrendingDigests(trending, local, catalogs)
    val trendingNotifications = app.reporove.core.trending.TrendingNotifications(application, local, trending, trendingDigests)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    init {
        scope.launch { RetiredFeatureCleanup.run(application); offline.initialize() }
        repository.beforeAccountChange = { offline.clearPrivate(); local.clearReadings() }
        scope.launch { local.preferences.map { it.trendingNotifications }.distinctUntilChanged().collect { enabled -> runCatching { trendingNotifications.schedule(enabled) } } }
    }
    val downloads = Downloads(application, local)
}
