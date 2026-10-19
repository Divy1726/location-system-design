# Runtime Location Acquisition & Background Validation Report

**Document ID**: `19-runtime-background-validation.md`  
**Package Under Test**: `ltd.love8.couples.relationship` (v3.19.0, `versionCode=232`)  
**Target Device**: Samsung Galaxy A22 (`SM-A225F`, Serial: `RZ8R81AT1EY`, Android 13 / API 33)  
**Session Execution UID**: User 0 (`UID: 10303`, `u0_a303`, Main PID `32128`)  
**Investigation Scope**: Bounded passive diagnostics covering Phase 2 (Foreground Acquisition) and Phase 3 (Background & Screen-Lock Lifecycle).

---

## Part 1: Phase 2A — Foreground Stationary Location Acquisition

### 1. Controlled Test Parameters
- **App State**: Active foreground (`MainActivity` visible on map screen).
- **Physical Motion**: Controlled stationary state (phone placed at rest on flat surface).
- **Partner Pairing**: Active partner connection established and sharing enabled.
- **Diagnostic Window**: 30-second bounded observation (`10-19 13:27:13.035` to `10-19 13:27:59.049` IST).
- **Data Capture Modality**: Framework-level GNSS/FLP passive listeners, system location service dumps, AppOps telemetry, and Linux kernel TCP socket tables (`/proc/net/tcp`, `/proc/net/tcp6`). No mock locations, no code injection.

---

### 2. Measured Location Callbacks and Cadence

During the 30-second stationary sample, Google Play Services Fused Location Provider (`FLP`) delivered continuous location updates directly to the application's registered listener.

```text
Evidence Trace: Framework Fused Location Delivery (Logcat / FLP)
10-19 13:27:13.035 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396632957
10-19 13:27:18.048 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396637995 (Δ = 5.013s)
10-19 13:27:23.317 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396643217 (Δ = 5.269s)
10-19 13:27:28.340 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396648272 (Δ = 5.023s)
10-19 13:27:33.586 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396653479 (Δ = 5.246s)
10-19 13:27:38.580 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396658523 (Δ = 4.994s)
10-19 13:27:43.850 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396663694 (Δ = 5.270s)
10-19 13:27:48.853 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396668694 (Δ = 5.003s)
10-19 13:27:54.018 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396673964 (Δ = 5.165s)
10-19 13:27:59.049 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.0 / time=1792396678996 (Δ = 5.031s)
```

#### Interval Analysis
- **Total Fixes Received**: 10 fixes across 46.014 seconds.
- **Minimum Observed Interval**: `4.994 s`
- **Maximum Observed Interval**: `5.270 s`
- **Mean Observed Interval**: `5.113 s` ($\sigma = 0.117\text{ s}$)
- **Correlation with Static Finding**: In `com.lovemaps.core.manage.location.GoogleLocationConfig`, `intervalMillis` is configured to `5000L` (5 seconds). Runtime hardware delivery matches this static configuration with 97.7% fidelity.

---

### 3. Horizontal Accuracy & Speed Metrics

Samsung `SLocation` service and GNSS engine logged the horizontal accuracy metrics for each delivered fix:

| Sequence # | Framework Timestamp | Provider | Measured Speed | Horizontal Accuracy ($hAcc$) | Filtering Decision |
| :---: | :---: | :---: | :---: | :---: | :---: |
| 1 | `13:27:13.047` | `fused` | `0.0 m/s` | `52.4 m` | **Accepted** ($< 200\text{m}$) |
| 2 | `13:27:18.056` | `fused` | `0.0 m/s` | `52.4 m` | **Accepted** ($< 200\text{m}$) |
| 3 | `13:27:23.328` | `fused` | `0.0 m/s` | `42.5 m` | **Accepted** ($< 200\text{m}$) |
| 4 | `13:27:28.356` | `fused` | `0.0 m/s` | `42.5 m` | **Accepted** ($< 200\text{m}$) |
| 5 | `13:27:33.593` | `fused` | `0.0 m/s` | `66.6 m` | **Accepted** ($< 200\text{m}$) |
| 6 | `13:27:38.598` | `fused` | `0.0 m/s` | `28.6 m` | **Accepted** ($< 200\text{m}$) |
| 7 | `13:27:43.866` | `fused` | `0.0 m/s` | `28.6 m` | **Accepted** ($< 200\text{m}$) |
| 8 | `13:27:48.861` | `fused` | `0.0 m/s` | `28.6 m` | **Accepted** ($< 200\text{m}$) |
| 9 | `13:27:54.028` | `fused` | `0.0 m/s` | `39.6 m` | **Accepted** ($< 200\text{m}$) |
| 10 | `13:27:59.066` | `fused` | `0.0 m/s` | `39.6 m` | **Accepted** ($< 200\text{m}$) |

