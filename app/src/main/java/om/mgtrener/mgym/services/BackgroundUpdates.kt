package om.mgtrener.mgym.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import om.mgtrener.mgym.BuildConfig
import om.mgtrener.mgym.R

object BackgroundUpdates {
    private const val PREFS = "background_updates"
    private const val ENABLED = "enabled"
    private const val LAST_CHECK = "last_check"
    private const val LAST_NOTICE = "last_notice"
    private const val PERIODIC_JOB = 2301
    private const val ONCE_JOB = 2302
    private const val DAY = 24L * 60 * 60 * 1000
    private const val CHANNEL = "mgym_updates"

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(ENABLED, value) }
        if (value) schedule(context) else {
            val jobs = context.getSystemService(JobScheduler::class.java)
            jobs.cancel(PERIODIC_JOB)
            jobs.cancel(ONCE_JOB)
            context.getSystemService(NotificationManager::class.java).cancel(PERIODIC_JOB)
        }
    }

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context) {
        if (!enabled(context) || !canNotify(context)) return
        val jobs = context.getSystemService(JobScheduler::class.java)
        val component = ComponentName(context, UpdateCheckJob::class.java)
        if (jobs.allPendingJobs.none { it.id == PERIODIC_JOB }) {
            jobs.schedule(JobInfo.Builder(PERIODIC_JOB, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(DAY, 60L * 60 * 1000)
                .setPersisted(true)
                .build())
        }
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_CHECK, 0)
        if (System.currentTimeMillis() - last >= DAY && jobs.allPendingJobs.none { it.id == ONCE_JOB }) {
            jobs.schedule(JobInfo.Builder(ONCE_JOB, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build())
        }
    }

    internal fun checked(context: Context, update: UpdateInfo) {
        if (!enabled(context) || !canNotify(context)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(LAST_CHECK, System.currentTimeMillis())
        }
        val version = update.available ?: return
        val url = update.apkUrl ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_NOTICE, null) == version) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Обновления MGym", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, PERIODIC_JOB, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Доступна MGym $version")
            .setContentText("Нажми, чтобы скачать обновление")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(PERIODIC_JOB, notification)
        prefs.edit { putString(LAST_NOTICE, version) }
    }
}

class UpdateCheckJob : JobService() {
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        worker = Thread {
            val update = runCatching { GitHubUpdateProvider(BuildConfig.VERSION_NAME).check() }.getOrNull()
            if (!Thread.currentThread().isInterrupted && update != null) {
                BackgroundUpdates.checked(applicationContext, update)
            }
            jobFinished(params, false)
        }.also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        worker = null
        return true
    }
}
