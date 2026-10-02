package com.cpkr.leanpedometer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import java.time.LocalDate

class StepProvider : ContentProvider() {
    private lateinit var stepStore: StepStore
    private lateinit var recordingRepository:
        RecordingStepsRepository

    override fun onCreate(): Boolean {
        val appContext =
            context?.applicationContext
                ?: return false

        stepStore =
            StepStore(appContext)
        recordingRepository =
            RecordingStepsRepository(
                appContext,
            )
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        enforceAllowedCaller()

        if (URI_MATCHER.match(uri) != MATCH_STEPS) {
            throw IllegalArgumentException(
                "Unsupported URI: $uri",
            )
        }

        val segment =
            uri.lastPathSegment
                ?: throw IllegalArgumentException(
                    "Missing date",
                )

        val date =
            when (segment) {
                TODAY ->
                    LocalDate.now()

                else ->
                    runCatching {
                        LocalDate.parse(segment)
                    }.getOrElse {
                        throw IllegalArgumentException(
                            "Invalid date: $segment",
                        )
                    }
            }

        runCatching {
            recordingRepository
                .syncDateBlocking(date)
        }

        return MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf(
                    date.toString(),
                    stepStore.getSteps(date),
                    stepStore.getUpdatedAt(date),
                    SOURCE,
                ),
            )
        }
    }

    override fun getType(uri: Uri): String {
        enforceAllowedCaller()
        return MIME_TYPE
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? =
        throw UnsupportedOperationException(
            "Lean Pedometer provider is read-only",
        )

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int =
        throw UnsupportedOperationException(
            "Lean Pedometer provider is read-only",
        )

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int =
        throw UnsupportedOperationException(
            "Lean Pedometer provider is read-only",
        )

    private fun enforceAllowedCaller() {
        val appContext =
            context
                ?: throw SecurityException(
                    "Provider context unavailable",
                )

        val callingUid =
            Binder.getCallingUid()
        if (callingUid == Process.myUid()) {
            return
        }

        val packages =
            appContext.packageManager
                .getPackagesForUid(callingUid)
                .orEmpty()

        if (
            packages.none {
                it in ALLOWED_CALLER_PACKAGES
            }
        ) {
            throw SecurityException(
                "Caller is not allowed to read step data",
            )
        }
    }

    companion object {
        const val AUTHORITY =
            "com.cpkr.leanpedometer.steps"
        const val COLUMN_DATE = "date"
        const val COLUMN_STEPS = "steps"
        const val COLUMN_UPDATED_AT =
            "updated_at_epoch_ms"
        const val COLUMN_SOURCE = "source"

        private const val MATCH_STEPS = 1
        private const val TODAY = "today"
        private const val SOURCE =
            "lean_pedometer"
        private const val MIME_TYPE =
            "vnd.android.cursor.item/vnd.com.cpkr.leanpedometer.steps"

        private val COLUMNS =
            arrayOf(
                COLUMN_DATE,
                COLUMN_STEPS,
                COLUMN_UPDATED_AT,
                COLUMN_SOURCE,
            )

        private val ALLOWED_CALLER_PACKAGES =
            setOf(
                "com.cpkr.lwdiary",
            )

        private val URI_MATCHER =
            UriMatcher(
                UriMatcher.NO_MATCH,
            ).apply {
                addURI(
                    AUTHORITY,
                    "steps/*",
                    MATCH_STEPS,
                )
            }
    }
}
