# Lean Pedometer

A deliberately small Android pedometer for the Lean app family.

## MVP

- Uses the accountless Recording API on mobile (FitnessLocal / LocalRecordingClient).
- Subscribes to TYPE_STEP_COUNT_DELTA for low-power background step collection.
- Does not keep a custom foreground service or SensorManager listener alive.
- Pulls current daily totals from the Recording API when the app or Lean Diary requests them.
- Periodically snapshots the Recording API's recent data into local storage with WorkManager.
- Retains local daily totals for 400 days.
- Shows today's total and the most recent 7 days.
- Exposes a read-only provider for Lean Diary.
- Does not require Health Connect.

## Lean Diary handoff

Lean Diary can query:

content://com.cpkr.leanpedometer.steps/steps/today

or:

content://com.cpkr.leanpedometer.steps/steps/YYYY-MM-DD

See docs/INTEGRATION.md.

The intended Diary behavior is:

1. Prefer Lean Pedometer when installed.
2. Fall back to the existing Health Connect reader otherwise.

## Android baseline

- minSdk 26
- targetSdk 35
- compileSdk 35
- Kotlin 2.2.10
- AGP 8.9.0
- Gradle 8.11.1
- JVM 17
- Google Play services Fitness 21.3.0
- WorkManager 2.12.0

## Recording behavior

The first successful subscription starts local Recording API collection. The subscription remains active while the app is not running and across system restarts.

The Recording API keeps up to 10 days of source data. Lean Pedometer therefore periodically copies recent daily totals into its own small local history.

No Google account or Google Fit OAuth flow is required.

## Build

./gradlew :app:assembleDebug

GitHub Actions builds pull requests and pushes to main, and uploads the debug APK as a workflow artifact.