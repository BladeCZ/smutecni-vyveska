package cz.pshornicek.vyveska.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.pshornicek.vyveska.data.AppUpdateManager
import cz.pshornicek.vyveska.data.ObituaryRepository
import java.util.concurrent.TimeUnit

class ObituarySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repository = ObituaryRepository(applicationContext)
            val result = repository.getObituaries(forceRefresh = true)

            // Zároveň zkontrolujeme dostupnost nové verze aplikace na GitHubu
            val updateRes = AppUpdateManager.checkForUpdate(applicationContext)
            updateRes.onSuccess { info ->
                if (info.isUpdateAvailable && AppUpdateManager.shouldNotifyBackgroundUpdate(applicationContext, info.remoteVersionCode)) {
                    repository.sendAppUpdateNotification(info)
                    AppUpdateManager.markBackgroundUpdateNotified(applicationContext, info.remoteVersionCode)
                }
            }

            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "smutecni_vyveska_periodic_sync"
        private const val UNIQUE_IMMEDIATE_WORK_NAME = "smutecni_vyveska_immediate_sync"
        private const val ALARM_REQUEST_CODE = 4242

        fun schedulePeriodicSync(context: Context, replaceExisting: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<ObituarySyncWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            val policy = if (replaceExisting) {
                ExistingPeriodicWorkPolicy.UPDATE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                policy,
                syncRequest
            )
            scheduleAlarmBackup(context)
        }

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<ObituarySyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                oneTimeRequest
            )
        }

        fun scheduleAlarmBackup(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, SyncAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
                    AlarmManager.INTERVAL_HALF_HOUR,
                    pendingIntent
                )
            } catch (_: Exception) {
            }
        }
    }
}
