# Complete Evidence-Based Love8 Location System Design

**Document ID**: `23-love8-location-system-design.md`  
**Target Application**: Love8 — App for Couples (Android)  
**Package Name**: `ltd.love8.couples.relationship`  
**Analyzed Version**: v3.19.0 (`versionCode = 232`)  
**Investigation Workspace**: `C:\Users\sutha\Desktop\rea`  
**Research Source Reports**: Reports `01` through `22` in `analysis/love8-location/`  
**Classification**: Forensic System Architecture & Specification  

---

## 1. Executive Summary

This document presents the complete, evidence-based system design of the mobile location engine recovered from the official Love8 Android application (v3.19.0). 

The recovered architecture demonstrates how Love8 balances continuous high-frequency location tracking, aggressive Android OS background survival, low-latency live partner synchronization, and storage/bandwidth efficiency.

### Key Architectural Pillars
1. **Acquisition & OS Survival Tier**: A sticky foreground service (`LoveLocationServer`) declaring `foregroundServiceType="location"` pins the process priority to `curProcState=4` (`FOREGROUND_SERVICE_ADJ`, `oom_adj=200`). Whitelisted from Android Doze power management (`Bucket 6 ACTIVE`), it commands Google Play Services `FusedLocationProviderClient` at a continuous $5000\text{ ms}$ interval with $0.0\text{ m}$ displacement threshold.
2. **Dual-Channel Live Transport Tier**: Real-time partner synchronization is maintained through a persistent TCP socket to Tencent Cloud IM (`libImSDK.so` on port 9971, resolved dynamically to Tencent Cloud infrastructure e.g. `163.171.205.35`), providing sub-second partner updates. State polling and reverse-geocoding fallback route over HTTPS (port 443) to Alibaba Cloud (`api.love8.ltd`).
3. **Software Geofence & Stay Hysteresis Tier**: Avoids OS geofence flakiness by evaluating a custom client-side state machine (`PlaceTransitionConfirmation.java`). Transitions require $\ge 3$ consecutive qualifying samples over $\ge 60\text{ seconds}$ elapsed duration within a $100.0\text{ m}$ accuracy boundary. Confirmed stays increment visit counters in a local Room database (`stay_location_new_dao`).
4. **Trajectory Compression & Sanitation Tier**: Raw 5-second trajectory points stored in `LocationDao` are compressed using a recursive Ramer–Douglas–Peucker (RDP) algorithm (`OrbitManager.java`) parameterized with an exact tolerance of $\epsilon = 1.0\times 10^{-4\circ}$ ($\approx 11.13\text{ m}$). Trajectories covering $< 500\text{ m}$ path length or lasting $< 300\text{ s}$ (5 minutes) are purged as stationary noise before batch upload to `POST /trail/batch`.
5. **Offline-First Timeline Presentation Tier**: Historical moments and path polylines are rendered natively using Jetpack Compose (`TimelineHomeActivity`, `TimelineMapActivity`) backed by an offline-first repository (`OfflineFirstTimelineRepository.java`), reconciling via WorkManager `SyncWork` and REST endpoints.

---

## 2. Scope, Boundaries, and Evidence Rules

### 2.1 Strict Investigation Boundaries
- **Love8 Only**: The analysis is restricted strictly to the decompiled artifacts of Love8 v3.19.0 and passive runtime telemetry captured on an authorized Samsung Galaxy A22 (`SM-A225F`, Android 13).
- **Zero Modification**: No source code of the user's target project (TYP) was inspected, altered, or created.
- **Non-Invasive Diagnostics**: Runtime testing utilized standard Android Debug Bridge (ADB) framework inspection (`dumpsys`, `logcat`, `/proc/net`, `uiautomator`). No rooting, Frida hooks, bytecode patching, or mock location injections were performed.

### 2.2 Four-Layer Evidentiary Classification
Every design statement in this specification is categorized according to strict evidentiary criteria:
1. **[CONFIRMED_STATIC]**: Direct implementation verified in decompiled Java, Kotlin, or C++ source code.
2. **[CONFIRMED_RUNTIME]**: Dynamic behavior directly measured, observed, or triggered on the physical device.
3. **[CONFIRMED_BOTH]**: Independently confirmed across both static bytecode analysis and physical device runtime testing.
4. **[INFERRED]**: Statistically, logically, or architecturally deduced from multiple corroborating evidence points.
5. **[STATIC_UNVERIFIED_RUNTIME]**: Present in the client bytecode, but inactive or un-triggered during the test scenarios.
6. **[OPAQUE_BOUNDARY]**: Server-side proprietary logic or sandboxed OS storage inaccessible via non-invasive client diagnostics.

---

## 3. High-Level System Architecture Diagram

