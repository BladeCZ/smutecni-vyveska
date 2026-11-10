package cz.pshornicek.vyveska.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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

        fun schedulePeriodicSync(context: Context, replaceExisting: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<ObituarySyncWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
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
        }
    }
}
