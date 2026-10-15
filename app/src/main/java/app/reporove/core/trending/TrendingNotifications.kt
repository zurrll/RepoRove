package app.reporove.core.trending

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.reporove.MainActivity
import app.reporove.RepoRoveApplication
import app.reporove.R
import app.reporove.core.model.*
import app.reporove.core.storage.LocalStore
import app.reporove.data.TrendingDigests
import app.reporove.data.TrendingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

enum class DigestDelivery(val message: String) {
    Posted("已发送今日精选提醒"), AlreadySent("今天已经提醒过了"), Disabled("请先开启每日发现通知"), NoPermission("请在系统设置中允许每日发现通知"), NoNewItems("暂时没有新的精选项目"), Unavailable("热榜暂时无法更新，本次未发送提醒")
}

class TrendingNotifications(private val context: Context, private val local: LocalStore, private val trending: TrendingRepository, private val digests: TrendingDigests) {
    @Volatile private var stoppedByUser = false
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun stopImmediately() { stoppedByUser = true; schedule(false) }
    suspend fun setEnabled(enabled: Boolean) {
        if (!enabled) stopImmediately()
        local.updatePreferences { it.copy(trendingNotifications = enabled) }
        if (enabled) stoppedByUser = false
        try { schedule(enabled) } catch (error: Exception) {
            if (enabled) { stoppedByUser = true; local.updatePreferences { it.copy(trendingNotifications = false) }; schedule(false) }
            throw error
        }
    }

    fun schedule(enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!enabled) { scheduler.cancel(JOB_ID); manager.cancel(NOTIFICATION_ID); return }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "每日发现", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "快速增长仓库的每日精选，最多每天一次" })
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, TrendingJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(TimeUnit.HOURS.toMillis(6), TimeUnit.HOURS.toMillis(1))
            .setBackoffCriteria(TimeUnit.MINUTES.toMillis(30), JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setPersisted(true).build()
        require(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) { "系统暂时无法安排每日发现任务，请重新开启。" }
    }

    suspend fun check(): DigestDelivery {
        if (!local.preferences.first().trendingNotifications) return DigestDelivery.Disabled
        if (!allowed()) return DigestDelivery.NoPermission
        val day = LocalDate.now().toString()
        if (trending.history().lastNotificationDay == day) return DigestDelivery.AlreadySent
        val result = digests.preview(refresh = true)
        val digest = result.data
        if (result.offline || System.currentTimeMillis() - digest.fetchedAt !in 0..TrendingRules.MAX_NOTIFY_AGE || digest.day != day) return DigestDelivery.Unavailable
        if (digest.picks.isEmpty()) return DigestDelivery.NoNewItems
        val posted = trending.notifyOnce(digest, { !stoppedByUser && local.preferences.first().trendingNotifications && allowed() }) {
            if (stoppedByUser || !allowed()) false else {
                val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_DAY, day).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pending = PendingIntent.getActivity(context, 62, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val titles = digest.picks.take(3).joinToString("、") { it.entry.fullName.substringAfter('/') }
                val notification = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification_trending)
                    .setContentTitle("今日发现 · ${digest.picks.size} 个快速增长项目")
                    .setContentText(titles).setStyle(NotificationCompat.BigTextStyle().bigText(titles))
                    .setContentIntent(pending).setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION).build()
                manager.notify(NOTIFICATION_ID, notification)
                true
            }
        }
        return if (posted) DigestDelivery.Posted else DigestDelivery.AlreadySent
    }

    companion object {
        const val JOB_ID = 6201
        const val NOTIFICATION_ID = 6202
        const val CHANNEL = "trending-digest"
        const val EXTRA_DAY = "app.reporove.TRENDING_DIGEST_DAY"
    }
}

class TrendingJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = scope.launch {
            val retry = try {
                val container = (application as RepoRoveApplication).container
                val hour = java.time.LocalTime.now().hour
                if (hour in 9..21) container.trendingNotifications.check()
                false
            } catch (error: CancellationException) { throw error } catch (_: Exception) { true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); task = null; return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
