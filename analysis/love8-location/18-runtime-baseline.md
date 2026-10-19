# Phase 1 Runtime Baseline Report: Love8 v3.19.0 on Android 13 Device

**Document ID**: `18-runtime-baseline.md`  
**Target Package**: `ltd.love8.couples.relationship`  
**Version**: `3.19.0` (`versionCode=232`)  
**Target Device**: Samsung Galaxy A22 (`SM-A225F`, Serial: `RZ8R81AT1EY`)  
**Android Platform**: Android 13 (API 33, Tiramisu), Build `TP1A.220624.014.A225FXXSBDYE2`  
**Primary Execution User**: User 0 (`UID: 10303`, `u0_a303`)  
**Investigation Timestamp**: 2026-10-19 13:10 IST  
**Methodology**: Passive non-invasive ADB shell diagnostics, dumpsys inspection, and logcat sampling. Zero modification of app data, OS settings, or permissions. Zero Frida / mock locations.

---

## 1. Device and OS Architecture Verification

| Metric / Parameter | Observed Value | Evidentiary Source |
| :--- | :--- | :--- |
| **Device Model** | Samsung Galaxy A22 (`SM-A225F`) | `adb shell getprop ro.product.model` |
| **Android Version** | 13 | `adb shell getprop ro.build.version.release` |
| **API Level (targetSdk / runtime)** | SDK 33 (`Tiramisu`) | `adb shell getprop ro.build.version.sdk` |
| **Build Fingerprint** | `TP1A.220624.014.A225FXXSBDYE2` | `adb shell getprop ro.build.display.id` |
| **Screen State** | Current ON / Effective ON | `dumpsys jobscheduler` |
| **Multi-Profile Topology** | User 0 (Primary Personal) & User 150 (Knox Secure Folder) | `dumpsys package`, `pm list users` |

> [!NOTE]
> Samsung One UI implements Knox container isolation on User 150. Global commands without target user qualification fail with `SecurityException: Shell does not have permission to access user 150`. All application-level queries are strictly directed to User 0 (`--user 0` / `u0a303`).

---

## 2. Package and Runtime Identity

| Attribute | Baseline Value | Forensic Observation |
| :--- | :--- | :--- |
| **Package Name** | `ltd.love8.couples.relationship` | Confirmed installed on User 0 |
| **Version Name** | `3.19.0` | Matches decompiled XAPK artifact |
| **Version Code** | `232` | Matches decompiled manifest |
| **App User ID** | `u0_a303` (`10303`) | Active runtime sandbox UID |
| **Primary App Process** | `ltd.love8.couples.relationship` | `PID 32128` (PPID 823, State `R`, VmSize ~37.8 MB) |
| **Sandboxed Web Process** | `com.google.android.webview:sandboxed_process0...` | `PID 32366` (Isolated WebView renderer) |
| **Top Activity** | `com.lovemaps.app.mvvm.ui.activity.MainActivity` | Confirmed top foreground activity via `TopAppTimer` |

---

## 3. Location Permissions and AppOps Audit

Static analysis in Mission 1 indicated Love8 declares both foreground and background location permissions. Runtime inspection on the authorized device demonstrates that **all critical runtime permissions are explicitly granted**:

### Granted Runtime Permissions (User 0)
- `android.permission.ACCESS_FINE_LOCATION`: `granted=true` (`flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED|524288]`)
- `android.permission.ACCESS_COARSE_LOCATION`: `granted=true` (`flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]`)
- `android.permission.ACCESS_BACKGROUND_LOCATION`: `granted=true` (`flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED|RESTRICTION_INSTALLER_EXEMPT]`)
- `android.permission.ACTIVITY_RECOGNITION`: `granted=true` (`flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]`)
- `android.permission.POST_NOTIFICATIONS`: `granted=true` (`flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]`)

### Active AppOps Modes (`cmd appops get ltd.love8.couples.relationship`)
```text
COARSE_LOCATION: allow
FINE_LOCATION: allow; time=+20s471ms ago
GPS: allow; time=+1m27s250ms ago (running)
WIFI_SCAN: allow; time=+20s471ms ago
READ_CLIPBOARD: allow; time=+1m24s666ms ago
WAKE_LOCK: allow; time=+25m38s496ms ago; duration=+4s95ms
MONITOR_LOCATION: allow; time=+1m27s250ms ago (running)
MONITOR_HIGH_POWER_LOCATION: allow; time=+1m27s250ms ago (running)
START_FOREGROUND: allow; time=+1m27s250ms ago (running)
```

**Key Takeaway**: The app currently possesses `allow` for all location and foreground execution AppOps. Both `MONITOR_LOCATION` and `MONITOR_HIGH_POWER_LOCATION` are in an active `(running)` state.

---

## 4. Foreground Services and Notification Infrastructure

Inspection of `dumpsys activity services ltd.love8.couples.relationship` reveals the exact running service components:

### 1. `LoveLocationServer` (Foreground Location Service)
```text
* ServiceRecord{a216572 u0 ltd.love8.couples.relationship/com.lovemaps.core.server.LoveLocationServer}
  intent={cmp=ltd.love8.couples.relationship/com.lovemaps.core.server.LoveLocationServer}
  processName=ltd.love8.couples.relationship
  app=ProcessRecord{5f7dc5b 32128:ltd.love8.couples.relationship/u0a303}
  allowWhileInUsePermissionInFgs=true
  isForeground=true
  foregroundId=100
  foregroundNoti=Notification(
      channel=system_location_permission
      shortcut=null
      contentView=ltd.love8.couples.relationship/0x7f0e01ef
      flags=0x6a (FLAG_FOREGROUND_SERVICE | FLAG_ONGOING_EVENT | FLAG_NO_CLEAR)
      category=service
      groupKey=location_server
      vis=PRIVATE
  )
  startRequested=true
  stopIfKilled=true
```

