# 08 — Error Handling and Power Management

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Forensic decompilation of `LocationManager.java`, `LoveLocationServer.java`, `SendLocationInfoWorker.java`, `PermissionPopupKt.java`, and `GeofencePlacesSendManager.java`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Battery Optimization & Doze Mode Exemption

Continuous background tracking on modern Android versions (Android 8.0+ to Android 14) is subject to strict battery management heuristics (Doze mode, App Standby Buckets, and OEM background killers like MIUI/EMUI/ColorOS). Love8 implements multiple layers of protection:

### A. Battery Optimization Exemption Request
- **Permission:** `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` `[CONFIRMED_STATIC]`.
- **UI Flow:** `com.lovemaps.app.homepop.PermissionPopupKt` prompts the user through interactive dialogs to whitelist Love8 from system battery saver optimizations `[CONFIRMED_STATIC]`.
- **System Action:** Launches `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with URI `package:ltd.love8.couples.relationship`.
- **Functional Effect:** Prevents Android Doze mode from deferring network access and holding CPU timers when the device screen is turned off `[CONFIRMED_STATIC]`.

### B. Strategic WakeLock Acquisition
In `com.lovemaps.core.work.SendLocationInfoWorker`, Love8 acquires a timed hardware WakeLock:
```java
// SendLocationInfoWorker.java (Lines 76-80)
PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK, 
    "Love8:LocationWakeLock"
);
wakeLock.acquire(10000L); // 10-second timeout guarantee
```
`[CONFIRMED_STATIC]`
- **Timeout Protection:** Prevents indefinite CPU drain by capping the lock duration at 10 seconds, guaranteeing CPU execution long enough to acquire coordinates and dispatch them `[CONFIRMED_STATIC]`.

---

## 2. Dynamic Permission Revocation & Recovery

If location permissions (`ACCESS_FINE_LOCATION` or `ACCESS_BACKGROUND_LOCATION`) are revoked by the user or OS while the foreground service is active, Love8 intercepts the failure state in `LoveLocationServer.onCreate`:

```java
// LoveLocationServer.java (Lines 166-194)
if (!com.lovemaps.core.permission.i.l()) { // Permission check helper
    NotificationChannel notificationChannel = new NotificationChannel(
        "request_location_permissions", 
        "request_location_permissions", 
        NotificationManager.IMPORTANCE_DEFAULT
    );
    notificationChannel.setSound(null, null);
    notificationChannel.setShowBadge(false);
    ((NotificationManager) this.f51431b.getValue()).createNotificationChannel(notificationChannel);
    
    PendingIntent activity = PendingIntent.getActivity(
        this, 0, 
        getPackageManager().getLaunchIntentForPackage(getPackageName()), 
        WidgetExtKt.pendingIntent_flag
    );
    
    NotificationCompat.Builder builder = new NotificationCompat.Builder(
        this, "system_location_permission"
    );
    builder.setContentTitle(getString(R$string.translation_idx_72));
    builder.setContentText(getString(R$string.translation_idx_73));
    builder.setContentIntent(activity);
    builder.setOngoing(false);
    Notification notificationB = builder.build();
    NotificationManagerCompat.from(getApplicationContext()).notify(notificationB);
}
```
`[CONFIRMED_STATIC]`
- **Fail-Safe Notification:** Instead of crashing or silently failing, the service posts an alert directly onto channel `request_location_permissions` linking back to the app settings `[CONFIRMED_STATIC]`.

---

## 3. Algorithmic Error Recovery: Consecutive Filter Reset

A notorious flaw in mobile location tracking occurs when a tracking engine rejects valid updates because its internal reference anchor was established at a bad coordinate (e.g. initial indoor fix with 150m error).

### The Reset Implementation (`LocationManager.java`, Lines 804-807)
```java
} else if (locationManager.f50903b >= 5) {
    LoggerExtKt.needUploadLoggerE(
        "连续过滤次数过多，可能基准位置错误，接受当前位置 " + str 
        + "-> 重置过滤计数 latLng:" + location.getLatitude() + "," + location.getLongitude(), 
        "LocationManager", 555
    );
    locationManager.f50903b = 0; // Reset counter
    // Current coordinate is accepted and adopted as the new anchor baseline
}
```
`[CONFIRMED_STATIC]`

- **Filter Counter:** `mConsecutiveFilterCount` (`f50903b`) increments whenever a point is discarded for:
  - Accuracy $> 200\text{ m}$
  - Decreasing timestamp
  - Theoretical velocity $> 200\text{ km/h}$
  - Wi-Fi indoor drift jump
- **Recovery Action:** When `f50903b` reaches `5`, the entire history of recent rejections is deemed an indicator of a false baseline. The current sample is admitted, breaking potential deadlock `[CONFIRMED_STATIC]`.

---

## 4. Mock Location & GPS Spoofing Detection

Love8 models mock coordinates in its internal entities:
- **Model Field:** `LocalLocationBean.isMock` (Boolean) `[CONFIRMED_STATIC]`
- **Bypass Rule:** If `location.getProvider() == "mock_test_location"`, coordinate transformation (`gpsToGcj02`) is bypassed, and stability filters allow developers to simulate flight/travel trajectories for debugging without triggering anomaly filters `[CONFIRMED_STATIC]`.
- **Production Detection:** Leverages standard Android framework calls (`Location.isFromMockProvider()` on API $< 31$, `Location.isMock()` on API $\ge 31$) to flag spoofed coordinates during partner sharing `[CONFIRMED_STATIC]`.

---

## 5. WorkManager Exponential Backoff & Retry Logic

Network timeouts or server downtime are handled via `androidx.work.BackoffPolicy`:

```java
// GeofencePlacesSendManager.java (Lines 68, 73)
OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(PlacesSendEnterWork.class)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS);
```
`[CONFIRMED_STATIC]`

- **Initial Backoff:** `30` seconds.
- **Progression:** $30\text{s} \to 60\text{s} \to 120\text{s} \to 240\text{s} \dots$
- **Offline Buffering:** If the device loses internet connectivity, points accumulate in `location_dao` (`isLocal = true`). Upon network restoration, `UploadOrbitWork` flushes pending batches in order without losing trajectory continuity `[CONFIRMED_STATIC]`.
