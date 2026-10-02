# Lean Diary integration

Lean Pedometer exposes a read-only Android ContentProvider so Lean Diary can read step totals without Health Connect.

## Contract

- Authority: com.cpkr.leanpedometer.steps
- URI for today: content://com.cpkr.leanpedometer.steps/steps/today
- URI for a date: content://com.cpkr.leanpedometer.steps/steps/YYYY-MM-DD

Returned columns:

| Column | Type | Meaning |
| --- | --- | --- |
| date | TEXT | ISO-8601 local date |
| steps | INTEGER | Recorded step total for that date |
| updated_at_epoch_ms | INTEGER | Last successful local snapshot time for the date |
| source | TEXT | lean_pedometer |

The provider is read-only. Insert, update and delete are rejected.

## Freshness

For dates inside the Recording API's recent 10-day window, the provider asks LocalRecordingClient for a fresh aggregate before returning the cached value.

Older dates are served from Lean Pedometer's local long-term snapshot only.

## Caller policy

The provider accepts calls from its own process and from the Lean Diary package com.cpkr.lwdiary.

This intentionally avoids a shared signing-key requirement because Play App Signing can give separate apps different signing keys.

## Lean Diary fallback

Lean Diary queries Lean Pedometer first. If the provider is not installed, unavailable or has no usable snapshot, it falls back to its existing Health Connect reader.

The provider call may wait briefly for a local Recording API read, so Lean Diary should perform the provider query away from the UI thread.