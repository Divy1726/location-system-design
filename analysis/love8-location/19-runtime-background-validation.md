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

*(Part 2: Phase 2B Movement Test and Part 3: Phase 3 Background Validation will be appended below upon test execution.)*