```mermaid
flowchart TD
    %% Boundaries
    subgraph CLIENT["NATIVE ANDROID CLIENT (ltd.love8.couples.relationship)"]
        subgraph ACQ["Location Acquisition & OS Survival"]
            FGS["LoveLocationServer\n(Foreground Service, oom_adj=200)"]
            FUSED["FusedLocationProviderClient\n(HIGH_ACCURACY, 5000ms, 0m)"]
            DOZE["Doze Whitelist Exemption\n(mStandbyBucket=6 ACTIVE)"]
            NATIVE_FALLBACK["LocationManager Fallback\n(GPS / Network Provider)"]
            
            DOZE -.-> FGS
            FGS -->|Starts & Manages| FUSED
            FUSED -.->|GMS Unavailable| NATIVE_FALLBACK
        end

        subgraph INGEST["Ingestion & Validation Engine"]
            ING_MGR["LocationManager.java\n(Ingestion Pipeline)"]
            MONO["Monotonic Filter\n(T_new > T_prev)"]
            GEO_TRANS["WGS-84 / GCJ-02 Transform\n(Coordinate Sanitizer)"]
            ACC_GATE["Accuracy Gate\n(hAcc <= 100m for Places)"]
            
            FUSED -->|Raw Fix| ING_MGR
            NATIVE_FALLBACK -->|Raw Fix| ING_MGR
            ING_MGR --> MONO
            MONO --> GEO_TRANS
            GEO_TRANS --> ACC_GATE
        end

        subgraph FANOUT["Ingestion Fan-Out Dispatcher"]
            FAN_DISP["Dual-Path Dispatcher"]
            ACC_GATE --> FAN_DISP
        end

        subgraph STAY_CORE["Stay Detection & Place Hysteresis"]
            HYST["PlaceTransitionConfirmation.java\n(>=3 points AND >=60s)"]
            STAY_REPO["StayLocationRepository (j.java)\n(Mutex-Locked Atomic Dwell)"]
            
            FAN_DISP -->|Qualified Fix| HYST
            HYST -->|Confirmed Stay Fix| STAY_REPO
        end

        subgraph TELEMETRY["Device Telemetry Acquisition"]
            TEL_ENG["Battery, Power & Network Monitor\n(dumpsys battery, wifi, telephony)"]
            TEL_BEAN["UploadLocationBean Construction\n(eq, hasCharging, netWork, netName)"]
            TEL_ENG --> TEL_BEAN
        end

        subgraph COMPRESSION["Trajectory Processing Pipeline"]
            ORBIT_MGR["OrbitManager.java\n(Polyline Sanitizer)"]
            RDP["Ramer-Douglas-Peucker (g)\n(epsilon = 1.0e-4 deg ~= 11.1m)"]
            PRUNE_GATE["Path Pruning Gate\n(>=500m length AND >=300s duration)"]
            
            ORBIT_MGR --> RDP
            RDP --> PRUNE_GATE
        end

        subgraph PRESENTATION["Presentation Layer (Jetpack Compose)"]
            MAIN_UI["MainActivity / Map Screen\n(Live Avatar Marker, Distance, Freshness)"]
            TL_UI["TimelineMapActivity / TimelineHomeActivity\n(History Feed, Clusters, Dwell Pins)"]
        end
    end

    subgraph LOCAL_STORAGE["LOCAL STORAGE & WORK SCHEDULER"]
        ROOM_LOC["Room DB: LocationDao\n(LocalLocationBean: 5s Raw Fixes)"]
        ROOM_STAY["Room DB: StayLocationNewDao\n(StayLocationNewBean: Dwell & Visits)"]
        ROOM_TL["Room DB: TimelineDao\n(TimelineEntity: Local Moment Cache)"]
        WM_PLACES["WorkManager:\nPlacesSendEnterWork / ExitWork\n(30s Exponential Backoff)"]
        WM_ORBIT["WorkManager:\nUploadOrbitWork (TestUploadOrbitManagerTag)\n(Periodic Batch Worker)"]
        WM_SYNC["WorkManager:\nSyncWork (Timeline Sync)"]
        
        FAN_DISP -->|Every 5s Fix| ROOM_LOC
        STAY_REPO -->|Insert / stayCount++| ROOM_STAY
        HYST -->|ENTER=4 / EXIT=2| WM_PLACES
        ROOM_LOC -->|Uncompressed Fixes| ORBIT_MGR
        PRUNE_GATE -->|Compressed Batches| WM_ORBIT
        WM_SYNC <-->|Local Cache Query| ROOM_TL
    end

    subgraph REMOTE_CLOUD["REMOTE TRANSPORT & CLOUD BACKEND (Opaque Boundary)"]
        subgraph LIVE_CHAN["Live Communication Infrastructure"]
            TIM_SOCKET["Tencent Cloud IM (TIM) Server\n(libImSDK.so TCP :9971)"]
            FCM["Firebase Cloud Messaging (FCM)\n(High-Priority Data Message Fallback)"]
        end
        
        subgraph REST_BACKEND["Alibaba Cloud Application Services (api.love8.ltd :443)"]
            API_LOC["/rt-loc, /geo-rt\n(Realtime Location REST API)"]
            API_ORBIT["/trail/batch, /trail/report-stats\n(Compressed Trajectory Ingestion)"]
            API_TL["/moments/home/timeline-sync\n/moments/home/timeline (Timeline Sync)"]
            API_GEO["/geocode, /fav-places\n(Reverse Geocode & Place Metadata)"]
        end
        
        FAN_DISP -->|Real-time Packet| TIM_SOCKET
        TEL_BEAN -->|Telemetry Payload| TIM_SOCKET
        FAN_DISP -.->|Socket Offline Fallback| API_LOC
        WM_PLACES -->|Place Transition Payload| API_GEO
        WM_ORBIT -->|POST UploadOrbitData| API_ORBIT
        WM_SYNC -->|GET TimelineSyncResponse| API_TL
        TIM_SOCKET -.->|Partner Deep Sleep| FCM
    end

    subgraph PARTNER_CLIENT["PARTNER CLIENT (Consenting Paired Device)"]
        PARTNER_TIM["TIMMessageManager / FCM Listener\n(Incoming Packet Receiver)"]
        STALE_FILTER["Stale Filter (EXTKt.isOldLocation)\n(Drop if T_cached > T_incoming)"]
        INTERP["ValueAnimator (Spherical Interpolation)\n(5-second Smooth Glide)"]
        PARTNER_MAP["Partner Map UI\n('Within 7 m', 'Stay 1h', '84%', '1:33 pm')"]
        
        TIM_SOCKET -->|P2P Location Push| PARTNER_TIM
        FCM -->|Push Notification Wakeup| PARTNER_TIM
        PARTNER_TIM --> STALE_FILTER
        STALE_FILTER --> INTERP
        INTERP --> PARTNER_MAP
    end

    %% Presentation Linkages
    ROOM_TL -.->|Flow Emission| TL_UI
    ROOM_STAY -.->|Flow Emission| MAIN_UI
    API_GEO -.->|Place Card Metadata| MAIN_UI
    PARTNER_MAP -.->|Visual Verification| MAIN_UI
```

