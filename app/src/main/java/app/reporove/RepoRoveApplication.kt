package app.reporove

import android.app.Application
import app.reporove.core.storage.*
import app.reporove.core.network.GitHubApi
import app.reporove.core.network.createApi
import app.reporove.data.*
import java.io.File

class RepoRoveApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application, apiFactory: (String?) -> GitHubApi = { createApi(it) }) {
    val catalogs = app.reporove.core.model.Catalogs(application)
    val local = LocalStore(application)
    val repository = GitHubRepository(Credentials(application), local, ResponseCache(File(application.cacheDir, "github-responses")), apiFactory)
    val downloads = Downloads(application, local)
    val deviceLogin = DeviceLogin(BuildConfig.GITHUB_OAUTH_CLIENT_ID)
}
