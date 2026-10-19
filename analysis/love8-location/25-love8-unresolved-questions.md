# Love8 Location System: Prioritized Ledger of Unresolved Questions & Testing Protocols

**Document ID**: `25-love8-unresolved-questions.md`  
**Package Under Test**: `ltd.love8.couples.relationship` (v3.19.0, `versionCode = 232`)  
**Hardware Environment**: Samsung Galaxy A22 (`SM-A225F`, Serial: `RZ8R81AT1EY`, Android 13 / One UI 5.1 / API 33)  
**Parent System Design**: `23-love8-location-system-design.md`  
**Purpose**: Document all remaining technical unknowns, boundary limitations, and the specific authorized protocols required to resolve them  

---

## 1. Overview & Boundary Rationale

The forensic investigation of Love8 v3.19.0 established the complete client-side architecture, acquisition parameters, in-memory filtering logic, stay hysteresis rules, trajectory compression algorithms, and live UI presentations.

However, strict adherence to non-invasive research ethics and safety boundaries—specifically prohibiting device rooting, Frida dynamic code injection, SSL/TLS man-in-the-middle decryption, and backend infrastructure tampering—leaves certain internal and cloud-side mechanisms unobserved.

This document catalogs these remaining unknowns in **strict priority order**, analyzes why each exists, and defines the exact authorized protocol needed to resolve it.

---

## 2. Prioritized Unknowns Ledger

### Priority 1: Server-Side Trajectory Clustering & Semantic Place Synthesis

- **The Unresolved Question**:  
  How does Love8’s cloud backend process compressed trajectory segments uploaded via `POST /trail/batch` to synthesize semantic place visits, cluster stay locations on the partner's timeline, and label venues (e.g. "Home", "Work", "Cafe")?
- **Current Evidence Status**:  
  - *Client Side*: The app uploads compressed polyline waypoints (`UploadOrbitDataList`) containing latitude, longitude, timestamp, speed, and accuracy `[CONFIRMED_STATIC]`. It queries `/fav-places` to retrieve place lists and `/moments/home/timeline-sync` to fetch timeline cards `[CONFIRMED_STATIC]`.
  - *Cloud Side*: Opaque boundary. The clustering algorithms (e.g. DBSCAN, HDBSCAN, or temporal dwell heuristics) run on Alibaba Cloud microservices `[OPAQUE_BOUNDARY]`.
- **Forensic Barrier**:  
  Server-side backend logic is hosted in closed-source cloud infrastructure and cannot be decompiled from client APK binaries.
- **Authorized Testing Protocol to Resolve**:  
  1. *Controlled Motion Experiment*: Execute a controlled multi-day field test where the device dwells at known coordinates for varying intervals ($15\text{ min}$, $30\text{ min}$, $1\text{ hour}$, $4\text{ hours}$).
  2. *Timeline Feed Audit*: Query `GET /moments/home/timeline` after each dwell period to observe when the server creates a new named place segment vs. merging it into a transit event.
  3. *Map Cluster Boundary Test*: Record the bounding boxes of places returned by `/fav-places` to infer the spatial clustering radius.
- **Architectural Impact for Target Development**:  
  Moderate. Client applications can implement standard client-side clustering (e.g. spatial bounding boxes or density clustering) without replicating proprietary Alibaba Cloud backend services.

---

### Priority 2: Partner-Side Independent Ingestion & Network Latency Profile

- **The Unresolved Question**:  
  What is the exact end-to-end network transmission latency (in milliseconds) from the instant the sender's GPS chip generates a fix to the instant the partner's screen begins interpolating the avatar? What is the packet loss rate under cellular handovers?
- **Current Evidence Status**:  
  - *Sender Side*: Confirmed 5.1s callback cadence and kernel socket transmissions (+137.8 KB during movement) `[CONFIRMED_BOTH]`.
  - *Partner Side*: Partner UI was manually observed rendering `"Within 7 m"` and freshness timestamps (`"1:33 pm"`) `[CONFIRMED_RUNTIME]`.
  - *Gap*: Independent millisecond-level network packet arrival timestamps on the partner smartphone were not recorded.
- **Forensic Barrier**:  
  The secondary partner smartphone was physically observed but was not connected to an ADB host workstation to preserve realistic, non-instrumented user conditions.
- **Authorized Testing Protocol to Resolve**:  
  1. Connect both the sender smartphone and partner smartphone to dual ADB debug interfaces on the same workstation.
  2. Execute synchronized NTP clock synchronization (`adb shell date`) across both devices.
  3. Run concurrent logcat captures filtering for sender dispatch (`TIMMessageManager: send`) and partner ingestion (`TIMMessageManager: onNewMessage`).
  4. Calculate $\Delta T_{\text{latency}} = T_{\text{partner\_recv}} - T_{\text{sender\_send}}$ across 100 consecutive fixes under Wi-Fi, 4G LTE, and 5G networks.