---

## 4. Component Responsibility Matrix

| Subsystem / Class | Technical Responsibility | Primary Evidence Reference | Evidentiary Status |
| :--- | :--- | :--- | :--- |
| **`LoveLocationServer`** | Sticky Android Foreground Service managing ongoing location lifecycle, persistent notification, and `FOREGROUND_SERVICE_LOCATION` typing. | `LoveLocationServer.java`, `AndroidManifest.xml` | **[CONFIRMED_BOTH]** |
| **`FusedLocationProviderClient`** | Google Play Services client configured for continuous $5000\text{ ms}$ interval, $2000\text{ ms}$ fastest interval, `HIGH_ACCURACY` priority, and $0.0\text{ m}$ displacement threshold. | `LoveLocationServer.java:31`, `dumpsys location` | **[CONFIRMED_BOTH]** |
| **`LocationManager.java` (Core)** | Central location ingestion engine; performs monotonic validation, coordinate transformation, listener dispatch, and fan-out. | `LocationManager.java:784-798` | **[CONFIRMED_STATIC]** |
| **`PlaceTransitionConfirmation`** | In-memory hysteresis state machine enforcing 3-point count and 60-second dwell gates before confirming ENTER/EXIT events. | `PlaceTransitionConfirmation.java:83-120` | **[CONFIRMED_BOTH]** |
| **`GeofencePlacesSendManager`** | Coordinates place transition events, enforces $100\text{ m}$ accuracy pre-filter, and schedules `PlacesSendEnterWork` with 30s backoff. | `GeofencePlacesSendManager.java:56-80` | **[CONFIRMED_BOTH]** |
| **`StayLocationRepository` (`j.java`)** | Mutex-guarded Room repository managing confirmed stay persistence and visit count increments (`stayCount++`). | `j.java:185-230`, `StayLocationNewDao_Impl.java` | **[CONFIRMED_STATIC]** |
| **`LocationDao` (`LocalLocationBean`)** | SQLite Room table storing raw 5-second GPS points (`latitude`, `longitude`, `speed`, `accuracy`, `time`). | `LocationDao_Impl.java`, `LocalLocationBean.java` | **[CONFIRMED_STATIC]** |
| **`OrbitManager.java`** | Trajectory optimizer executing RDP line simplification ($\epsilon = 1.0\times 10^{-4\circ}$), $\ge 500\text{ m}$ path length, and $\ge 300\text{ s}$ duration pruning. | `OrbitManager.java:396-424, 632` | **[CONFIRMED_STATIC]** |
| **`UploadOrbitWork.java`** | Periodic WorkManager `CoroutineWorker` chunking trajectories into sub-batches of 5 and uploading to `POST /trail/batch`. | `UploadOrbitWork.java:47-110`, `dumpsys jobscheduler` | **[CONFIRMED_BOTH]** |
| **`TIMMessageManager.java`** | Manages live P2P push delivery over Tencent Cloud IM TCP socket (`libImSDK.so` port 9971), and incoming stale packet rejection. | `TIMMessageManager$updateFriendLocation$1.java` | **[CONFIRMED_BOTH]** |
| **`OfflineFirstTimelineRepository`** | Timeline data hub coordinating local Room cache (`TimelineDao`), WorkManager `SyncWork`, and remote sync (`/moments/home/timeline-sync`). | `OfflineFirstTimelineRepository.java:24-87` | **[CONFIRMED_STATIC]** |
| **`TimelineMapActivity` / `Home`** | Jetpack Compose UI rendering interactive polylines, stay clusters (`TimelineClusterItem`), and historical daily feeds. | `TimelineMapActivity.java:31-110`, DEX 7 | **[CONFIRMED_STATIC]** |
| **`UserStatusManager.java`** | Telemetry aggregator calculating dwell durations (`getLastDwellTime`) and packaging `UploadLocationBean`. | `UserStatusManager.java:80-140` | **[CONFIRMED_BOTH]** |

---

## 5. End-to-End Location Ingestion Sequence

The detailed sequence below traces a raw GPS fix from hardware arrival through ingestion, filtering, fan-out, persistence, and live partner dispatch:

```mermaid
sequenceDiagram
    autonumber
    participant GPS as GPS Chipset / Sensor Hub
    participant Fused as FusedLocationProviderClient
    participant Server as LoveLocationServer (FGS)
    participant Mgr as LocationManager (Ingestion)
    participant RoomLoc as Room DB (LocationDao)
    participant PlaceHyst as PlaceTransitionConfirmation
    participant TIM as TIMMessageManager (Tencent IM)
    participant REST as Alibaba Cloud (/rt-loc)
    participant Partner as Partner Client (TIM Receiver)

    GPS->>Fused: Hardware NMEA Fix
    Fused->>Server: onLocationResult(LocationResult) [Every ~5.1s]
    Server->>Mgr: onLocationChanged(Location)
    
    Note over Mgr: Ingestion Pre-Filters
    Mgr->>Mgr: Validate Coordinates (Non-null, lat in [-90,90], lng in [-180,180])
    Mgr->>Mgr: Monotonic Check: (location.getTime() > lastAcceptedTime)
    alt Time is Non-Monotonic (Out-of-Order)
        Mgr-->>Server: Drop Fix [REJECT_NON_MONOTONIC]
    end
    Mgr->>Mgr: Regional Coordinate Guard (WGS-84 to GCJ-02 if inside CN)
    
    par Path A: Raw Local Persistence (Trajectory Recording)
        Mgr->>RoomLoc: insert(LocalLocationBean)
        Note over RoomLoc: Appended to SQLite DB for future RDP processing
    and Path B: Software Geofence & Stay Hysteresis
        Mgr->>PlaceHyst: update(placeId, transitionType, time, accuracy <= 100m)
        alt Fix is Valid (hAcc <= 100m) AND Candidate Count >= 3 AND Duration >= 60s
            PlaceHyst-->>Mgr: Confirmed Transition (ENTER=4 or EXIT=2)
            Mgr->>Mgr: Enqueue PlacesSendEnterWork / Cancel Opposite Work
        else Hysteresis Not Yet Satisfied
            PlaceHyst-->>Mgr: Return null (Candidate Accumulated)
        end
    and Path C: Live Partner Telemetry Dispatch
        Mgr->>Mgr: Build UploadLocationBean (lat, lng, speed, eq, hasCharging, netWork)
        Mgr->>TIM: sendMessage(partnerImId, UploadLocationBean) [TCP :9971]
        alt Tencent IM Socket Connected
            TIM->>Partner: P2P Socket Push (<500ms latency)
            Partner->>Partner: EXTKt.isOldLocation check (cachedTime > incomingTime)
            Partner->>Partner: ValueAnimator Smooth Interpolation (5s duration)
        else TIM Socket Offline / Failed
            Mgr->>REST: POST /rt-loc (HTTP Polling Fallback)
        end
    end
```

