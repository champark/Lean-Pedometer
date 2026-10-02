package com.cpkr.leanpedometer

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.LocalRecordingClient
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.data.LocalField
import com.google.android.gms.fitness.request.LocalDataReadRequest
import com.google.android.gms.tasks.Tasks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordingStepsRepository(
    context: Context,
) {
    private val appContext =
        context.applicationContext
    private val stepStore =
        StepStore(appContext)
    private val localRecordingClient by lazy {
        FitnessLocal.getLocalRecordingClient(
            appContext,
        )
    }

    fun isPlayServicesReady(): Boolean =
        GoogleApiAvailability
            .getInstance()
            .isGooglePlayServicesAvailable(
                appContext,
                LocalRecordingClient
                    .LOCAL_RECORDING_CLIENT_STEPS_MIN_VERSION_CODE,
            ) == ConnectionResult.SUCCESS

    suspend fun syncRecent(
        days: Int = DEFAULT_SYNC_DAYS,
    ): Boolean =
        withContext(Dispatchers.IO) {
            syncRecentBlocking(days)
        }

    fun syncRecentBlocking(
        days: Int = DEFAULT_SYNC_DAYS,
    ): Boolean {
        if (!canUseRecordingApi()) {
            return false
        }

        return runCatching {
            ensureSubscriptionBlocking()

            val today = LocalDate.now()
            val safeDays =
                days.coerceIn(
                    1,
                    MAX_RECORDING_DAYS,
                )
            val startDate =
                today.minusDays(
                    (safeDays - 1).toLong(),
                )
            val end =
                ZonedDateTime.now()

            val totals =
                readDailyTotalsBlocking(
                    startDate = startDate,
                    end = end,
                )
            val updatedAt =
                System.currentTimeMillis()

            totals.forEach {
                (date, steps) ->
                stepStore.updateSteps(
                    date = date,
                    observedSteps = steps,
                    updatedAtEpochMillis =
                        updatedAt,
                )
            }

            if (today !in totals) {
                stepStore.updateSteps(
                    date = today,
                    observedSteps = 0L,
                    updatedAtEpochMillis =
                        updatedAt,
                )
            }

            true
        }.getOrDefault(false)
    }

    fun syncDateBlocking(
        date: LocalDate,
    ): StepStore.DailySteps? {
        val cached =
            stepStore.getDailySteps(date)

        if (!canUseRecordingApi()) {
            return cached
        }

        val today = LocalDate.now()
        if (
            date.isAfter(today) ||
            date.isBefore(
                today.minusDays(
                    (MAX_RECORDING_DAYS - 1)
                        .toLong(),
                ),
            )
        ) {
            return cached
        }

        return runCatching {
            ensureSubscriptionBlocking()

            val zone =
                ZoneId.systemDefault()
            val end =
                if (date == today) {
                    ZonedDateTime.now(zone)
                } else {
                    date.plusDays(1)
                        .atStartOfDay(zone)
                }

            val totals =
                readDailyTotalsBlocking(
                    startDate = date,
                    end = end,
                )
            val total =
                totals[date]

            if (
                total != null ||
                date == today
            ) {
                stepStore.updateSteps(
                    date = date,
                    observedSteps =
                        total ?: 0L,
                )
            } else {
                cached
            }
        }.getOrElse {
            cached
        }
    }

    private fun canUseRecordingApi(): Boolean =
        hasActivityRecognitionPermission(
            appContext,
        ) &&
            isPlayServicesReady()

    private fun ensureSubscriptionBlocking() {
        Tasks.await(
            localRecordingClient.subscribe(
                LocalDataType
                    .TYPE_STEP_COUNT_DELTA,
            ),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun readDailyTotalsBlocking(
        startDate: LocalDate,
        end: ZonedDateTime,
    ): Map<LocalDate, Long> {
        val zone =
            ZoneId.systemDefault()
        val start =
            startDate.atStartOfDay(zone)

        if (!end.isAfter(start)) {
            return emptyMap()
        }

        val request =
            LocalDataReadRequest.Builder()
                .aggregate(
                    LocalDataType
                        .TYPE_STEP_COUNT_DELTA,
                )
                .bucketByTime(
                    1,
                    TimeUnit.DAYS,
                )
                .setTimeRange(
                    start.toEpochSecond(),
                    end.toEpochSecond(),
                    TimeUnit.SECONDS,
                )
                .build()

        val response =
            Tasks.await(
                localRecordingClient
                    .readData(request),
                TASK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )

        val totals =
            linkedMapOf<LocalDate, Long>()

        response.buckets.forEach { bucket ->
            val date =
                Instant.ofEpochSecond(
                    bucket.getStartTime(
                        TimeUnit.SECONDS,
                    ),
                )
                    .atZone(zone)
                    .toLocalDate()

            val total =
                bucket.dataSets
                    .flatMap {
                        it.dataPoints
                    }
                    .sumOf { point ->
                        point.getValue(
                            LocalField.FIELD_STEPS,
                        )
                            .asInt()
                            .toLong()
                    }

            totals[date] =
                (totals[date] ?: 0L) +
                    total
        }

        return totals
    }

    companion object {
        const val MAX_RECORDING_DAYS = 10
        const val DEFAULT_SYNC_DAYS = 10

        private const val TASK_TIMEOUT_SECONDS =
            10L
    }
}
