# 17 — Verified Evidence Audit, Corrections, and Unresolved Gaps

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Formal evidentiary audit against decompiled source lines and bytecode disassembly.  
**Evidentiary Tags:** `[VERIFIED]`, `[PARTIALLY_VERIFIED]`, `[CORRECTED]`, `[RUNTIME_REQUIRED]`, `[INACCESSIBLE]`

---

## 1. Master Evidence Audit & Corrections

Every major architectural claim from the initial static-analysis pass has been re-examined against exact source code references in `decompiled_love8`:

| Claim / Component | Previous Finding | Deeper Audit Finding & Source Verification | Final Status |
| :--- | :--- | :--- | :--- |
| **Coordinate Transformation** | Transformed to GCJ-02 prior to storage/transmission. | `EXTKt.gpsToGcj02` (Lines 5383–5395) contains an explicit region gate: `if (!isCN()) return latLng;`. **Outside mainland China, coordinates remain raw WGS-84.** | `[CORRECTED]` |
| **Flutter Application Scope** | Flutter presentation layer handles map screens. | `libapp.so` string pool contains 38 packages. Application code in `package:love8_flutter` implements **only** CS Chat, Canvas Drawing, and VIP events. **Map rendering, location tracking, and timeline are 100% native Android.** | `[CORRECTED]` |
| **Speed Preference** | Velocity ceiling of 200 km/h applied. | `LocationManager.java` (Line 811): **Derived speed** `(dist/time)*3.6` is used for the 200 km/h anomaly gate. **Hardware speed** `location.getSpeed()` is stored in Room DB and used for the indoor jump filter ($\ge 5\text{ m/s}$). | `[VERIFIED]` |
| **Place Transition Hysteresis** | Transition debouncing implemented. | `PlaceTransitionConfirmation.java` (Lines 83–110): Symmetrical for both Enter and Exit: requires both **$\ge 3$ consecutive samples** AND **$\ge 60$ seconds ($60,000\text{ ms}$) duration**. | `[VERIFIED]` |
| **Opposite Work Cancellation** | Flapping prevented in WorkManager. | `GeofencePlacesSendManager.java` (Lines 101–105): Confirmed Enter cancels `"places_send_exit_work"`; Confirmed Exit cancels `"places_send_enter_work"`. | `[VERIFIED]` |
| **Trajectory Compression Epsilon** | Douglas-Peucker compression used. | `OrbitManager.java` (Lines 340–375): Exact epsilon is **$\epsilon = 1.0\times 10^{-4}$ degrees ($\approx 11.13\text{ meters}$)**. Pre-filtered by adaptive accuracy ceiling ($> \bar{A} + 30\text{m}$). | `[VERIFIED]` |
| **Consecutive Filter Reset** | Resets after 5 rejections. | `LocationManager.java` (Lines 804–807): If `f50903b >= 5`, resets counter and forces acceptance of 5th point to prevent getting locked on a faulty anchor. | `[VERIFIED]` |
| **Service Restart Policy** | Service runs continuously in background. | `LoveLocationServer.java` (Line 216) returns `START_NOT_STICKY (2)`. OS does not auto-restart service when killed. Resilience relies on `SendLocationInfoWorker`, `BootBroadcastReceiver`, and FCM data pushes. | `[VERIFIED]` |
| **Live P2P Transport** | Tencent IM transmits updates. | `TIMMessageManager$updateFriendLocation$1.java` (Lines 109–120): Deserializes `UploadLocationBean`, drops stale fixes (`EXTKt.isOldLocation`), updates `HomeUserLocationInfoManager`. | `[VERIFIED]` |

---

## 2. Inaccessible Knowledge Boundaries & Unresolved Gaps

The following capabilities are architecturally external or require dynamic hardware measurement:

| Knowledge Gap | Technical Limitation | Rationale & Evidence Boundary |
| :--- | :--- | :--- |
| **Server-Side Reverse Geocoding** | `[INACCESSIBLE]` | The Love8 client transmits coordinate pairs (`lat`, `lng`) to REST endpoints and receives localized address names. The address resolution database and POI synthesis reside exclusively on backend servers. |
| **Long-Term Stay Semantic Clustering** | `[INACCESSIBLE]` | While the client records raw visit counts in `stay_location_new_dao`, clustering algorithms that classify unlabelled stays into "Home", "Work", or "Frequent Place" are calculated server-side. |
| **True Milliampere-Hour (mAh) Battery Drain** | `[RUNTIME_REQUIRED]` | Code decompilation establishes request cadences (5s nominal, 2.5s fastest, 10s WakeLocks), but exact battery impact varies widely across OEM kernel governors (Samsung OneUI, Xiaomi HyperOS, Google Pixel) and requires physical hardware power monitoring. |
| **Tencent Cloud IM Server Routing** | `[INACCESSIBLE]` | The client links against `libImSDK.so` and dispatches C2C custom messages. The internal server-side routing, queueing, and fallback logic inside Tencent Cloud clusters cannot be decompiled from client binaries. |

---

## 3. Step 10: Runtime Validation Readiness Report

A non-invasive system audit was conducted to verify dynamic analysis readiness:

* **Android Debug Bridge (ADB):**
  - Path: `C:\Users\sutha\AppData\Local\Android\Sdk\platform-tools\adb.exe` `[VERIFIED]`
* **Connected Physical Test Device:**
  - Device Serial: `RZ8R81AT1EY`
  - Connection State: `device` (Authorized via USB Debugging) `[VERIFIED]`
* **Target Package Verification on Device:**
  - Package: `ltd.love8.couples.relationship`
  - Installed Version: `versionName=3.19.0`, `versionCode=232` (User 0) `[VERIFIED]`
* **Operational Boundary Adherence:**
  - No commands were executed to launch, instrument, modify, or change settings on the device.
  - The test device is confirmed ready for controlled runtime tracing (logcat monitoring, Fridadynamic hooks, or socket inspection) whenever you grant explicit approval.