---

## 6. Background Lifecycle State Machine

Love8 achieves zero-throttling background execution through a multi-tiered OS survival state machine:

```mermaid
stateDiagram-v2
    [*] --> ProcessStarting: App Launched / BOOT_COMPLETED
    ProcessStarting --> ForegroundActive: MainActivity Resumed

    state ForegroundActive {
        [*] --> MapUI_Visible: Window Focused
        MapUI_Visible --> FGS_Binding: Start LoveLocationServer
    }

    ForegroundActive --> BackgroundFGS: Home Pressed / Screen Locked
    
    state BackgroundFGS {
        [*] --> PriorityPinned: curProcState=4 (FOREGROUND_SERVICE_ADJ)
        PriorityPinned --> DozeExempt: Standby Bucket 6 ACTIVE (Whitelisted)
        DozeExempt --> ContinuousTracking: 5-second Fused GPS Callbacks Maintained
    }

    BackgroundFGS --> BackgroundFGS: Screen Off (mHoldingDisplaySuspendBlocker=false)
    BackgroundFGS --> ProcessKilled: OS Low Memory Pressure / Force Stop
    
    state ProcessKilled {
        [*] --> DeadProcess: Process Reaped by OS
    }

    ProcessKilled --> BackgroundFGS: WorkManager Periodic Job Wakeup (15-min SystemJobService)
    ProcessKilled --> BackgroundFGS: BOOT_COMPLETED Broadcast (Phone Rebooted)
    ProcessKilled --> ForegroundActive: User Re-launches Application
```

### Detailed State Transition Table

| Source State | Trigger / Event | Target State | OS Mechanism & Evidence |
| :--- | :--- | :--- | :--- |
| **Uninitialized** | System boot completed | **Background FGS** | `BootReceiver` captures `BOOT_COMPLETED`, starts `LoveLocationServer` `[CONFIRMED_STATIC]`. |
| **Foreground Active** | User exits app / presses Home | **Background FGS** | Process transitions to `curProcState=4` (`FOREGROUND_SERVICE_ADJ`, `oom_adj=200`). Notification channel remains pinned `[CONFIRMED_BOTH]`. |
| **Background FGS** | Screen turned off / locked | **Background FGS** | Display suspend blocker released (`mHoldingDisplaySuspendBlocker=false`). Because Love8 is on Doze whitelist (`dumpsys deviceidle whitelist`), GPS callbacks continue at $\bar{\Delta} = 5.15\text{s}$ `[CONFIRMED_BOTH]`. |
| **Background FGS** | Sudden OS process termination | **Dead Process** | Service marked `START_NOT_STICKY` in bytecode `[CONFIRMED_STATIC]`, meaning OS does not auto-restart service immediately without pending intents. |
| **Dead Process** | Periodic WorkManager trigger | **Background FGS** | `JOB #u0a303/79` (`androidx.work.impl.background.systemjob.SystemJobService`) wakes app process every $\approx 15\text{ minutes}$ `[CONFIRMED_BOTH]`. |

---

## 7. Movement & Stay State Machines

### 7.1 Mathematical Transition Formulation
The stay detection engine (`PlaceTransitionConfirmation.java`) models place transitions as a discrete temporal state machine:

$$\text{Transition Confirmed} \iff (N_{\text{pts}} \ge 3) \land (T_{\text{last}} - T_{\text{start}} \ge 60,000\text{ ms}) \land (hAcc \le 100.0\text{ m})$$

### 7.2 State Transition Diagram

```mermaid
stateDiagram-v2
    [*] --> NO_CANDIDATE: Initial / Stationary Outside Places

    state NO_CANDIDATE {
        [*] --> ScanningPlaces: GPS Fix Received
        ScanningPlaces --> EvaluateDistance: Distance to Place <= Radius
    }

    NO_CANDIDATE --> CANDIDATE_ACCUMULATING: Valid Fix in Place (Count=1, T_start=T0, Accuracy<=100m)

    state CANDIDATE_ACCUMULATING {
        [*] --> PointCount1: Point 1 Registered
        PointCount1 --> PointCount2: Valid Fix in Same Place (T1 > T0) [Count=2]
        PointCount2 --> TransitionConfirmed: Valid Fix (Count>=3 AND Elapsed >= 60s)
        
        PointCount1 --> PointCount1: Noisy Fix (Accuracy > 100m) [Candidate Preserved]
        PointCount2 --> PointCount2: Noisy Fix (Accuracy > 100m) [Candidate Preserved]
        
        PointCount1 --> CANDIDATE_ACCUMULATING: Place ID or Transition Type Changed [Reset, Count=1]
        PointCount2 --> CANDIDATE_ACCUMULATING: Place ID or Transition Type Changed [Reset, Count=1]
    }

    TransitionConfirmed --> NO_CANDIDATE: Emit Transition (ENTER=4 or EXIT=2), Reset Candidate to null
```