- **Architectural Impact for Target Development**:  
  Low. Tencent Cloud IM SDK benchmarks and standard TCP socket performance reliably guarantee $< 500\text{ ms}$ latency under typical broadband/LTE conditions.

---

### Priority 3: Native Fallback Behavior in the Absence of Google Play Services

- **The Unresolved Question**:  
  How does Love8 perform on AOSP or De-Googled devices (e.g. Huawei HarmonyOS, microG, or custom ROMs) where `GoogleApiAvailability.isGooglePlayServicesAvailable()` returns an error? Does `NativeLocationProvider` maintain the same 5-second cadence?
- **Current Evidence Status**:  
  - Bytecode contains a complete `NativeLocationProvider.java` implementing Android framework `LocationManager.requestLocationUpdates` using `GPS_PROVIDER` and `NETWORK_PROVIDER` `[CONFIRMED_STATIC]`.
  - On the test device (Samsung Galaxy A22), Google Play Services was fully present, so `FusedLocationProviderClient` was selected `[CONFIRMED_BOTH]`.
- **Forensic Barrier**:  
  Disabling Google Play Services on the authorized primary test phone would require altering device settings or installing custom firmware, violating the non-invasive boundary rules.
- **Authorized Testing Protocol to Resolve**:  
  1. Deploy Love8 to an authorized secondary AOSP testing device or Android emulator configured without Google Play Services.
  2. Execute ADB dumpsys diagnostics (`dumpsys location`) to verify whether `NativeLocationProvider` registers continuous GPS listeners.
  3. Measure battery consumption and accuracy degradation when relying purely on native hardware GPS without Google network location assist.
- **Architectural Impact for Target Development**:  
  Critical for global/cross-platform reach. Knowing whether native `LocationManager` is viable without GMS determines whether the target app can operate on Huawei devices or GMS-free enterprise hardware.

---

### Priority 4: Production SQLite Database Row Retention & Deletion Lifecycle

- **The Unresolved Question**:  
  How long are raw 5-second location points retained in Room table `local_location` before being pruned? Does the database enforce a maximum row count (e.g. 10,000 rows) or a time-to-live (e.g. 7 days)?
- **Current Evidence Status**:  
  - Static analysis confirmed table schemas `local_location` (`LocationDao`) and `stay_location_new` (`StayLocationNewDao`) `[CONFIRMED_STATIC]`.
  - In `OrbitManager.java:632`, abnormal trajectories are removed from in-memory maps (`orbitMap.remove`), and uploaded records set `isUpload = true` `[CONFIRMED_STATIC]`.
  - Direct SQL deletion queries (`DELETE FROM local_location WHERE time < ?`) were not located in the primary decompiled DAO slice.
- **Forensic Barrier**:  
  The target APK is an official release build (`android:debuggable="false"`). Android security sandboxing rejects `run-as ltd.love8.couples.relationship`, preventing direct SQLite file inspection (`/data/data/ltd.love8.couples.relationship/databases/`).
- **Authorized Testing Protocol to Resolve**:  
  1. Perform an authorized ADB backup (`adb backup -f love8.ab -noapk ltd.love8.couples.relationship`) if allowBackup permits, or inspect an unencrypted test device backup.
  2. Alternatively, run Love8 over 14 consecutive days on an authorized test device and measure the growth of the private app data directory via `dumpsys diskstats` or `adb shell du -sh /data/data/ltd.love8.couples.relationship`.
- **Architectural Impact for Target Development**:  
  High. Unchecked continuous 5-second GPS insertion generates $\approx 17,280\text{ rows/day}$. A production app must have an explicit TTL pruning policy (e.g. prune uploaded records older than 48 hours).

---

### Priority 5: Wire Protocol Binary Serialization on Tencent IM Port 9971

- **The Unresolved Question**:  
  What is the exact binary framing and protobuf definition used by `libImSDK.so` when packaging `UploadLocationBean` into packets transmitted over TCP port 9971?
- **Current Evidence Status**:  
  - Java DTO `UploadLocationBean` has fields `lat`, `lng`, `speed`, `dir`, `eq`, `hasCharging`, `time`, `accuracy`, `status`, `dwellTime`, `netWork`, `netName`, `Devicename`, `screenStatus` `[CONFIRMED_STATIC]`.
  - Serialized via `kotlinx.serialization` and passed to native `libImSDK.so` `[CONFIRMED_STATIC]`.
  - Socket traffic on port 9971 observed on device `[CONFIRMED_BOTH]`.
- **Forensic Barrier**:  
  Packet payloads on port 9971 are encrypted via proprietary Tencent Cloud transport security. Decrypting the raw wire bytes would require native memory hooking (Frida) or reverse engineering `libImSDK.so` assembly.
- **Authorized Testing Protocol to Resolve**:  
  1. Set up an isolated lab Wi-Fi network connected to a Wireshark capture tap.
  2. Inspect packet headers and frame delimiters on port 9971.
  3. Alternatively, reverse engineer `libImSDK.so` using REA native disassembly tools to map the internal JNI serialization bridge (`Java_com_tencent_imsdk_...`).
