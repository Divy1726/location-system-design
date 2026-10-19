# 01 — Environment and Artifact Inventory

**Mission:** Love8 Mobile Location System — Forensic Reverse Engineering Report  
**Evidence Standard:** Strict forensic static analysis grounded in verified artifact evidence.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Analysis Environment & Toolchain Verification

| Component | Path / Configuration | Version / Verification Status | Status |
| :--- | :--- | :--- | :--- |
| **Operating System** | Windows 11 Enterprise (NT Kernel) | PowerShell 5.1 / 7+ compatible | Verified `[CONFIRMED_STATIC]` |
| **Working Directory** | `C:\Users\sutha\Desktop\rea` | Dedicated investigation workspace | Verified `[CONFIRMED_STATIC]` |
| **Java Development Kit** | `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\java.exe` | OpenJDK 21.0.12.1 LTS (Microsoft Build) | Verified `[CONFIRMED_STATIC]` |
| **JAVA_HOME** | `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot` | System environment variable set & active | Verified `[CONFIRMED_STATIC]` |
| **JADX Headless MCP** | `C:\Users\sutha\.gemini\config\android\jadx-headless-mcp-0.7.1-all.jar` | `0.7.1-all` (SHA-256: `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`) | Verified `[CONFIRMED_STATIC]` |
| **REA Server** | `rea-agents@6.7.0` | Active MCP Server connected to Antigravity | Verified `[CONFIRMED_STATIC]` |
| **Target Application** | Love8 — Couple Relationship App | Version 3.19.0 (Version Code 477) | Verified `[CONFIRMED_STATIC]` |
| **Target Package** | `ltd.love8.couples.relationship` | Target SDK 34 (Android 14), Min SDK 24 (Android 7.0) | Verified `[CONFIRMED_STATIC]` |
| **Android Device Bridge** | `adb` | Not located on PATH; runtime analysis deferred | Verified `[UNKNOWN]` |

---

## 2. Original Primary Artifact Verification

The target package was supplied as an XAPK bundle. The original artifact was preserved intact, and a cryptographic checksum was established before any analysis:

- **Original Absolute Path:** `C:\Users\sutha\Desktop\rea\Love8+-+App+for+Couples_3.19.0_APKPure.xapk`
- **File Size:** `259,336,179` bytes (247.32 MB)
- **SHA-256 Hash:** `0a484a92285e24c456e7ed724990e87776f96d26ffcee217c4482dcde9c8ecfb`
- **Format:** Zip archive container conforming to standard XAPK package specification `[CONFIRMED_STATIC]`.

---

## 3. Extracted Artifact Inventory