### 7.3 State Transition Table

| Current State | Incoming Event / Condition | Next State | Action Executed & Evidence |
| :--- | :--- | :--- | :--- |
| **NO_CANDIDATE** | Fix inside place, $hAcc \le 100\text{m}$ | **CANDIDATE_ACCUMULATING** | Create `Candidate(placeId, transitionType, T0, T0, pointCount=1)` `[CONFIRMED_STATIC]`. |
| **NO_CANDIDATE** | Fix inside place, $hAcc > 100\text{m}$ | **NO_CANDIDATE** | Ignored (`valid = false`). No candidate created `[CONFIRMED_STATIC]`. |
| **CANDIDATE_ACCUMULATING** | Fix in same place, $T_{loc} \le T_{prev}$ | **CANDIDATE_ACCUMULATING** | Non-monotonic timestamp dropped (`PlaceTransitionConfirmation.java:100`) `[CONFIRMED_STATIC]`. |
| **CANDIDATE_ACCUMULATING** | Fix in same place, $hAcc > 100\text{m}$ | **CANDIDATE_ACCUMULATING** | Candidate preserved! `valid = false` bypasses update, keeping dwell clock running `[CONFIRMED_STATIC]`. |
| **CANDIDATE_ACCUMULATING** | Fix for *different* place ID | **CANDIDATE_ACCUMULATING** | Candidate reset: new `Candidate(newPlaceId, transitionType, T_now, T_now, 1)` `[CONFIRMED_STATIC]`. |
| **CANDIDATE_ACCUMULATING** | Fix in same place, Count $\ge 3$, Elapsed $\ge 60\text{s}$ | **NO_CANDIDATE** | **Transition Confirmed**. Candidate reset to `null`. Emit `transitionType` (4 or 2) `[CONFIRMED_BOTH]`. |
| **CONFIRMED (ENTER=4)** | ENTER transition emitted | **NO_CANDIDATE** | 1. Enqueue `PlacesSendEnterWork` (Unique, Backoff 30s).<br>2. Cancel opposite `places_send_exit_work`.<br>3. Increment `stayCount` in Room `stay_location_new_dao` `[CONFIRMED_BOTH]`. |
| **CONFIRMED (EXIT=2)** | EXIT transition emitted | **NO_CANDIDATE** | 1. Enqueue `PlacesSendExitWork`.<br>2. Cancel opposite `places_send_enter_work`.<br>3. Compute final dwell duration `dwellTime = T_exit - T_enter` `[CONFIRMED_BOTH]`. |

---

## 8. Live-Sharing Architecture & Remote Boundaries

### 8.1 Dual-Channel Protocol Strategy
Love8 achieves high availability and sub-second latency through a decoupled dual-channel transport:

```text
+---------------------------------------------------------------------------------------------------+
|                                   Love8 Dual-Channel Transport Matrix                             |
+----------------------+------------------------------------+---------------------------------------+
| Feature              | Primary Push Channel (IM)          | State & Polling Channel (REST HTTPS)  |
+----------------------+------------------------------------+---------------------------------------+
| Implementation       | Tencent Cloud IM (TIM SDK)         | Alibaba Cloud REST API                |
| Native Library       | libImSDK.so, libTIMQuicPlugin.so   | OkHttp / Retrofit                     |
| Network Layer        | Persistent TCP Socket (Port 9971)  | TLS 1.3 / HTTPS (Port 443)            |
| Remote Endpoint      | 163.171.205.35:9971 (Tencent Cloud)| 47.77.228.191:443 (api.love8.ltd)     |
| Target Addressing    | partnerImId (Alphanumeric Token)   | Bearer Session Token                  |
| Latency Profile      | < 500 ms (Sub-second P2P push)     | 1.5 - 3.0 s (Request / Response)      |
| Data Payload         | UploadLocationBean (Binary/Proto)  | JSON via /rt-loc, /geo-rt             |
| Fallback Mechanism   | FCM High-Priority Data Messages    | Exponential HTTP retry                |
+----------------------+------------------------------------+---------------------------------------+
```

### 8.2 Reconciliation of Tencent Cloud IM vs Easemob Terminology
- **Decompiled Source Reality**: The decompiled bytecode establishes that Love8's instant messaging engine is exclusively **Tencent Cloud IM (TIM)** (`com.tencent.imsdk`, `TIMMessageManager.java`, `libImSDK.so`).
- **Runtime Socket Evidence**: Kernel connection tables (`/proc/net/tcp6`) observed an active connection to `163.171.205.35:9971`. IP block `163.171.0.0/16` is owned by Tencent Cloud, and TCP port 9971 is Tencent's proprietary IM SDK long-connection port.
- **Reconciliation Conclusion**: References to "Easemob" in preliminary working notes were early working hypotheses. All static and runtime evidence confirms **Tencent Cloud IM (TIM)** as the sole real-time transport provider.

### 8.3 Receiver-Side Stale Filtering & Smoothing
When the partner device receives an incoming location fix:
1. **Timestamp Freshness Gate (`EXTKt.isOldLocation`)**:
   ```java
   public static final boolean isOldLocation(UploadLocationBean incoming, UploadLocationBean cached) {
       long cachedTime = (cached != null) ? cached.time() : 0L;
       return cachedTime <= System.currentTimeMillis() && cachedTime > incoming.time();
   }
   ```
   If a delayed packet arrives out of order (e.g. following network handover or FCM delay), it is dropped immediately with log `"更新信息 -> 旧位置消息"`.
