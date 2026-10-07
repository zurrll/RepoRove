package app.reporove

import android.app.Application
import app.reporove.core.storage.*
import app.reporove.core.network.GitHubApi
import app.reporove.core.network.createApi
import app.reporove.data.*
import java.io.File
import kotlinx.coroutines.launch

class RepoRoveApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application, translationFactory: (SecretStore, File) -> app.reporove.core.translation.TranslationService = { secrets, cache -> app.reporove.core.translation.TranslationService(secrets, cache) }, apiFactory: (String?) -> GitHubApi = { createApi(it) }) {
    val catalogs = app.reporove.core.model.Catalogs(application)
    val local = LocalStore(application)
    val repository = GitHubRepository(Credentials(application), local, ResponseCache(File(application.cacheDir, "github-responses")), apiFactory)
    val offline = app.reporove.core.offline.OfflineStore(File(application.filesDir, "offline-repositories"), repository)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val translation = translationFactory(SecretStore(application), File(application.filesDir, "translations"))
    init { scope.launch { offline.initialize() }; repository.beforeAccountChange = { offline.clearPrivate(); translation.clearPrivate(); local.clearReadings() } }
    val downloads = Downloads(application, local)
    val deviceLogin = DeviceLogin(BuildConfig.GITHUB_OAUTH_CLIENT_ID)
}
