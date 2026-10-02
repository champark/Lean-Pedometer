package com.cpkr.leanpedometer

import android.content.Context
import java.time.LocalDate

class StepStore(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    fun updateSteps(
        date: LocalDate,
        observedSteps: Long,
        updatedAtEpochMillis: Long =
            System.currentTimeMillis(),
    ): DailySteps {
        val next =
            maxOf(
                getSteps(date),
                observedSteps.coerceAtLeast(0L),
            )

        prefs.edit()
            .putLong(dayKey(date), next)
            .putLong(
                updatedKey(date),
                updatedAtEpochMillis,
            )
            .putLong(
                KEY_LAST_UPDATED_AT,
                updatedAtEpochMillis,
            )
            .apply()

        pruneOldDays(LocalDate.now())

        return DailySteps(
            date = date,
            steps = next,
            updatedAtEpochMillis =
                updatedAtEpochMillis,
        )
    }

    fun getTodaySteps(): Long =
        getSteps(LocalDate.now())

    fun getSteps(date: LocalDate): Long =
        prefs.getLong(dayKey(date), 0L)

    fun getUpdatedAt(date: LocalDate): Long =
        prefs.getLong(updatedKey(date), 0L)

    fun getDailySteps(
        date: LocalDate,
    ): DailySteps? {
        val updatedAt = getUpdatedAt(date)
        if (updatedAt <= 0L) {
            return null
        }

        return DailySteps(
            date = date,
            steps = getSteps(date),
            updatedAtEpochMillis = updatedAt,
        )
    }

    fun getRecentDays(days: Int): List<DailySteps> {
        val safeDays = days.coerceAtLeast(0)
        val today = LocalDate.now()

        return (0 until safeDays).map { offset ->
            val date =
                today.minusDays(offset.toLong())
            DailySteps(
                date = date,
                steps = getSteps(date),
                updatedAtEpochMillis =
                    getUpdatedAt(date),
            )
        }
    }

    fun lastUpdatedAt(): Long =
        prefs.getLong(
            KEY_LAST_UPDATED_AT,
            0L,
        )

    @Synchronized
    private fun pruneOldDays(today: LocalDate) {
        if (
            prefs.getString(
                KEY_LAST_PRUNE_DATE,
                null,
            ) == today.toString()
        ) {
            return
        }

        val cutoff =
            today.minusDays(RETENTION_DAYS)
        val editor = prefs.edit()

        prefs.all.keys.forEach { key ->
            val prefix =
                when {
                    key.startsWith(DAY_PREFIX) ->
                        DAY_PREFIX

                    key.startsWith(UPDATED_PREFIX) ->
                        UPDATED_PREFIX

                    else ->
                        null
                } ?: return@forEach

            val date =
                runCatching {
                    LocalDate.parse(
                        key.removePrefix(prefix),
                    )
                }.getOrNull()
                    ?: return@forEach

            if (date.isBefore(cutoff)) {
                editor.remove(key)
            }
        }

        editor
            .putString(
                KEY_LAST_PRUNE_DATE,
                today.toString(),
            )
            .apply()
    }

    private fun dayKey(date: LocalDate): String =
        DAY_PREFIX + date

    private fun updatedKey(date: LocalDate): String =
        UPDATED_PREFIX + date

    data class DailySteps(
        val date: LocalDate,
        val steps: Long,
        val updatedAtEpochMillis: Long,
    )

    companion object {
        private const val PREFS_NAME =
            "lean_pedometer_steps"
        private const val KEY_LAST_UPDATED_AT =
            "last_updated_at"
        private const val KEY_LAST_PRUNE_DATE =
            "last_prune_date"
        private const val DAY_PREFIX = "day:"
        private const val UPDATED_PREFIX = "updated:"
        private const val RETENTION_DAYS = 400L
    }
}