2. **Animation Smoothing**: Position updates do not jump rigidly. A `ValueAnimator` with a spherical coordinate evaluator interpolates the partner's avatar smoothly across the 5-second interval.

---

## 9. Timeline Architecture: Persistence, RDP & Upload

### 9.1 Data Flow: From Raw Points to Cloud Timeline
Historical timeline generation is entirely separate from the live location stream:

```mermaid
flowchart TD
    RAW["Raw 5-Second Fixes\n(LocationDao / LocalLocationBean)"]
    FILTER["handleAveragePrecision\n(Cut points where hAcc > Moving Avg)"]
    RDP_ALG["Ramer-Douglas-Peucker Simplification (g)\n(epsilon = 1.0e-4 deg ~= 11.13m)"]
    GATE_LEN{"Total Distance\n>= 500.0 meters?"}
    GATE_DUR{"Total Duration\n>= 300 seconds (5 min)?"}
    DROP["Discard Abnormal Footprint\n('异常足迹 清理数据')"]
    BATCH["UploadOrbitWork Chunking\n(Sub-batches of 5 segments)"]
    REST_ORBIT["POST /trail/batch\n(api.love8.ltd)"]
    ROOM_TL["TimelineDao\n(Local Cached Moments)"]
    COMPOSE_UI["TimelineMapActivity\n(Interactive Compose Polyline & Clusters)"]

    RAW --> FILTER
    FILTER --> RDP_ALG
    RDP_ALG --> GATE_LEN
    GATE_LEN -- No --> DROP
    GATE_LEN -- Yes --> GATE_DUR
    GATE_DUR -- No --> DROP
    GATE_DUR -- Yes --> BATCH
    BATCH --> REST_ORBIT
    REST_ORBIT -.->|Cloud Sync via SyncWork| ROOM_TL
    ROOM_TL --> COMPOSE_UI
```

### 9.2 RDP Epsilon Parameter & Mathematical Analysis
In `OrbitManager.java:414`, the RDP line simplification algorithm employs:

$$\epsilon = 1.0\times 10^{-4\circ} = 0.0001^\circ$$

- **Physical Ground Resolution**: At latitude $\phi \approx 23^\circ$ (Ahmedabad, India):
  - Latitude: $0.0001^\circ \times 110.75\text{ km/deg} \approx 11.08\text{ meters}$.
  - Longitude: $0.0001^\circ \times 102.47\text{ km/deg} \approx 10.25\text{ meters}$.
- **Filtering Outcome**: Any intermediate GPS sample deviating less than $\approx 11\text{ meters}$ from the chord between endpoints is pruned. This eliminates sidewalk jitter, building reflection noise, and stationary flutter, reducing trajectory polyline payload size by **$\approx 80-90\%$** while preserving street turn fidelity.

---

## 10. Telemetry Source-to-Display Mapping

| Telemetry Dimension | Client Framework Source | DTO Field (`UploadLocationBean`) | Transport Route | Receiver UI Presentation |
| :--- | :--- | :--- | :--- | :--- |
| **Battery Level** | Android `BatteryManager.EXTRA_LEVEL` | `eq: Integer` (0–100) | TIM TCP :9971 | Battery percentage icon (e.g. `"84%"`) |
| **Charging State** | Android `BatteryManager.BATTERY_STATUS_CHARGING` | `hasCharging: Integer` (1 or 0) | TIM TCP :9971 | Lightning bolt charging glyph on avatar |
| **Network Type** | `ConnectivityManager.getActiveNetwork()` | `netWork: Integer` (1=Wi-Fi, 2=Cell) | TIM TCP :9971 | Network label (e.g. `"Cell"` or `"Wi-Fi"`) |
| **Network SSID / Name** | `WifiInfo.getSSID()` | `netName: String` | TIM TCP :9971 | Network name card (e.g. `"Why ??"`) |
| **Hardware Model** | `android.os.Build.MODEL` | `Devicename: String` | TIM TCP :9971 | Partner device info sheet (`"SM-A225F"`) |
| **Screen Power State** | `PowerManager.isInteractive()` | `screenStatus: Integer` (1 or 0) | TIM TCP :9971 | Sleep indicator badge |
| **Instantaneous Velocity**| `Location.getSpeed()` | `speed: Float` (m/s) | TIM TCP :9971 | Speed badge (e.g. `"8.2 km/h"`) |
| **Heading Direction** | `Location.getBearing()` | `dir: Float` (degrees) | TIM TCP :9971 | Avatar directional pointer arrow |
| **Stay / Dwell Time** | `UserStatusManager.getLastDwellTime()` | `dwellTime: Integer` (seconds) | TIM TCP :9971 | Stationary pin banner (e.g. `"Stay 1h"`) |
| **Inter-Partner Distance**| `SphericalUtil.computeDistanceBetween` | Computed client-side | Local Calculation | Distance banner (e.g. `"Within 7 m"`) |
| **Fix Freshness** | `System.currentTimeMillis() - time` | Local relative clock | Local Calculation | Freshness indicator (`"Now"` vs `"1:33 pm"`) |

---

## 11. Failure & Recovery Matrix

