# 10 — Flutter / Dart AOT Layer Analysis and Separation of Concerns

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**ABI Split SHA-256:** `00d77cd50e670d5b11d344740ecf6b59420e5adb58d2ec9d1e0ddf7a5e770451` (`config.arm64_v8a.apk`)  
**Evidence Standard:** Direct ELF header extraction, REA tool verification (`identify_flutter_build`), and string pool analysis of `libapp.so`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Flutter Engine & Dart AOT Binary Facts

Verification of `config.arm64_v8a.apk` via the REA tool `identify_flutter_build` establishes the exact toolchain parameters:

| Artifact | File Size | SHA-256 Digest | Toolchain / Build Fact |
| :--- | :--- | :--- | :--- |
| `libapp.so` | 7,472,008 B | `59019dec13dd815b8a3c15c7b5f9c82c2cad31b0a94da19cd67c702df78fd803` | Snapshot Hash: `0451907c2eaa8467e848c0067bfe8ed4` `[CONFIRMED_STATIC]` |
| `libflutter.so` | 11,747,864 B | `d70bbedc2b157630f90123a32ca894a6fa1816ec10cda9f335bada0692b376ee` | GNU Build-ID: `fdf0a0a6f673cdcfc6c0e5f8dc7c8828d63fb3b6`<br>Compiler: Android Clang 19.0.1 (r530567e) `[CONFIRMED_STATIC]` |

### Diagnostic Execution of `inspect_dart_aot`
Execution of REA's `inspect_dart_aot` tool against `config.arm64_v8a.apk` returned:
```
Encountered error in tool execution: No recognizable Dart snapshot symbols were found: 
symbol _kDartVmSnapshotData is not in the dynamic symbol table; 
symbol _kDartVmSnapshotInstructions is not in the dynamic symbol table; 
symbol _kDartIsolateSnapshotData is not in the dynamic symbol table; 
symbol _kDartIsolateSnapshotInstructions is not in the dynamic symbol table
```
`[CONFIRMED_STATIC]`

### Forensic Explanation of Symbol Resolution:
Direct ELF inspection of `libapp.so` reveals that standard split snapshot symbols were omitted or merged in this build. Dynamic symbols in `.dynstr` (offset `0x1e8`, length 61 bytes) contain only:
- `_kDartSnapshotText`
- `_kDartSnapshotData`
- `_kDartSnapshotBuildId`

The ELF sections consist of:
- `.rodata` (`0x2c0`, size 2,780,560 B)
- `.text` (`0x2b0000`, size 4,615,968 B)
- `.dynamic` (`0x720000`, size 96 B)
- `.bss` (`0x720060`, size 8 B)

---

## 2. Dart Package Inventory from Byte Pool

Scanning the `.rodata` string pool of `libapp.so` revealed **38 unique Dart package roots** (encompassing 902 individual file URIs). The application-specific code is strictly organized under `package:love8_flutter/`:

```
package:love8_flutter/
├── core/
│   ├── localization/service_language.dart
│   └── network/ (api_client.dart, api_config.dart, api_exception.dart)
├── features/
│   ├── cs_chat/              <-- Customer Service Help Chat
│   │   ├── data/ (cs_chat_api.dart, cs_chat_socket.dart, media_upload_api.dart)
│   │   ├── models/chat_message.dart
│   │   └── pages/ (cs_user_chat_page.dart, media_preview_page.dart)
│   ├── draw_together/        <-- Real-Time Canvas Drawing Feature
│   │   ├── controllers/draw_together_controller.dart
│   │   ├── data/draw_together_host.dart
│   │   ├── models/ (draw_together_labels.dart, draw_together_models.dart)
│   │   ├── pages/draw_together_page.dart
│   │   └── widgets/ (draw_canvas.dart, draw_sticker_layer.dart, draw_together_toolbar.dart)
│   └── premium_event/        <-- VIP Membership & Promotion Dialogs
│       ├── controllers/premium_event_controller.dart
│       ├── data/ (premium_event_host.dart, premium_event_repository.dart, premium_event_tracking.dart)
│       ├── models/ (premium_event_copy.dart, premium_event_models.dart)
│       ├── pages/ (premium_event_page.dart, premium_event_rules_page.dart)
│       └── widgets/ (premium_event_benefits_carousel.dart, premium_event_day_card.dart, ...)
└── main.dart
```
`[CONFIRMED_STATIC]`

---

## 3. Separation of Concerns: Flutter vs. Native Android

An exhaustive string regex search across all Dart bytecode in `libapp.so` for location keywords (`location`, `geofence`, `places`, `orbit`, `timeline`, `stay`, `speed`) yielded zero hits within `package:love8_flutter/`.

### Verified Division of Responsibilities

| Subsystem | Execution Layer | Underlying Technologies / Libraries |
| :--- | :--- | :--- |
| **Location Acquisition** | **100% Native Android** | `com.google.android.gms.location.FusedLocationProviderClient`, `LocationManager` `[CONFIRMED_STATIC]` |
| **Stability & Anomaly Filtering** | **100% Native Android** | `com.lovemaps.core.manage.LocationManager` (speed, Wi-Fi jump, 5-count reset) `[CONFIRMED_STATIC]` |
| **Geofencing & Place Detection** | **100% Native Android** | `PlaceTransitionConfirmation`, `GeofencePlacesSendManager`, `WorkManager` `[CONFIRMED_STATIC]` |
| **Local Location Database** | **100% Native Android** | Android Room SQLite (`location_dao`, `stay_location_new_dao`) `[CONFIRMED_STATIC]` |
| **Historical Trajectory Smoothing**| **100% Native Android** | `OrbitManager` (Douglas-Peucker compression), `UploadOrbitWork` `[CONFIRMED_STATIC]` |
| **Live P2P Location Transport** | **100% Native Android** | Tencent Cloud IM SDK (`libImSDK.so`, `libTIMQuicPlugin.so`, `TIMMessageManager`) `[CONFIRMED_STATIC]` |
| **Main Map View & Avatars** | **100% Native Android** | Native Google Maps SDK (`com.google.android.gms.maps.*`), Jetpack Compose `[CONFIRMED_STATIC]` |
| **Customer Service Chat** | **Flutter / Dart** | `package:love8_flutter/features/cs_chat` `[CONFIRMED_STATIC]` |
| **Draw Together Canvas** | **Flutter / Dart** | `package:love8_flutter/features/draw_together` `[CONFIRMED_STATIC]` |
| **Premium Membership Events** | **Flutter / Dart** | `package:love8_flutter/features/premium_event` `[CONFIRMED_STATIC]` |

### Architectural Conclusion
Love8 is **not** a Flutter-first location app. It is a **native Android location tracking and mapping application** that embeds Flutter via `FlutterActivity` / `FlutterFragment` solely as an ancillary UI module for interactive canvas drawing, support chat, and promo carousels. All mobile location tracking, background services, and real-time synchronization run completely in native JVM and C++ layers `[CONFIRMED_STATIC]`.
