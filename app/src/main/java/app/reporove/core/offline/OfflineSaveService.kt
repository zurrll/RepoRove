package app.reporove.core.offline

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.reporove.MainActivity
import app.reporove.RepoRoveApplication
import app.reporove.core.network.AppJson
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString

/** User-initiated, cancellable transfer. Killed processes leave an explicit retry state, never a false success. */
class OfflineSaveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()
    private val store get() = (application as RepoRoveApplication).container.offline
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "离线仓库保存", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            intent.getStringExtra("repository")?.let { jobs[it]?.cancel() }
            if (jobs.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        val request = runCatching { AppJson.decodeFromString<SaveRequest>(intent?.getStringExtra("request").orEmpty()) }.getOrNull() ?: run { stopSelf(startId); return START_NOT_STICKY }
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, request.fullName.hashCode(), Intent(this, OfflineSaveService::class.java).setAction(CANCEL).putExtra("repository", request.fullName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("保存离线仓库").setContentText(text).setContentIntent(pending).setOngoing(true).addAction(0, "取消", cancel).build()
        startForeground(NOTIFICATION, notification(request.fullName), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (jobs[request.fullName]?.isActive == true) return START_NOT_STICKY
        jobs[request.fullName] = scope.launch {
            val progress = launch { store.progress.collect { states ->
                states[request.fullName]?.let { startForeground(NOTIFICATION, notification("${request.fullName} · ${it.stage}"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
            } }
            try { store.ready.await(); store.save(request) }
            finally {
                progress.cancel(); jobs.remove(request.fullName)
                if (jobs.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { jobs.values.forEach { it.cancel() }; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        private const val CHANNEL = "offline-save"
        private const val NOTIFICATION = 705
        private const val CANCEL = "app.reporove.CANCEL_OFFLINE"
        fun start(context: Context, request: SaveRequest) { ContextCompat.startForegroundService(context, Intent(context, OfflineSaveService::class.java).putExtra("request", AppJson.encodeToString(request))) }
        fun cancel(context: Context, fullName: String) { context.startService(Intent(context, OfflineSaveService::class.java).setAction(CANCEL).putExtra("repository", fullName)) }
    }
}