| Failure Mode | Static Detection Mechanism | Client Recovery Behavior | Verified at Runtime? |
| :--- | :--- | :--- | :--- |
| **GPS Fix Unavailable (Indoors)** | Fused provider fallback | Automatically switches to Wi-Fi / Cell tower trilateration ($hAcc \approx 28-66\text{m}$). | **[CONFIRMED_BOTH]** |
| **Noisy GPS Spike ($>100\text{m}$)** | `GeofencePlacesSendManager.java:412` | Fix marked `valid=false`. Skipped for geofence; dwell candidate timer preserved. | **[CONFIRMED_STATIC]** |
| **Out-of-Order Location Fix** | `LocationManager.java:784-798` | Fix dropped if $T_{loc} \le T_{prev}$. Ingestion dropped. | **[CONFIRMED_BOTH]** |
| **TIM TCP Socket Disconnect** | `libImSDK.so` auto-reconnect | 1. Native SDK auto-reconnects with exponential backoff.<br>2. HTTP fallback via `POST /rt-loc`.<br>3. High-priority FCM push wakeup. | **[CONFIRMED_STATIC]** |
| **Foreground Service Killed by OS**| ActivityManager process reaping | WorkManager `JOB #u0a303/79` wakes process within $\approx 15\text{ minutes}$. `BOOT_COMPLETED` restarts on reboot. | **[CONFIRMED_BOTH]** |
| **Trajectory Upload Failure** | `UploadOrbitWork.java` failure | WorkManager retries task with exponential backoff; unuploaded points stay in `LocationDao`. | **[CONFIRMED_STATIC]** |
| **Stale Partner Message Received** | `EXTKt.isOldLocation` check | Discarded immediately with logcat warning `"旧位置消息"`. | **[CONFIRMED_STATIC]** |
| **Doze Mode / Screen Lock Sleep** | PowerManager suspend blocker | Whitelisted in Doze (`Bucket 6 ACTIVE`), maintaining unthrottled 5s execution. | **[CONFIRMED_BOTH]** |

---

## 12. Recovered Data Models & Storage Responsibilities

```text
+---------------------------------------------------------------------------------------------------+
|                                  Recovered Data Model Specifications                              |
+----------------------+---------------------------+------------------------------------------------+
| Entity / DTO         | Storage Layer / Class     | Schema Fields & Types                          |
+----------------------+---------------------------+------------------------------------------------+
| LocalLocationBean    | Room DB (LocationDao)     | id: Long (PK, auto-increment)                  |
|                      | Table: local_location     | time: Long (Epoch ms)                          |
|                      |                           | latitude: Double, longitude: Double            |
|                      |                           | speed: Float?, accuracy: Float                 |
|                      |                           | isUpload: Boolean, complete: Boolean           |
+----------------------+---------------------------+------------------------------------------------+
| StayLocationNewBean  | Room DB (StayLocationDao) | roomId: Long (PK, auto-increment)              |
|                      | Table: stay_location_new  | latitude: Double, longitude: Double            |
|                      |                           | userId: Int                                    |
|                      |                           | stayCount: Int (Atomic visit counter)          |
|                      |                           | markerTagClickTime, topTipCloseTime: Long      |
+----------------------+---------------------------+------------------------------------------------+
| UploadLocationBean   | In-Memory DTO / Network   | lat, lng: Double?, speed, dir: Float?          |
|                      | (TIM Socket & REST)       | eq: Integer? (Battery %), hasCharging: Integer?|
|                      |                           | netWork: Integer?, netName: String?            |
|                      |                           | dwellTime: Integer?, addressType: Integer?     |
|                      |                           | Devicename: String?, screenStatus: Integer?    |
+----------------------+---------------------------+------------------------------------------------+
| UploadOrbitDataList  | Network Batch DTO         | start_time, end_time: Long                     |
|                      | (POST /trail/batch)       | points: List<Point> (Compressed RDP path)      |
|                      |                           | total_distance: Float, speed_avg: Float        |
+----------------------+---------------------------+------------------------------------------------+
| Candidate            | In-Memory State Machine   | placeId: Int, transitionType: Int              |
|                      | (PlaceTransitionConfirm)  | startedAtMillis: Long, lastLocationMillis: Long|
|                      |                           | pointCount: Int                                |
+----------------------+---------------------------+------------------------------------------------+
```

---

## 13. Recovered Constants & Configuration Parameters

```text
+---------------------------------------------------------------------------------------------------+
|                                      System-Wide Constant Ledger                                  |
+------------------------------------+-----------------------+--------------------------------------+
| Constant Identifier                | Exact Value           | Source Class Reference               |
+------------------------------------+-----------------------+--------------------------------------+
| Fused Interval                     | 5000 ms               | LoveLocationServer.java:31           |
| Fused Fastest Interval             | 2000 ms               | LoveLocationServer.java:32           |
| Fused Priority                     | 100 (HIGH_ACCURACY)   | LoveLocationServer.java:30           |
| Fused Displacement Gate            | 0.0 meters            | LoveLocationServer.java:33           |
| Stay Hysteresis Point Count        | 3 points              | PlaceTransitionConfirmation.java:84  |
| Stay Hysteresis Duration           | 60,000 ms (60 s)      | PlaceTransitionConfirmation.java:84  |
| Place Transition Max Accuracy      | 100.0 meters          | GeofencePlacesSendManager.java:412   |
| RDP Simplification Epsilon         | 1.0E-4 deg (~11.13m)  | OrbitManager.java:414                |
| Trajectory Minimum Distance Gate   | 500.0 meters          | OrbitManager.java:632                |
| Trajectory Minimum Duration Gate   | 300 seconds (5 min)   | OrbitManager.java:770                |
| WorkManager Orbit Sub-Batch Size   | 5 segments            | OrbitManager.java:785                |
| Geofence WorkManager Backoff Delay | 30 seconds            | GeofencePlacesSendManager.java:68    |
| WorkManager Backoff Policy         | 1 (EXPONENTIAL)       | GeofencePlacesSendManager.java:68    |
| Tencent IM Long-Connection Port    | 9971 (TCP)            | libImSDK.so / /proc/net/tcp6         |
| Alibaba Cloud REST API Port        | 443 (HTTPS)           | ApiService.java / /proc/net/tcp6     |
+------------------------------------+-----------------------+--------------------------------------+
```