Extraction was executed into a dedicated analysis workspace: `C:\Users\sutha\Desktop\rea\extracted\love8-xapk\`. Hashes and specifications of all contained members:

| File Name | File Size (Bytes) | SHA-256 Hash | Role / Description |
| :--- | :--- | :--- | :--- |
| `manifest.json` | 3,342 | `46a7a8c389be893e1a4f9ea4b3389596ce31f77e692ca683c9b9f1856a8af4ea` | XAPK package descriptor metadata `[CONFIRMED_STATIC]` |
| `ltd.love8.couples.relationship.apk` | 226,527,669 | `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619` | **Base APK**: Contains DEX bytecode (classes.dex - classes9.dex), resources, assets, AndroidManifest.xml `[CONFIRMED_STATIC]` |
| `config.arm64_v8a.apk` | 32,593,267 | `00d77cd50e670d5b11d344740ecf6b59420e5adb58d2ec9d1e0ddf7a5e770451` | **ABI Split**: Native binary libraries compiled for `arm64-v8a` `[CONFIRMED_STATIC]` |
| `config.xxhdpi.apk` | 211,299 | `5953131c2d80c47e10cfcc795b751350ba478d0d00f785272c9ce1146e7e9586` | **Density Split**: Screen density resource drawables `[CONFIRMED_STATIC]` |
| `icon.png` | 29,298 | `1321741761e0501a35561a0b1cb7ebc973bfdc2bf9c6563ee9a9dd7ba2ae5dcf` | Application Launcher Icon |

---

## 4. Package Metadata & Architecture Inventory

Inspection of `manifest.json` and decoded `AndroidManifest.xml` (`C:\Users\sutha\Desktop\rea\extracted\base-extracted\AndroidManifest_decoded.xml`) establishes the package identity:

- **Package Name:** `ltd.love8.couples.relationship` `[CONFIRMED_STATIC]`
- **Version Code:** `477` `[CONFIRMED_STATIC]`
- **Version Name:** `3.19.0` `[CONFIRMED_STATIC]`
- **Min SDK Version:** `24` (Android 7.0 Nougat) `[CONFIRMED_STATIC]`
- **Target SDK Version:** `34` (Android 14 Upside Down Cake) `[CONFIRMED_STATIC]`
- **Compile SDK Version:** `34` `[CONFIRMED_STATIC]`
- **Application Class:** `com.lovemaps.app.LoveApplication` `[CONFIRMED_STATIC]`
- **Main Launch Activity:** `com.lovemaps.app.mvvm.ui.activity.MainActivity` `[CONFIRMED_STATIC]`

### Hybrid Application Stack Architecture
Static deconstruction of the Base and Split APKs reveals a hybrid architecture:
1. **Flutter Hybrid Presentation Layer:**
   - Framework binary: `libflutter.so` (Flutter engine)
   - Application binary: `libapp.so`
   - Engine snapshot hash: `0451907c2eaa8467e848c0067bfe8ed4` `[CONFIRMED_STATIC]`
   - Role: Renders the majority of interactive couple screens, widgets, animations, and relationship UI.
2. **Native Android MVVM Core Engine:**
   - Implemented under Java/Kotlin namespace: `com.lovemaps.core.*` and `com.lovemaps.app.*` `[CONFIRMED_STATIC]`
   - Framework: Android Jetpack MVVM, Room Database, Kotlin Coroutines & Flow, WorkManager 2.9+, Google Play Services Location.
   - Role: All critical location acquisition, coordinate transformation (WGS84 -> GCJ-02), stability filtering, geofencing, battery monitoring, and Tencent IM networking run completely natively in Android background services and workers `[CONFIRMED_STATIC]`.

---

## 5. Packaged Native Libraries Inventory (`config.arm64_v8a.apk`)

The native split contains compiled dynamic libraries (`lib/arm64-v8a/`):
- `libapp.so`: Flutter compiled Dart AOT snapshot `[CONFIRMED_STATIC]`.
- `libflutter.so`: Flutter engine native core `[CONFIRMED_STATIC]`.
- `libImSDK.so` & `libTIMQuicPlugin.so`: Tencent Cloud Instant Messaging (TIM) SDK native networking core. Handles peer-to-peer friend location distribution over QUIC/TCP `[CONFIRMED_STATIC]`.
- `libmmkv.so`: Tencent MMKV high-performance key-value storage engine `[CONFIRMED_STATIC]`.
- `liblogan.so`: Meituan Logan encrypted mobile logging library (used by `LoggerExtKt.needUploadLoggerE`) `[CONFIRMED_STATIC]`.
- `libpag.so`: Tencent Portable Animated Graphics renderer `[CONFIRMED_STATIC]`.

---

## 6. Decompilation & Forensic Workspace

A headless decompilation pipeline was executed using `jadx-headless-mcp-0.7.1-all.jar` targeting all bytecode in package namespace `com.lovemaps.core.*`.
- **Output Directory:** `C:\Users\sutha\Desktop\rea\extracted\decompiled_love8\`
- **Recovered Java Source Files:** 4,103 classes `[CONFIRMED_STATIC]`
- **Primary Decompiled Components:**
  - `com.lovemaps.core.server.LoveLocationServer`: Foreground location lifecycle service.
  - `com.lovemaps.core.manage.LocationManager`: Core location acquisition and algorithmic filtering singleton.
  - `com.lovemaps.core.manage.location.GoogleLocation` / `GoogleLocationConfig`: GMS FusedLocationProviderClient client.
  - `com.lovemaps.core.manage.GeofencePlacesSendManager` & `PlaceTransitionConfirmation`: Places geofence transition confirmation engine.
  - `com.lovemaps.core.work.orbit.OrbitManager` & `UploadOrbitWork`: Trajectory compression (Douglas-Peucker) and upload worker.
  - `com.lovemaps.core.manage.tim.TIMMessageManager`: Tencent IM real-time peer location synchronization listener.
- **Evidence Bundle Export:** `C:\Users\sutha\Desktop\rea\analysis\love8-location\evidence-bundle.json` (18,210,527 bytes) `[CONFIRMED_STATIC]`.
