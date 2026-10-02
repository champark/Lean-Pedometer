package com.cpkr.leanpedometer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class StepSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParams,
) {
    override suspend fun doWork(): Result {
        if (
            !hasActivityRecognitionPermission(
                applicationContext,
            )
        ) {
            return Result.success()
        }

        val repository =
            RecordingStepsRepository(
                applicationContext,
            )

        if (!repository.isPlayServicesReady()) {
            return Result.success()
        }

        return if (
            repository.syncRecent(
                RecordingStepsRepository
                    .DEFAULT_SYNC_DAYS,
            )
        ) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME =
            "lean_pedometer_step_sync"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<
                    StepSyncWorker
                >(
                    12,
                    TimeUnit.HOURS,
                )
                    .build()

            WorkManager
                .getInstance(
                    context.applicationContext,
                )
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
        }
    }
}