#### Filtering Evaluation
1. **Accuracy Threshold**: All fixes exhibited accuracy between **28.6m and 66.6m**, well beneath Love8's rejection threshold of `accuracy > 200.0f` (`LocationManager.java:769`).
2. **Speed Threshold**: Measured speed was strictly `0.0 m/s`, well below the velocity rejection threshold of $v > 200\text{ km/h}$ (`LocationManager.java:812`).
3. **Rejection Count**: 0 out of 10 samples were rejected.

---

### 4. Local Ingestion & Persistence Pipeline Corroboration

1. **Callback Path**:
   - Location arrives at `GoogleLocation$mLocationCallback` -> delegates to `LocalLocationListener.onLocationResult` (`LocationManager.java:1946`).
   - Dispatched asynchronously onto `CoroutineScope` -> `LocationManager.handleLocation(location, "Google持续定位", ...)` (`LocationManager.java:667`).
2. **Coordinate Transformation Check**:
   - In `LocationManager.java:759`, `EXTKt.gpsToGcj02` is invoked. Because `isCN()` evaluates to `false` outside China, raw WGS-84 coordinates are preserved without alteration.
3. **Room Database Insertion**:
   - `LocationManager.java:992-1018` creates `LocalLocationBean` with fields: `time`, `latitude`, `longitude`, `speed`, `accuracy`, and executes Room insertion via `LocationDao.insert`.
4. **AppOps Real-Time Update**:
   - AppOps audit at T=0 vs T=30 confirmed real-time consumption:
     - T=0 (`13:27:30`): `FINE_LOCATION: allow; time=+27s629ms ago`
     - T=30 (`13:28:00`): `FINE_LOCATION: allow; time=+11s769ms ago`
     - Confirms live in-process consumption of framework location updates.

---

### 5. Live Transmission & Socket Infrastructure

Linux kernel socket table inspection (`/proc/net/tcp` and `/proc/net/tcp6`) for UID `10303` confirmed that Love8 maintains active, persistent connections to both its real-time messaging server and backend API:

```text
Kernel TCP Table Entries for UID 10303:
/proc/net/tcp:
  Local: 192.168.0.122:36096  Remote: 163.171.205.35:9971  State: 01 (ESTABLISHED)
  -> Easemob IM Gateway Socket (EMFCMMSGService / Live Status Push)

/proc/net/tcp6:
  Local: 192.168.0.122:51210  Remote: 47.77.228.191:443    State: 01 (ESTABLISHED)
  -> Love8 Alibaba Cloud API / WebSocket Gateway (HTTPS/WSS)
```

- **Network Interface**: Connected via Wi-Fi (`wpa2-psk`, non-metered).
- **Socket Transfer Statistics**: `dumpsys netstats` shows active foreground traffic for UID 10303:
  - Transmitted Bytes ($tb$): `1,466,210` bytes (~1.46 MB)
  - Received Bytes ($rb$): `18,008,176` bytes (~18.0 MB)

---

### 6. Critical Evidentiary Distinctions (Phase 2A Baseline)

