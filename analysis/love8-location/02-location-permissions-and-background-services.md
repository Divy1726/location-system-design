# 02 — Location Permissions and Background Services

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Static forensic extraction from `AndroidManifest.xml` and decompiled Java/Kotlin source files.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Declared Permissions Analysis

Inspection of the decoded manifest (`C:\Users\sutha\Desktop\rea\extracted\base-extracted\AndroidManifest_decoded.xml`) reveals the explicit permission architecture required to support continuous location tracking, geofencing, motion recognition, and power management:

| Permission Name | Protection Level / Type | Functional Role in Love8 |
| :--- | :--- | :--- |
| `android.permission.ACCESS_FINE_LOCATION` | Dangerous (Runtime) | High-precision GPS and GNSS satellite positioning `[CONFIRMED_STATIC]` |
| `android.permission.ACCESS_COARSE_LOCATION` | Dangerous (Runtime) | Network/Cell-tower and Wi-Fi triangulation fallback `[CONFIRMED_STATIC]` |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | Dangerous (Runtime, API 29+) | Uninterrupted positioning when app is minimized or device is locked `[CONFIRMED_STATIC]` |
| `android.permission.FOREGROUND_SERVICE` | Normal | Baseline capability to execute background foreground services `[CONFIRMED_STATIC]` |
| `android.permission.FOREGROUND_SERVICE_LOCATION` | Normal (API 34+) | Mandatory Android 14 declaration for location foreground services `[CONFIRMED_STATIC]` |
| `android.permission.ACTIVITY_RECOGNITION` | Dangerous (Runtime, API 29+) | Detection of user motion state (still, walking, driving) `[CONFIRMED_STATIC]` |
| `com.google.android.gms.permission.ACTIVITY_RECOGNITION` | Normal | Pre-API 29 Google Play Services motion recognition fallback `[CONFIRMED_STATIC]` |
| `android.permission.WAKE_LOCK` | Normal | CPU wake lock acquisition during location acquisition and dispatch `[CONFIRMED_STATIC]` |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Rescheduling background location sync after device reboot `[CONFIRMED_STATIC]` |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Normal | Exemption prompt from Android Doze mode and App Standby buckets `[CONFIRMED_STATIC]` |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Detection of network connectivity transitions `[CONFIRMED_STATIC]` |
| `android.permission.ACCESS_WIFI_STATE` | Normal | Used in Wi-Fi stationary filtering algorithm (`getMineNetInfo`) `[CONFIRMED_STATIC]` |
| `android.permission.POST_NOTIFICATIONS` | Dangerous (Runtime, API 33+) | Display of continuous foreground service persistent notification `[CONFIRMED_STATIC]` |

---

## 2. Core Foreground Location Service: `LoveLocationServer`

The primary engine sustaining continuous background execution is `com.lovemaps.core.server.LoveLocationServer`.

### Manifest Declaration
```xml
<service
    android:name="com.lovemaps.core.server.LoveLocationServer"
    android:exported="false"
    android:foregroundServiceType="location" />
```
`[CONFIRMED_STATIC]`

### Service Architecture & Lifecycle Code Analysis
- **Base Class:** `androidx.lifecycle.LifecycleService` (provides lifecycle coroutine scopes directly to the service).
- **Class Reference:** `C:\Users\sutha\Desktop\rea\extracted\decompiled_love8\com\lovemaps\core\server\LoveLocationServer.java`

```java
// LoveLocationServer.java (Lines 143-160, 212-217)
public final void a() {
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            // 8 = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            startForeground(100, Love8InfoNotificationExt.INSTANCE.getLove8BasicNotification(), 8);
        } else {
            startForeground(100, Love8InfoNotificationExt.INSTANCE.getLove8BasicNotification());
        }
    } catch (Throwable th2) { ... }
}

@Override
public final int onStartCommand(Intent intent, int flags, int startId) {
    super.onStartCommand(intent, flags, startId);
    a(); // Guarantees foreground state immediately on command
    r.B(p.a(this.f19745a.getF19782a()), null, null, new C08871(), 3);
    return 2; // 2 = Service.START_NOT_STICKY
}
```
`[CONFIRMED_STATIC]`

### Lifecycle Behaviors Verified
1. **Foreground Promotion Timing:**
   `a()` is executed immediately in `onCreate()` (line 164), `onStart()` (line 207), and `onStartCommand()` (line 214) to satisfy Android OS foreground service timeout constraints (5 seconds before ANR/ForegroundServiceDidNotStartInTimeException) `[CONFIRMED_STATIC]`.
