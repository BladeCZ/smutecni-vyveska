package cz.pshornicek.vyveska.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.pshornicek.vyveska.data.ObituaryRepository
import java.util.concurrent.TimeUnit

class ObituarySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = ObituaryRepository(applicationContext)
        if (!repository.isNotificationsEnabled()) {
            return Result.success()
        }
        val res = repository.getObituaries(forceRefresh = true)
        return if (res.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "smutecni_vyveska_periodic_sync"

        fun schedulePeriodicSync(context: Context, replaceExisting: Boolean = false) {
            val repository = ObituaryRepository(context.applicationContext)
            val workManager = WorkManager.getInstance(context.applicationContext)

            if (!repository.isNotificationsEnabled()) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ObituarySyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            val policy = if (replaceExisting) {
                ExistingPeriodicWorkPolicy.UPDATE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }
    }
}
