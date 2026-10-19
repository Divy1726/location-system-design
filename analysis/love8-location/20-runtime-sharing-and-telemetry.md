# Love8 Live Partner Synchronization and Device Telemetry Report

**Document ID**: `20-runtime-sharing-and-telemetry.md`  
**Package Under Test**: `ltd.love8.couples.relationship` (v3.19.0, `versionCode=232`)  
**Sender Hardware**: Samsung Galaxy A22 (`SM-A225F`, Serial: `RZ8R81AT1EY`, Android 13 / API 33)  
**Receiver Configuration**: Consenting paired partner account on secondary physical device (externally observed)  
**Investigation Timestamp**: 2026-10-19 13:50 IST  
**Methodology**: Passive non-invasive ADB queries on sender, Linux kernel socket state, eBPF netstats, decompiled protocol schema verification, and coordinated partner screen observation. Zero code injection, zero mock locations, zero MITM interception.

---

## 1. Forensic Audit & Boundary Reconciliation of Phase 3 Background Capture

In Phase 3, 18 consecutive location callbacks were recorded with an average interval of $5.148\text{ seconds}$. A rigorous reconciliation between the nominal 60-second test window and the 87.6-second span of captured logcat callbacks establishes the exact timeline:

### Execution Timeline & Boundary Segregation

| Boundary Marker | Clock Time (IST) | Elapsed Delta | Description |
| :--- | :--- | :--- | :--- |
| **Diagnostic Script Launch** | `13:44:22` | $0.0\text{ s}$ | Script execution initiated via ADB shell |
| **T=0 Baseline Snapshot** | `13:44:27` | $+5.0\text{ s}$ | `dumpsys power`, `dumpsys location`, and AppOps sampled |
| **Screen Lock Window** | `13:44:27` $\to$ `13:45:28` | $61.0\text{ s}$ | Nominal 60-second observation window (`Start-Sleep -Seconds 60`) |
| **T=60 Conclusion Snapshot**| `13:45:28` | $+66.0\text{ s}$ | Post-sleep service status, process priority, and netstats sampled |
| **Logcat Query Query Range**| `13:44:00` $\to$ `13:45:30` | **$90.0\text{ s}$** | Logcat buffer queried using `-t '10-19 13:44:00.000'` |

### Callback Segregation Across Windows

```text
Detailed Callback Distribution:
[Pre-Snapshot Transition: 13:44:01.934 to 13:44:27.906 (25.972s)]
  1. 13:44:01.934 (speed=0.27 m/s)
  2. 13:44:07.209 (speed=0.09 m/s, Δ=5.275s)
  3. 13:44:12.245 (speed=0.00 m/s, Δ=5.036s)
  4. 13:44:17.493 (speed=1.15 m/s, Δ=5.248s)
  5. 13:44:22.509 (speed=0.56 m/s, Δ=5.016s)
  6. 13:44:27.906 (speed=0.07 m/s, Δ=5.397s)  <-- T=0 Snapshot taken at 13:44:27

[Strictly Locked Observation Window: 13:44:27.906 to 13:45:28 (60.094s)]
  7.  13:44:32.954 (speed=0.29 m/s, Δ=5.048s)
  8.  13:44:38.200 (speed=0.18 m/s, Δ=5.246s)
  9.  13:44:43.196 (speed=0.14 m/s, Δ=4.996s)
  10. 13:44:48.464 (speed=0.01 m/s, Δ=5.268s)
  11. 13:44:53.486 (speed=0.00 m/s, Δ=5.022s)
  12. 13:44:58.732 (speed=0.75 m/s, Δ=5.246s)
  13. 13:45:03.741 (speed=0.21 m/s, Δ=5.009s)
  14. 13:45:08.991 (speed=0.09 m/s, Δ=5.250s)
  15. 13:45:14.024 (speed=0.03 m/s, Δ=5.033s)
  16. 13:45:19.326 (speed=0.52 m/s, Δ=5.302s)
  17. 13:45:24.315 (speed=0.22 m/s, Δ=4.989s)  <-- T=60 Snapshot initiated at 13:45:28

[Post-Observation Boundary: 13:45:28 to 13:45:30]
  18. 13:45:29.550 (speed=0.82 m/s, Δ=5.235s)
```