- **Architectural Impact for Target Development**:  
  Low. A custom application does not need to duplicate Tencent’s proprietary wire format; standard open protocols (e.g. WebSockets with Protocol Buffers or JSON over TLS) achieve equivalent or superior performance.

---

### Priority 6: OEM-Specific Task Killer Variations (MIUI, ColorOS, OriginOS)

- **The Unresolved Question**:  
  How does Love8 maintain background survival on aggressive third-party Android distributions (e.g. Xiaomi MIUI/HyperOS, Oppo ColorOS, Vivo OriginOS, Huawei EMUI) that routinely ignore standard Android `curProcState` priority and terminate whitelisted foreground services?
- **Current Evidence Status**:  
  - On the Samsung Galaxy A22 running One UI 5.1 (Android 13), `LoveLocationServer` ran with zero dropped callbacks over 60 seconds of screen sleep `[CONFIRMED_BOTH]`.
  - Code references custom manufacturer battery intent helpers (`com.lovemaps.core.ext.AutoStartPermissionHelper`) guiding users to manually grant "Auto-Start" permissions on Xiaomi and Huawei `[CONFIRMED_STATIC]`.
- **Forensic Barrier**:  
  Testing was constrained to the physically connected Samsung device.
- **Authorized Testing Protocol to Resolve**:  
  1. Install Love8 on secondary test devices representing major Chinese OEMs (Xiaomi Redmi, Oppo, Vivo).
  2. Run the Phase 3 background locked-screen test suite across each OEM device without granting custom OEM auto-start permissions.
  3. Measure the exact time-to-kill when the screen turns off across each manufacturer ROM.
- **Architectural Impact for Target Development**:  
  Critical for Android production readiness. Samsung is relatively compliant with standard AOSP lifecycle rules, whereas Xiaomi and Huawei require dedicated user-permission onboarding flows.

---

### Priority 7: High-Velocity Movement & Cadence Adaptation

- **The Unresolved Question**:  
  Does Love8 dynamically adjust its $5000\text{ ms}$ callback interval when the device reaches high velocities (e.g. highway driving at $100\text{ km/h}$), or does it remain strictly at 5000ms regardless of speed?
- **Current Evidence Status**:  
  - Tested speeds ranged from $0.0\text{ m/s}$ (stationary) to $8.29\text{ m/s}$ ($29.8\text{ km/h}$, running/sprinting) `[CONFIRMED_RUNTIME]`.
  - The callback interval remained constant: mean interval was $5.11\text{s}$ stationary and $5.12\text{s}$ moving `[CONFIRMED_BOTH]`.
  - In `LoveLocationServer.java:31`, `setIntervalMillis(5000L)` is hardcoded in the primary request builder `[CONFIRMED_STATIC]`.
- **Forensic Barrier**:  
  Vehicular testing at highway speeds was outside the bounded walking test protocol.
- **Authorized Testing Protocol to Resolve**:  
  1. Conduct a vehicular diagnostic capture while driving along a highway ($60-100\text{ km/h}$).
  2. Capture logcat output and GPS intervals over a 5-minute transit window.
  3. Verify whether `LocationRequest` is re-created with a different interval via `LocationManager.notifySyncFreqChanged`.
- **Architectural Impact for Target Development**:  
  Moderate. A 5-second interval at $100\text{ km/h}$ corresponds to $\approx 139\text{ meters}$ between fixes. While sufficient for high-level tracking, navigation-grade applications may require adaptive intervals.

---

## 3. Summary of Testing Priorities for Future Research

```text
+---------------------------------------------------------------------------------------------------+
|                                  Testing Roadmap for Remaining Unknowns                           |
+----+--------------------------------------------+----------+--------------------------------------+
| #  | Unresolved Technical Question              | Priority | Minimum Required Test Protocol       |
+----+--------------------------------------------+----------+--------------------------------------+
| 1  | Cloud Trajectory Clustering Algorithms     | HIGH     | Multi-day stationary dwell audit     |
| 2  | Dual-Device Sub-Second Delivery Latency    | HIGH     | Dual-device synchronized ADB logcat  |
| 3  | Non-GMS AOSP Native Provider Fallback      | HIGH     | De-Googled / AOSP device test        |
| 4  | SQLite Table Retention & Purge Lifespan    | MEDIUM   | Long-term (14-day) storage tracking  |
| 5  | Tencent IM Port 9971 Wire Serialization    | MEDIUM   | Wireshark capture / native disasm    |
| 6  | Aggressive OEM Task Killer Compatibility   | MEDIUM   | Multi-OEM test suite (Xiaomi/Oppo)   |
| 7  | High-Velocity (Highway) Cadence Adaptation | LOW      | Vehicular transit diagnostic capture |
+----+--------------------------------------------+----------+--------------------------------------+
```