2. **Notification Configuration:**
   - **Notification ID:** `100`
   - **Channel ID:** Managed via `Love8InfoNotificationExt.INSTANCE.getLove8BasicNotification()`
   - **Foreground Type Flag:** Value `8` (`0x00000008`), corresponding to `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION` required under Android 10+ (API 29+) and enforced under Android 14 (API 34) `[CONFIRMED_STATIC]`.
3. **Restart Policy:**
   Returns `2` (`Service.START_NOT_STICKY`). If the operating system terminates the process under severe memory pressure, the OS does not automatically recreate the service with the pending intent unless explicitly re-triggered by WorkManager, boot receiver, or user activity `[CONFIRMED_STATIC]`.
4. **Service Startup Trigger:**
   In `onStartCommand()`, an asynchronous Kotlin coroutine executes `EXTKt.startLocation("启动定位服务")` (Chinese string: "Start location service"), activating the singleton `LocationManager` pipeline `[CONFIRMED_STATIC]`.
5. **Geofence Registration at Startup:**
   In `onCreate()` (line 87), the service initiates geofence verification asynchronously:
   ```java
   GeofenceManager geofenceManager = l.f51022a;
   geofenceManager.e(false, this);
   ```
   `[CONFIRMED_STATIC]`

---

## 3. Background Receivers and Wakeup Mechanisms

| Receiver Class | Declared Intent Filters | Actions & Forensic Findings |
| :--- | :--- | :--- |
| `com.lovemaps.core.broadcast.LocationBroadcastTransitionReceiver` | `google.action_process_transition_location` | PendingIntent callback receiver for background GMS FusedLocationProviderClient transitions `[CONFIRMED_STATIC]`. |
| `com.lovemaps.core.broadcast.GeofenceOrbitBroadcastReceiver` | Internal Geofence PendingIntent | Receives geofence boundary dwell/exit/enter events for orbit tracking `[CONFIRMED_STATIC]`. |
| `com.lovemaps.core.broadcast.GeofencePlacesBroadcastReceiver` | Internal Geofence PendingIntent | Receives frequent-place boundary transitions (Home/Work/Custom places) `[CONFIRMED_STATIC]`. |
| `com.lovemaps.core.broadcast.BatteryChangeReceiver` | `android.intent.action.BATTERY_CHANGED`, `ACTION_POWER_CONNECTED`, `ACTION_POWER_DISCONNECTED` | Monitors battery level changes and power state switches `[CONFIRMED_STATIC]`. |
| `com.lovemaps.core.broadcast.BootBroadcastReceiver` | `android.intent.action.BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED` | Restarts background location synchronizers and reschedules WorkManager jobs on boot `[CONFIRMED_STATIC]`. |

---

## 4. Background Scheduled Tasks & WorkManager Architecture

Love8 employs `androidx.work.WorkManager` (2.9+) to manage resilient background execution that survives app termination:

### A. `SendLocationInfoWorker`
- **Class Reference:** `com.lovemaps.core.work.SendLocationInfoWorker`
- **Inheritance:** `androidx.work.CoroutineWorker`
- **Foreground Worker Promotion:** Implements `createForegroundInfo()` with Notification ID `100` and `NOTIFICATION_CHANNEL_ID = "location_channel"`.
- **Power Management:** Explicitly acquires an Android `PowerManager.WakeLock` (`WAKE_LOCK` with timeout) during execution to prevent CPU sleep before GPS coordinates are resolved and transmitted `[CONFIRMED_STATIC]`.
- **Action:** Triggers `LocationManager.requestCurrentLocation()` or `LocationManager.getLastLocationWork()`.

### B. `UploadOrbitWork`
- **Class Reference:** `com.lovemaps.core.work.UploadOrbitWork`
- **Inheritance:** `androidx.work.CoroutineWorker`
- **Function:** Uploads batched location orbit and trajectory records from the local Room database to the backend server. Receives boolean argument `"upload_all"` `[CONFIRMED_STATIC]`.

### C. `PlacesSendEnterWork` & `PlacesSendExitWork`
- **Class Reference:** `com.lovemaps.core.work.PlacesSendEnterWork`
- **Enqueued via:** `GeofencePlacesSendManager`
- **Unique Work Policy:** `ExistingWorkPolicy.REPLACE` (`places_send_enter_work` and `places_send_exit_work`).
- **Backoff Policy:** `BackoffPolicy.EXPONENTIAL` with initial retry delay of `30` seconds `[CONFIRMED_STATIC]`.