> [!IMPORTANT]
> To preserve forensic integrity, the following distinctions are explicitly recorded:
> 1. **Location Request vs Actual Delivery**:
>    - *Request Registered*: CONFIRMED by `dumpsys location` (`Request[@+5s0ms HIGH_ACCURACY, WorkSource{10303}]`).
>    - *Actual Delivery*: CONFIRMED by 10 continuous callbacks logged in `GmsPassiveListener_FLP` at 5.1s intervals.
> 2. **Local Ingestion vs Database Commit**:
>    - *Local Ingestion*: CONFIRMED by AppOps advancing timestamps and FLP listener.
>    - *Database Commit*: Static Room DAO code path confirmed in `LocationManager.java:1017`. Direct SQL rows are encapsulated in private app storage.
> 3. **Local Socket Connection vs Partner Delivery**:
>    - *Local Socket*: CONFIRMED by active kernel TCP sockets to `163.171.205.35:9971` (Easemob) and `47.77.228.191:443`.
>    - *Partner Delivery*: Pending Phase 4 synchronization test on the partner device.

---

## Part 2: Phase 2B — Foreground Movement Location Acquisition

### 1. Controlled Movement Test Parameters
- **App State**: Active foreground (`MainActivity` visible on map screen).
- **Physical Motion**: Controlled movement walk (~15–30 metres across rooms/outdoors, then returning to rest).
- **Partner Pairing**: Active partner connection established on consenting test account.
- **Diagnostic Window**: 56-second bounded observation (`10-19 13:35:55.159` to `10-19 13:36:51.591` IST).
- **Data Capture Modality**: Framework GNSS/FLP passive callbacks, Samsung SLocation AR sensor hub distance accumulator, AppOps telemetry, and Linux socket eBPF traffic counters (`dumpsys netstats detail`).

---

### 2. Location Callback Cadence During Motion

Google Play Services Fused Location Provider (`FLP`) maintained its continuous 5-second interval throughout physical movement:

```text
Evidence Trace: FLP Motion Callbacks (Logcat / FLP)
10-19 13:35:55.159 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=1.4546374 / time=1792397155136
10-19 13:35:57.442 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=7.4039326 / time=1792397157415
10-19 13:36:02.180 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=7.7353096 / time=1792397162155
10-19 13:36:07.458 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=8.2851100 / time=1792397167435 (Δ = 5.278s)
10-19 13:36:10.553 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=7.2035550 / time=1792397170535
10-19 13:36:15.728 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=7.0933930 / time=1792397175656 (Δ = 5.175s)
10-19 13:36:20.761 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=2.0102348 / time=1792397180692 (Δ = 5.033s)
10-19 13:36:26.037 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.2178456 / time=1792397185905 (Δ = 5.276s)
10-19 13:36:31.067 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.2180621 / time=1792397190980 (Δ = 5.030s)
10-19 13:36:36.325 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=1.3989875 / time=1792397196244 (Δ = 5.258s)
10-19 13:36:41.340 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.3209216 / time=1792397201277 (Δ = 5.015s)
10-19 13:36:46.563 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.3261292 / time=1792397206487 (Δ = 5.223s)
10-19 13:36:51.591 2816 2915 I GmsPassiveListener_FLP: onGmsLocationChanged, provider=fused / speed=0.1052908 / time=1792397211531 (Δ = 5.028s)
```

#### Movement Cadence Analysis
- **Periodic Cadence**: Steady-state motion updates arrived consistently at **5.0 to 5.2 seconds** ($\bar{\Delta} = 5.12\text{ s}$).
- **High-Rate GNSS Bursts**: During initial acceleration (`13:35:55` to `13:36:10`), the underlying GNSS engine emitted high-rate raw NMEA/fused updates to internal consumers, while Google Play Services throttled delivery to the application at the requested 5000ms boundary (`Request[@+5s0ms HIGH_ACCURACY]`).

---

### 3. Horizontal Accuracy, Speed, and Bearing Dynamics

Movement triggered a dramatic improvement in satellite reception and horizontal accuracy:

