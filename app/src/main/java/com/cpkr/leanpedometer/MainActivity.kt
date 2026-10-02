package com.cpkr.leanpedometer

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        StepSyncWorker.schedule(
            applicationContext,
        )

        setContent {
            val colors =
                if (isSystemInDarkTheme()) {
                    darkColorScheme()
                } else {
                    lightColorScheme()
                }

            MaterialTheme(
                colorScheme = colors,
            ) {
                LeanPedometerScreen()
            }
        }
    }
}

@Composable
private fun LeanPedometerScreen() {
    val context =
        LocalContext.current
    val appContext =
        context.applicationContext
    val store =
        remember(appContext) {
            StepStore(appContext)
        }
    val repository =
        remember(appContext) {
            RecordingStepsRepository(
                appContext,
            )
        }
    val coroutineScope =
        rememberCoroutineScope()

    var permissionRefresh by remember {
        mutableLongStateOf(0L)
    }
    var syncState by remember {
        mutableStateOf(
            SyncState.IDLE,
        )
    }
    var steps by remember {
        mutableLongStateOf(
            store.getTodaySteps(),
        )
    }
    var recentDays by remember {
        mutableStateOf(
            store.getRecentDays(7),
        )
    }

    val playServicesReady =
        remember {
            repository
                .isPlayServicesReady()
        }

    val recognitionGranted =
        remember(permissionRefresh) {
            hasActivityRecognitionPermission(
                appContext,
            )
        }

    fun reloadFromStore() {
        steps =
            store.getTodaySteps()
        recentDays =
            store.getRecentDays(7)
    }

    fun refreshNow() {
        if (
            !recognitionGranted ||
            !playServicesReady
        ) {
            return
        }

        coroutineScope.launch {
            syncState =
                SyncState.SYNCING

            syncState =
                if (
                    repository.syncRecent(7)
                ) {
                    reloadFromStore()
                    SyncState.ACTIVE
                } else {
                    SyncState.ERROR
                }
        }
    }

    val activityPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .RequestPermission(),
        ) { granted ->
            permissionRefresh++
            if (granted) {
                StepSyncWorker.schedule(
                    appContext,
                )
            }
        }

    LaunchedEffect(
        recognitionGranted,
        playServicesReady,
    ) {
        if (
            !recognitionGranted ||
            !playServicesReady
        ) {
            return@LaunchedEffect
        }

        while (true) {
            syncState =
                SyncState.SYNCING

            syncState =
                if (
                    repository.syncRecent(7)
                ) {
                    reloadFromStore()
                    SyncState.ACTIVE
                } else {
                    SyncState.ERROR
                }

            delay(5_000L)
        }
    }

    val statusText =
        when {
            !playServicesReady ->
                "Google Play 서비스 업데이트가 필요합니다."

            !recognitionGranted ->
                "활동 인식 권한이 필요합니다."

            syncState == SyncState.SYNCING ->
                "걸음 수 동기화 중…"

            syncState == SyncState.ERROR ->
                "Recording API 데이터를 읽지 못했습니다."

            else ->
                "Recording API 기록 활성화"
        }

    Surface(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            verticalArrangement =
                Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Lean Pedometer",
                style =
                    MaterialTheme.typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold,
            )

            Text(
                text = statusText,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            20.dp,
                        ),
                ) {
                    Text(
                        text = "오늘",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                    )
                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp,
                            ),
                    )
                    Text(
                        text =
                            NumberFormat
                                .getNumberInstance(
                                    Locale
                                        .getDefault(),
                                )
                                .format(steps),
                        style =
                            MaterialTheme
                                .typography
                                .displayMedium,
                        fontWeight =
                            FontWeight.Bold,
                    )
                    Text(
                        text = "걸음",
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                    )
                }
            }

            Button(
                onClick = {
                    if (!recognitionGranted) {
                        activityPermissionLauncher
                            .launch(
                                Manifest.permission
                                    .ACTIVITY_RECOGNITION,
                            )
                    } else {
                        refreshNow()
                    }
                },
                enabled =
                    playServicesReady,
            ) {
                Text(
                    if (recognitionGranted) {
                        "지금 새로고침"
                    } else {
                        "기록 시작"
                    },
                )
            }

            Text(
                text = "최근 7일",
                style =
                    MaterialTheme.typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold,
            )

            recentDays.forEach { item ->
                HistoryRow(
                    date = item.date,
                    steps = item.steps,
                )
            }

            Spacer(
                modifier =
                    Modifier.weight(1f),
            )

            Text(
                text =
                    "백그라운드 수집은 Google Play 서비스의 모바일 Recording API가 담당합니다. Lean Pedometer는 포그라운드 서비스를 계속 실행하지 않습니다.",
                style =
                    MaterialTheme.typography
                        .bodySmall,
            )

            Text(
                text =
                    "Recording API 원천 데이터는 최대 10일 보관되며, Lean Pedometer가 주기적으로 날짜별 값을 자체 기록에 보존합니다.",
                style =
                    MaterialTheme.typography
                        .bodySmall,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    date: LocalDate,
    steps: Long,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        Text(
            text =
                date.format(
                    DateTimeFormatter
                        .ofPattern("MM.dd"),
                ),
        )
        Text(
            text =
                NumberFormat
                    .getNumberInstance(
                        Locale.getDefault(),
                    )
                    .format(steps),
            fontWeight =
                FontWeight.Medium,
        )
    }
}

private enum class SyncState {
    IDLE,
    SYNCING,
    ACTIVE,
    ERROR,
}
