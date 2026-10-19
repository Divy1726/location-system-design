# 09 — Evidence Index and Unknowns

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Comprehensive evidentiary ledger and explicit knowledge boundary declaration.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Master Evidence Ledger

The following ledger establishes the evidentiary basis for every architectural conclusion across the research mission:

| Topic & Finding | Artifact & Checksum | Class / Source File & Line Range | Concrete Evidence Statement | Confidence Tag |
| :--- | :--- | :--- | :--- | :--- |
| **Original XAPK Inventory** | `Love8+-+App+for+Couples_3.19.0_APKPure.xapk` (`0a484a92...`) | Archive Root (`manifest.json`) | Package `ltd.love8.couples.relationship`, version 3.19.0 (code 477), target SDK 34, min SDK 24. | `[CONFIRMED_STATIC]` |
| **Foreground Service Lifecycle** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.server.LoveLocationServer.java` (Lines 143-160, 212-217) | Service extends `LifecycleService`, `onStartCommand` returns `2` (`START_NOT_STICKY`), starts foreground with type `8` (`FOREGROUND_SERVICE_LOCATION`) and notification ID 100. | `[CONFIRMED_STATIC]` |
| **GMS Fused Location Parameters** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.manage.location.GoogleLocationConfig.java` (Lines 65-91) & `GoogleLocation.java` (Lines 140-145) | `LocationRequest`: Priority `100` (`PRIORITY_HIGH_ACCURACY`), `intervalMillis = 5000L` (5s), `minUpdateIntervalMillis = 2500L` (2.5s), `minUpdateDistanceMeters = 0.0f`. | `[CONFIRMED_STATIC]` |
| **GCJ-02 Coordinate Transformation** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.manage.LocationManager.java` (Lines 759-762) | Every raw GNSS fix is transformed via `EXTKt.gpsToGcj02()` unless originating from `"mock_test_location"` or `"fcm_location_provider"`. | `[CONFIRMED_STATIC]` |
| **Horizontal Accuracy Cutoff** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.manage.LocationManager.java` (Lines 769-777) | Fixes with reported accuracy $> 200.0\text{ meters}$ are discarded with diagnostic log `"排除该位置 -> accuracy == ..."` and increment filter count. | `[CONFIRMED_STATIC]` |
| **Theoretical Velocity Anomaly Rejection** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.manage.LocationManager.java` (Lines 808-815) | Computes `(distance / deltaSec) * 3.6f`. Discards fixes where speed $> 200.0\text{ km/h}$ with log `"排除该位置 -> 理论速度过高"`. | `[CONFIRMED_STATIC]` |
| **Stationary Wi-Fi Jump Rejection** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.manage.LocationManager.java` (Lines 815-834) | When user is at Frequent Place (`userStatusManager.f51095i == 0`) and connected to Wi-Fi (`netInfo == 1`), jumps $\ge \text{placeRadius}$ are rejected as indoor GPS drift. | `[CONFIRMED_STATIC]` |
| **Consecutive Filter Reset** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.manage.LocationManager.java` (Lines 804-807) | If rejections reach 5 (`f50903b >= 5`), resets `f50903b = 0` and forces acceptance of 5th sample to prevent getting locked on an invalid baseline. | `[CONFIRMED_STATIC]` |
| **Place Transition Confirmation** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.manage.PlaceTransitionConfirmation.java` (Lines 83-110) | Candidate arrival/departure transitions require both $\ge 3\text{ consecutive points}$ AND $\ge 60,000\text{ ms}$ (60s) duration before confirmation. | `[CONFIRMED_STATIC]` |
| **Opposite Work Cancellation** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.manage.GeofencePlacesSendManager.java` (Lines 56-80) | Enqueueing `places_send_enter_work` automatically cancels pending `places_send_exit_work` via WorkManager to prevent race conditions. | `[CONFIRMED_STATIC]` |
| **Trajectory Simplification** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.work.orbit.OrbitManager.java` (Line 46, Method 55) | Compresses raw historical tracking breadcrumbs using Ramer–Douglas–Peucker algorithm (`douglasPeucker(points, epsilon)`). | `[CONFIRMED_STATIC]` |
| **Real-Time P2P Transport** | `config.arm64_v8a.apk` (`00d77cd5...`) & Base APK | `libImSDK.so`, `libTIMQuicPlugin.so`, `com.lovemaps.core.manage.tim.TIMMessageManager.java` | Real-time live location updates stream directly between partner devices using Tencent Cloud IM SDK socket/QUIC channels (`updateFriendLocation`). | `[CONFIRMED_STATIC]` |
| **Battery Level Telemetry** | `ltd.love8.couples.relationship.apk` (`def1b368...`) | `com.lovemaps.core.broadcast.BatteryChangeReceiver.java` (Lines 105-112) | Captures `ACTION_BATTERY_CHANGED`, calculates `(level * 100) / scale`, and transmits battery status to partner. | `[CONFIRMED_STATIC]` |

---

## 2. Explicit Knowledge Boundaries & Unknowns

In adherence to non-negotiable research constraints, areas where client bytecode does not provide definitive answers are explicitly classified:

| Area / Component | Knowledge Boundary Description | Evidentiary Tag | Rationale |
| :--- | :--- | :--- | :--- |
| **Cloud Reverse Geocoding Algorithms** | Place name synthesis and address generation on Love8 backend servers | `[UNKNOWN]` | Server-side proprietary API. Client transmits only GCJ-02 coordinates and receives resolved address strings; reverse geocoding logic is not resident in client APK. |
| **Cloud Stay Clustering & Machine Learning** | Cloud-level machine learning models for detecting semantic places | `[UNKNOWN]` | Client maintains raw stay counts (`StayLocationNewBean`), but long-term semantic clustering (categorizing unlabelled places as Home vs Work) resides on backend servers. |
| **Hardware Battery Drain Across OEM Android Skins** | Exact milliampere-hour (mAh) consumption on Samsung OneUI, Xiaomi MIUI/HyperOS, and Google Pixel | `[UNKNOWN]` | Requires physical instrumentation using hardware power monitors (e.g. Monsoon Power Monitor). Bytecode proves configuration (5s interval, WakeLocks, START_NOT_STICKY), but real-world drain varies with OEM kernel governors. |
| **Flutter Map Custom Shader Pipeline** | Internal low-level OpenGL/Vulkan rendering routines in `libapp.so` | `[UNKNOWN]` | Flutter Dart code is compiled to AOT assembly (`snapshot hash 0451907c2eaa8467e848c0067bfe8ed4`). Java/Kotlin platform channels deliver coordinates to Flutter, but internal shader logic inside `libapp.so` was not deconstructed. |

---

## 3. Phase 6 Readiness Assessment

- **Android Debug Bridge (ADB) Availability:**
  Verification command `adb devices` exited with code 1: `adb` is not currently recognized on the system `PATH` `[UNKNOWN]`.
- **Target Device Presence:**
  No Android device is currently connected or authorized on this workstation.
- **Prerequisites for Authorized Runtime Phase:**
  Should runtime dynamic investigation (logcat monitoring, Fridadynamic tracing, or packet inspection) be requested in the future:
  1. Install Android SDK Platform-Tools and add `adb` to system `PATH`.
  2. Connect a dedicated physical Android test device with USB Debugging enabled.
  3. Ensure explicit device owner authorization is verified before launching instrumented runs.