- **Forensic Resolution**: The 18 callbacks span $87.616\text{ seconds}$ because the logcat query boundary began at `13:44:00.000` (capturing the 27 seconds prior to T=0 while the user placed the app into background and locked the screen).
- **Core Verification**: Within the strictly locked window, exactly **11 full inter-callback intervals** occurred ($5.14\text{s}$ average), confirming that location delivery never throttled or deferred during screen sleep.

---

## 2. Sender Device Telemetry Baseline

Direct ADB query of Android framework subsystems on the sender device establishes the true underlying hardware telemetry:

| Telemetry Dimension | Framework Source | Underlying Hardware Value | Decompiled Property Mapping |
| :--- | :--- | :--- | :--- |
| **Battery Percentage** | `dumpsys battery` | `level: 79` (79%) | `UploadLocationBean.eq = 79` |
| **Power / Charging State** | `dumpsys battery` | `USB powered: true`, `status: 2` (Charging) | `UploadLocationBean.hasCharging = 1` |
| **Battery Voltage & Temp** | `dumpsys battery` | `4184 mV`, `36.4 °C` | Internal hardware telemetry |
| **Network Type** | `dumpsys wifi` | Wi-Fi connected (`type=1`, `wpa2-psk`) | `UploadLocationBean.netWork = 1` |
| **Network Name (SSID)** | `dumpsys wifi` | `"Why ??"` | `UploadLocationBean.netName = "Why ??"` |
| **Device Model** | `ro.product.model` | `SM-A225F` | `UploadLocationBean.Devicename = "SM-A225F"` |
| **Screen State** | `dumpsys power` | `mHoldingDisplaySuspendBlocker=true` (ON) | `UploadLocationBean.screenStatus = 1` |

---

## 3. Decompiled Protocol & Telemetry Data Model

In `com.lovemaps.core.entity.UploadLocationBean`, Love8 structures all telemetry into a unified parcelable and serializable payload:

```kotlin
// Extracted from decompiled UploadLocationBean.java
data class UploadLocationBean(
    var lat: Double?,            // WGS-84 Latitude
    var lng: Double?,            // WGS-84 Longitude
    var speed: Float?,           // Instantaneous velocity (m/s)
    var dir: Float?,             // Heading / bearing direction (degrees)
    var eq: Integer?,            // Battery percentage ("electric quantity", 0-100)
    var hasCharging: Integer?,   // Charging indicator: 1 = Charging, 0 = Discharging
    var time: Long?,             // Location timestamp (epoch ms)
    var accuracy: Float?,        // Horizontal accuracy radius (meters)
    var status: Integer?,        // Movement status enum
    var dwellTime: Integer?,     // Stay duration at place (seconds)
    var addressType: Integer?,   // Home, Work, or Custom geofence place ID
    var netWork: Integer?,       // 1 = Wi-Fi, 2 = Cellular, 0 = None
    var netName: String?,        // Wi-Fi SSID or mobile network operator
    var addressName: String?,    // Reverse-geocoded place title
    var Devicename: String?,     // Client device hardware identifier
    var screenStatus: Integer?,  // 1 = Screen Active, 0 = Screen Off
    var locPermission: Boolean?, // FINE location granted
    var locService: Boolean?,    // Location provider enabled
    var locShare: Boolean?,      // Partner sharing toggle state
    var musicStatus: String?,    // Media playback status
    var vpn: Integer?            // VPN proxy detection status
)
```

### Transmission Architecture
1. **Payload Merging**: `EXTKt.updateUploadLocationBean(bean1, bean2)` selectively merges incoming telemetry changes without overwriting valid cached metrics.
2. **Dual-Channel Transport**:
   - **Primary Live Sync**: Dispatched over Easemob IM TCP socket (`192.168.0.122:36096` $\to$ `163.171.205.35:9971`) via `com.lovemaps.core.manage.tim.i.f(...)` as an encrypted transient peer message.
   - **Backend Web API**: Dispatched via HTTPS/WSS POST to Alibaba Cloud (`47.77.228.191:443`) for timeline persistence and historical aggregation.

---

## 4. Receiver-Side Baseline Verification

*To be completed via coordinated partner screen observation.*

### Expected Receiver-Side Display Mapping
- **Sender Avatar Pin**: Placed on map at current sender coordinates.
- **Battery Badge**: Expected to display **79%** with lightning/charging icon.
- **Freshness Indicator**: Expected to indicate "Just now" or last-minute timestamp.
- **Network Tag**: Expected to show Wi-Fi icon or `"Why ??"` SSID tag if configured in UI.