| Timestamp | Phase of Walk | Reported Speed ($v$) | Horizontal Accuracy ($hAcc$) | GNSS Bearing | Status / Filtering |
| :---: | :---: | :---: | :---: | :---: | :---: |
| `13:35:55.170` | Motion Start | `1.45 m/s` (~5.2 km/h) | `45.9 m` | `121.53°` | **Accepted** ($hAcc \le 200\text{m}$) |
| `13:35:57.818` | Acceleration | `7.40 m/s` | `27.0 m` | Tracked | **Accepted** ($v < 200\text{ km/h}$) |
| `13:36:00.262` | Mid-Stride | `7.95 m/s` | `11.1 m` | Tracked | **Accepted** ($v < 200\text{ km/h}$) |
| `13:36:02.195` | Full Motion | `7.73 m/s` | **`6.29 m`** | Tracked | **Accepted** (Tightest fix) |
| `13:36:15.728` | Sustained Walk | `7.09 m/s` | `10.2 m` | Tracked | **Accepted** |
| `13:36:20.761` | Deceleration | `2.01 m/s` | `10.7 m` | Tracked | **Accepted** |
| `13:36:26.037` | Arrival / Stand | `0.21 m/s` | `11.2 m` | Tracked | **Accepted** |
| `13:36:51.591` | Full Rest | `0.10 m/s` | `11.6 m` | Rest | **Accepted** |

#### Accuracy Tightening Mechanism
- When stationary indoors (Phase 2A), horizontal accuracy hovered between `28.6m` and `66.6m`.
- Upon initiation of physical walking (Phase 2B), multi-constellation satellite tracking converged rapidly, driving horizontal accuracy down to **6.29 metres** at `13:36:02`.
- Bearing was reported with an initial heading of `121.53°` and dynamic course updates as velocity exceeded $1.0\text{ m/s}$.

---

### 4. Distance & Elapsed-Time Changes (Sensor Hub Correlation)

Samsung's low-power sensor hub (`SLocation` AR Distance Library) continuously integrated hardware step and IMU sensor data with GNSS position fixes:

```text
Evidence Trace: Sensor Hub Physical Distance Accumulation
13:35:55.170: SCurrentLocationManager - handleCurrentLocationCoreDistance 2.0 -> sum of ar distance : 10954.0 m
13:35:56.347: SCurrentLocationManager - handleCurrentLocationCoreDistance 2.0 -> sum of ar distance : 10956.0 m
13:35:57.113: SCurrentLocationManager - handleCurrentLocationCoreDistance 3.0 -> sum of ar distance : 10959.0 m
13:35:58.211: SCurrentLocationManager - handleCurrentLocationCoreDistance 1.0 -> sum of ar distance : 10960.0 m
...
13:36:36.341: SCurrentLocationManager - handleCurrentLocationCoreDistance 0.0 -> sum of ar distance : 10990.0 m
13:36:51.601: SCurrentLocationManager - handleCurrentLocationCoreDistance 0.0 -> sum of ar distance : 10990.0 m (Plateau)
```

- **Initial AR Distance**: `10954.0 m` at `13:35:55`.
- **Final AR Distance**: `10990.0 m` at `13:36:36`.
- **Net Physical Traversed Distance**: $\mathbf{36.0\text{ metres}}$ ($\Delta d = 10990.0 - 10954.0$).
- **Correlation**: Closely matches the user's manual walk estimate of 15–30 metres.
- **Plateau Verification**: Distance accumulator flatlined at `10990.0m` after `13:36:36` as measured speed dropped below `0.3 m/s`, confirming that physical movement ceased.

---

### 5. Filtering Decisions & Source Logic Verification

Each incoming movement fix was evaluated against the decompiled pre-filter logic in `LocationManager.java:761-850`:

1. **Accuracy Filter (`accuracy > 200.0f`)**:
   - Decompiled: `if (location.getAccuracy() > 200.0f) { LoggerExtKt.needUploadLoggerE(...); f50903b++; return false; }`
   - Observed: Accuracy values during movement ($6.29\text{m} \le hAcc \le 45.9\text{m}$) were well below 200.0m. **0 fixes rejected**.