### 2. `EMFCMMSGService` (Easemob / Firebase Cloud Messaging Service)
```text
* ServiceRecord{3fb4f06 u0 ltd.love8.couples.relationship/com.lovemaps.im.service.EMFCMMSGService}
  intent={act=com.google.firebase.MESSAGING_EVENT pkg=ltd.love8.couples.relationship}
  processName=ltd.love8.couples.relationship
  app=ProcessRecord{5f7dc5b 32128:ltd.love8.couples.relationship/u0a303}
  startForegroundCount=0
```

---

## 5. System Location Manager & Fused Provider Registrations

`dumpsys location` provides conclusive, live verification of the location requests issued by Love8 through Google Play Services (`com.google.android.gms`):

### Live Provider Registration
```text
gps provider request = ProviderRequest[@+5s0ms, HIGH_ACCURACY, WorkSource{10303 ltd.love8.couples.relationship}]

gps provider listeners:
  10210/com.google.android.gms[fused_location_provider]/34DE33C1 
  Request[@+5s0ms HIGH_ACCURACY, minUpdateInterval=0, hiddenFromAppOps, WorkSource{10303 ltd.love8.couples.relationship}]
```

### Corroboration with Static Findings
| Parameter | Statically Derived (`GoogleLocationConfig`) | Runtime Observed (`dumpsys location`) | Status |
| :--- | :--- | :--- | :--- |
| **Requested Interval** | `5000L` (5.0s) | `@+5s0ms` | **CONFIRMED** |
| **Priority** | `100` (`PRIORITY_HIGH_ACCURACY`) | `HIGH_ACCURACY` | **CONFIRMED** |
| **WorkSource UID** | `10303` (`ltd.love8.couples.relationship`) | `WorkSource{10303 ltd.love8.couples.relationship}` | **CONFIRMED** |
| **Min Update Interval** | `2500L` (2.5s) in fallback / `0` in FusedRequest | `minUpdateInterval=0` | **CONFIRMED** |

---

## 6. Power Management and Battery Whitelisting

Android 13 enforces aggressive background restrictions (App Standby Buckets, Doze mode, and cached process limitations). Love8's configuration on the target device was audited via `dumpsys deviceidle` and `dumpsys jobscheduler`:

1. **Doze Whitelist Status**:
   `dumpsys deviceidle whitelist` lists:
   `user,ltd.love8.couples.relationship,10303`
   *Love8 has been placed on the user-exempted battery optimization whitelist.*
2. **App Standby Bucket**:
   The app is currently in `Bucket: 6 (ACTIVE)` with `EXEMPTED` quota (`allowedTimePerPeriodMs=600000`, `jobCountLimit=75`).
3. **JobScheduler / WorkManager**:
   - `androidx.work.impl.background.systemjob.SystemJobService` is active.
   - Enqueued jobs possess `Priority: 400 [HIGH]` and `Internal flags: 1 HAS_FOREGROUND_EXEMPTION`.
   - 53 successful job finishes recorded over the session with 0 quota violations.

---

## 7. Passive Diagnostics and Observability Limitations

During baseline capture, logcat was passively analyzed without clearing buffers (`logcat -d`). The following diagnostic dynamics were identified:

1. **Stripped Release Logs**:
   In `com.lovemaps.core.ext.LoggerExtKt`, methods `loggerD`, `loggerI`, `loggerW`, and `loggerE` have empty method bodies (`str.getClass(); str2.getClass();`) due to R8 release optimization. Consequently, Love8 does not emit verbose text lines to standard logcat buffers under the `LocationManager` tag.
2. **Framework-Level Traces**:
   Samsung's `SLocationService`, `GnssLocationProvider`, and Google Play Services `FusedLocationService` emit real-time continuous traces verifying the pipeline (`PositionManager - receive fused location`, `fused provider request = ProviderRequest[@+5s0ms...]`).
3. **Logan Encrypted Telemetry**:
   `needUploadLoggerE` delegates to `com.dianping.logan.Logan` for encrypted on-disk storage (`Logan.c(i5, ...); Logan.a();`). This client-side audit file is kept in app private storage (`/data/user/0/ltd.love8.couples.relationship/files/logan`).
4. **Active UI Rendering**:
   Foreground logcat shows continuous rendering of PAG vector weather animations (`smallWeathers/weather_clear_day.pag`, `weather_light_rain.pag`) driven by `MainActivity`.

---

## 8. Summary of Baseline State for Subsequent Phases

| Capability / Check | Status | Evidence / Notes |
| :--- | :--- | :--- |
| **ADB Connectivity** | Pass | Authorized, persistent transport `1` |
| **App Running State** | Pass | Foreground `MainActivity`, PID 32128 |
| **Location Service** | Pass | `LoveLocationServer` active as FGS (ID: 100) |
| **Permissions** | Pass | FINE, COARSE, BACKGROUND, ACTIVITY_REC granted |
| **Battery Whitelist** | Pass | Exempted from battery optimization |
| **Google Fused Client** | Pass | Active GPS request at 5000ms interval |
| **Partner Sync Service** | Pass | `EMFCMMSGService` active for push sync |
| **WorkManager Sync** | Pass | SystemJobService active, High Priority |

The environment and runtime baseline are fully validated. Investigation is ready to proceed to **Phase 2 — Foreground Location Acquisition Diagnostics**.