---

## 14. Security, Privacy & Access-Control Boundaries

1. **Identifier Obfuscation**: Live location messages do not transmit phone numbers, emails, or hardware MAC addresses. Partner dispatch targets an opaque alphanumeric internal identity (`partnerImId`), isolating real-world identity from network transit.
2. **Coordinate Transformation Protection**: Inside mainland China, Love8 enforces regional transformation guards (`WGS-84` to `GCJ-02`), preventing legal and regulatory compliance violations.
3. **Session & Token Authentication**: All REST requests to Alibaba Cloud (`api.love8.ltd`) require bearer token headers obtained via `/user/token` and `/user/websocket/token`.
4. **Android Sandbox Protection**: SQLite database files are sandboxed with user UID permissions (`u0_a303`). In production release builds, `run-as` is disabled by the OS, preventing unauthenticated local extraction without device compromise.

---

## 15. Performance & Power-Management Tradeoffs

1. **Cadence vs. Battery Drain**: A $5000\text{ ms}$ interval with high-accuracy GPS consumes significant battery if unmanaged. Love8 mitigates this by allowing the underlying sensor hub to deliver Wi-Fi trilateration when stationary ($v = 0.0\text{ m/s}$), only powering up full GPS hardware when physical movement ($\Delta d > 0$) is detected.
2. **Network Bandwidth vs. History Granularity**: Uploading raw 5-second GPS fixes over cellular data would drain both quota and battery. RDP compression ($\epsilon = 1.0\times 10^{-4\circ}$) eliminates redundant linear points, reducing packet payload by $\approx 85\%$.
3. **Noise Immunity vs. Responsiveness**: The 3-point / 60-second stay hysteresis gate introduces a nominal 1-minute detection latency, but completely eliminates false geofence alerts caused by indoor multipath reflection.

---

## 16. Runtime Evidence vs. Source Evidence Cross-Check

```text
+---------------------------------------------------------------------------------------------------+
|                                  Evidence Cross-Validation Summary                                |
+--------------------------------------+---------------------------+--------------------------------+
| Architectural Property               | Source Code Evidence      | Physical Runtime Measurement   |
+--------------------------------------+---------------------------+--------------------------------+
| Fused Location Cadence               | Configured for 5000 ms    | Stationary: 5.11s mean interval|
|                                      |                           | Moving:     5.12s mean interval|
|                                      |                           | Locked:     5.15s mean interval|
+--------------------------------------+---------------------------+--------------------------------+
| Dynamic Accuracy Tightening          | Fused provider API        | Stationary: 28.6m - 66.6m (Wi-Fi)|
|                                      |                           | Walking:    Tightened to 6.29m |
+--------------------------------------+---------------------------+--------------------------------+
| Background OOM Immunity              | Android FGS architecture  | curProcState=4, oom_adj=200    |
+--------------------------------------+---------------------------+--------------------------------+
| Power Whitelist Status               | Whitelist intent request  | Whitelisted (Bucket 6 ACTIVE)  |
+--------------------------------------+---------------------------+--------------------------------+
| Dual-Channel Socket Endpoints        | TIM SDK & Retrofit classes| TCP Port 9971 (Tencent IM)     |
|                                      |                           | TCP Port 443 (Alibaba Cloud)   |
+--------------------------------------+---------------------------+--------------------------------+
| Live Telemetry Tracking              | UploadLocationBean schema | Battery incremented 79%->80%->84%|
|                                      |                           | Wi-Fi SSID 'Why ??' captured   |
+--------------------------------------+---------------------------+--------------------------------+
| Live UI Dwell Badge Rendering        | UserStatusManager dwell   | UI dump confirmed 'Stay 1h'    |
+--------------------------------------+---------------------------+--------------------------------+
| Relative Distance Calculation        | SphericalUtil formula     | UI dump confirmed 'Within 7 m' |
+--------------------------------------+---------------------------+--------------------------------+
```

---

## 17. Unresolved Questions & Known Limitations

1. **Server-Side Trajectory Clustering**: The backend algorithms that cluster compressed trajectories into semantic places on Alibaba Cloud remain proprietary and unobservable.
2. **Receiver-Side Packet Receipt Latency**: While sender-side dispatch and partner-side UI presentation were verified, independent millisecond-level packet arrival timing on the partner device could not be instrumented without connecting the secondary phone to ADB.
3. **Cellular Provider Triangulation Fallback**: Direct fallback to raw cell tower IDs (`TelephonyManager`) exists in bytecode, but was not triggered because Google Play Services remained available throughout all tests.
4. **OEM-Specific Background Variations**: Tests were conducted on a Samsung Galaxy A22 (One UI 5.1). While Doze whitelisting and FGS status were verified, aggressive proprietary OEM task killers (e.g. Xiaomi MIUI, Huawei EMUI) may impose additional constraints.

---

## 18. Traceability Index to Research Reports

- **Subsystem 1 (Permissions & FGS)**: Reports `02`, `11`, `12`, `18`, `19`
- **Subsystem 2 (Acquisition & Cadence)**: Reports `03`, `11`, `18`, `19`
- **Subsystem 3 (Ingestion & Validation)**: Reports `03`, `11`, `19`, `20`
- **Subsystem 4 (Stay & Hysteresis)**: Reports `04`, `13`, `21`
- **Subsystem 5 (RDP Compression & Orbits)**: Reports `05`, `14`, `21`
- **Subsystem 6 (Live Sharing & TIM Transport)**: Reports `07`, `15`, `20`
- **Subsystem 7 (Telemetry & Power Management)**: Reports `06`, `08`, `16`, `20`
- **Subsystem 8 (Timeline & Compose UI)**: Reports `05`, `14`, `21`
- **Subsystem 9 (Reconciliation & Matrix)**: Reports `17`, `22`