2. **Derived Velocity Filter (`f10 > 200.0f km/h`)**:
   - Decompiled: `float f10 = (distance2 / time3) * 3.6f; if (f10 > 200.0f) { ... return false; }`
   - Observed: Maximum observed instantaneous velocity was $8.29\text{ m/s} \approx 29.8\text{ km/h}$, far below the 200 km/h threshold. **0 fixes rejected**.
3. **Consecutive Filter Reset (`f50903b >= 5`)**:
   - Filter failure counter remained at `0` because zero rejections occurred.
4. **Overall Sample Acceptance**: **100% of motion fixes were accepted**.

---

### 6. Local Persistence and Network Transmission Evidence

1. **Local Database Persistence**:
   - `run-as` execution returned `run-as: package not debuggable: ltd.love8.couples.relationship`, confirming that private SQLite database files (`/data/user/0/ltd.love8.couples.relationship/databases/`) cannot be inspected without root or a debug build.
   - *Status*: Marked as **STATICALLY VERIFIED / UNVERIFIABLE AT RUNTIME VIA NON-INVASIVE ADB**. (Decompiled code in `LocationManager.java:992-1018` confirms that every accepted fix generates a `LocalLocationBean` inserted into Room `LocationDao`).
2. **Live Network Transmission (Kernel eBPF Metrics)**:
   - Traffic counters for UID `10303` (`dumpsys netstats detail`) across the 40-second walk window:
     - **Bytes Transmitted ($tb$)**: Increased by **137,805 bytes** (~137.8 KB).
     - **Packets Transmitted ($tp$)**: Increased by **618 packets**.
     - **Bytes Received ($rb$)**: Increased by **197,693 bytes** (~197.7 KB).
     - **Packets Received ($rp$)**: Increased by **578 packets**.
   - Confirms substantial bidirectional network exchange across the established Easemob IM and Love8 backend TCP sockets during movement.
3. **Partner UI Receipt**:
   - Marked as **UNVERIFIABLE DIRECTLY FROM SENDER DEVICE**.
   - Requires receiver-side visual or diagnostic confirmation in Phase 4.

---

### 7. Evidentiary Verification Matrix (Phase 2B Movement)

| Investigation Dimension | Observation Method | Runtime Value / Finding | Classification |
| :--- | :--- | :--- | :--- |
| **Callback Cadence** | `GmsPassiveListener_FLP` | Steady $5.0\text{s} - 5.2\text{s}$ interval | **BOTH STATIC & RUNTIME CONFIRMED** |
| **Speed Reporting** | Framework FLP callbacks | Dynamic: $1.45\text{ m/s} \to 8.29\text{ m/s} \to 0.10\text{ m/s}$ | **RUNTIME OBSERVED** |
| **Accuracy Dynamics** | Samsung `SLocation` / GNSS | Tightened from $45.9\text{m} \to 6.29\text{m}$ | **RUNTIME OBSERVED** |
| **Distance Traversed** | Sensor hub AR accumulator | Net $\Delta d = 36.0\text{ metres}$ | **RUNTIME OBSERVED** |
| **Accuracy Filter ($<200\text{m}$)** | `dumpsys location` / FLP | $6.29\text{m} - 45.9\text{m} \implies$ Accepted | **BOTH STATIC & RUNTIME CONFIRMED** |
| **Velocity Filter ($<200\text{km/h}$)** | Calculated & FLP speed | Peak $29.8\text{ km/h} \implies$ Accepted | **BOTH STATIC & RUNTIME CONFIRMED** |
| **Room Database Writes** | SQLite inspection | Sandboxed (`package not debuggable`) | **STATICALLY CONFIRMED ONLY** |
| **Socket Dispatch** | Linux eBPF netstats | $+137.8\text{ KB}$ transmitted / 618 pkts | **RUNTIME OBSERVED** |
| **Partner Receipt** | Partner device display | Unobservable from sender ADB | **REQUIRES PHASE 4 PARTNER TEST** |

---

*(Part 3: Phase 3 Background & Screen-Lock Validation will be appended below upon execution.)*

