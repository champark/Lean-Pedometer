# Architecture

## Goal

Keep the pedometer independent from Lean Diary while giving Lean Diary a stable, tiny read API.

## Flow

1. Lean Pedometer receives the Android ACTIVITY_RECOGNITION runtime permission.
2. LocalRecordingClient.subscribe(TYPE_STEP_COUNT_DELTA) asks Google Play services to perform low-power background collection.
3. The subscription persists while Lean Pedometer is not running and across system restarts.
4. RecordingStepsRepository reads aggregated daily totals with LocalDataReadRequest.
5. StepStore snapshots those totals locally for long-term retention.
6. StepSyncWorker refreshes the recent 10-day Recording API window every 12 hours.
7. StepProvider refreshes a requested recent date before returning it to Lean Diary.
8. Lean Diary reads the provider first and retains Health Connect as a fallback.

## Persistence

The Recording API keeps up to 10 days of source data while the subscription is active.

Lean Pedometer stores one daily count and update timestamp per day in SharedPreferences, retained for 400 days. This dataset is intentionally tiny, so a database is unnecessary for the MVP.

No migration layer is included. The project is pre-release and the stored shape can be replaced if the design changes.

## Background execution

There is no custom foreground tracking service and no boot receiver.

Google Play services owns the persistent Recording API subscription. WorkManager only wakes Lean Pedometer periodically to copy recent aggregate totals into its longer local history.

## Availability

The app checks Google Play services against LOCAL_RECORDING_CLIENT_STEPS_MIN_VERSION_CODE. If the installed Play services version is too old, Recording API collection is unavailable until Play services is updated.

The Recording API requires ACTIVITY_RECOGNITION on Android 10 and later.