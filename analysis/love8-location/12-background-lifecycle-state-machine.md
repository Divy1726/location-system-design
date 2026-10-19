# 12 — Background Lifecycle and Execution State Machine

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Forensic decompilation of `com.lovemaps.core.h.java`, `LoveLocationServer.java`, `BootBroadcastReceiver.java`, and `SendLocationInfoWorker.java`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Background Execution State Transition Diagram

```
                 [Device Boot / ACTION_BOOT_COMPLETED]
                                   │
                                   ▼
[User Launches MainActivity] ──► h.c(Context) ◄── [App OnResume / Foregrounding]
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                             ▼
          [Android < API 34]              [Android >= API 34]
                    │                             │
                    ▼                    Is Permission Granted?
         context.startService()             (i.j() == true)
                    │                       ├── Yes ──► context.startForegroundService()
                    │                       └── No  ──► context.stopService() & Post Alert
                    └──────────────┬──────────────┘
                                   ▼
                 [LoveLocationServer.onCreate()]
                                   │
                 ├─► 1. startForeground(ID=100, type=0x08)
                 ├─► 2. Register Geofence: GeofenceManager.e()
                 └─► 3. LoveLocationServer.onStartCommand()
                                   │
                                   ├─► EXTKt.startLocation("启动定位服务")
                                   └─► Returns: START_NOT_STICKY (2)
                                   │
        ┌──────────────────────────┴──────────────────────────┐
        ▼                                                     ▼
[Process Remains Alive]                               [OS Kills Process (Low RAM)]
        │                                                     │
Continuous GMS Callbacks (every 5s)                   Does OS recreate service?
        │                                                     │
        │                                             NO: START_NOT_STICKY suppresses
        │                                             automatic intent recreation!
        │                                                     │
        │                                                     ▼
        │                                           [Recovery Mechanisms]
        │                                             1. WorkManager: SendLocationInfoWorker
        │                                             2. High-Priority FCM Wakeup Push
        │                                             3. BootBroadcastReceiver (on reboot)
        └─────────────────────────────────────────────4. MainActivity onResume (user re-opens)
```
`[CONFIRMED_STATIC]`

---

## 2. Who Starts the Service & Entry Gates

All service startup requests converge onto a centralized static orchestrator: `com.lovemaps.core.h.c(Context context)` (`C:\Users\sutha\Desktop\rea\extracted\decompiled_love8\com\lovemaps\core\h.java`, Lines 32–70):

```java
// com.lovemaps.core.h.java (Lines 32-63)
public static void c(Context context) {
    context.getClass();
    CoreApp.f46349i.getClass();
    if (CoreApp.f46351k) { // Guard: Already running
        return;
    }
    try {
        // Android 14 (API 34) Foreground Service Safety Check
        if (SDKUtil.isAfter34() && !com.lovemaps.core.permission.i.j()) {
            context.stopService(new Intent(context, LoveLocationServer.class));
            CoreApp.f46351k = false;
        }
    } catch (Throwable th2) { ... }
    
    try {
        if (!SDKUtil.isAfter34()) {
            context.startService(new Intent(com.lovemaps.jetpackmvvm.base.a.a(), LoveLocationServer.class));
            CoreApp.f46351k = true;
        } else if (com.lovemaps.core.permission.i.j()) {
            context.startForegroundService(new Intent(com.lovemaps.jetpackmvvm.base.a.a(), LoveLocationServer.class));
            CoreApp.f46351k = true;
        }
        r.B(k0.a(a()), null, null, new CoreAppKt$startLocationServer$1(2, null), 3);
    } catch (Exception e) {
        CoreApp.f46351k = false;
        e.printStackTrace();
    }
}
```
`[CONFIRMED_STATIC]`

### Verified Callers of `h.c()`:
1. **Activity Resumption:** `com.lovemaps.app.mvvm.ui.activity.MainActivity$detailAppForegroundService$1` (Line 53). Triggered on every cold launch and subsequent `onResume` lifecycle event.
2. **Device Reboot:** `com.lovemaps.core.broadcast.BootBroadcastReceiver.onReceive` (Line 35). Triggered upon system broadcast `android.intent.action.BOOT_COMPLETED`.

---

## 3. Lifecycle Execution & What `START_NOT_STICKY` Proves

Inside `LoveLocationServer.java`:
```java
@Override
public final int onStartCommand(Intent intent, int flags, int startId) {
    super.onStartCommand(intent, flags, startId);
    a(); // Immediate startForeground(100, notification, 8)
    r.B(p.a(this.f19745a.getF19782a()), null, null, new C08871(), 3);
    return 2; // Exact constant: Service.START_NOT_STICKY
}
```
`[CONFIRMED_STATIC]`

### Forensic Reality of `START_NOT_STICKY (2)`:
1. **No Automatic OS Respawn:** If the Android OS terminates the process under memory pressure (e.g., user launches a heavy 3D game or camera), the operating system **will not** recreate `LoveLocationServer` until another explicit intent is sent.
2. **Why Not `START_STICKY`?** On Android 12+ (API 31+), `START_STICKY` services that attempt to call `startForeground()` when respawned in the background frequently crash with `ForegroundServiceStartNotAllowedException`. Returning `START_NOT_STICKY` prevents crash loops.
3. **Resilience Strategy:** Instead of relying on OS service recreation, Love8 shifts background resilience to **WorkManager scheduled workers** and **high-priority FCM data pushes**.

---

## 4. Background Recovery: `SendLocationInfoWorker`

When `LoveLocationServer` is terminated or suspended, background positioning is revived by `com.lovemaps.core.work.SendLocationInfoWorker`:

```java
// SendLocationInfoWorker.java (Lines 76-80, 100-115)
PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Love8:LocationWakeLock");
wakeLock.acquire(10000L); // 10-second timeout guarantee

setForeground(createForegroundInfo()); // Promotes worker to foreground status
LocationManager locationManager = LocationManager.getInstance();
locationManager.requestCurrentLocation("WorkerLocation", true, continuation);
```
`[CONFIRMED_STATIC]`

- **Scheduled by WorkManager:** Periodically executed to guarantee fresh coordinate acquisition even when the main service has been suspended by aggressive OEM battery managers `[CONFIRMED_STATIC]`.
- **Timed Partial WakeLock:** Holds CPU execution awake for up to 10 seconds to allow satellite lock acquisition and outbound network socket dispatch `[CONFIRMED_STATIC]`.

---

## 5. Persistent Notification: Evidence vs. Indicator

- **Notification ID:** `100`
- **Channel ID:** Managed via `Love8InfoNotificationExt`
- **Foreground Type:** `0x00000008` (`FOREGROUND_SERVICE_TYPE_LOCATION`)
- **Evidentiary Reality:** The presence of the persistent notification icon in the Android status bar is **evidence only that the Android service process is currently pinned in RAM**. It is **not** evidence that valid GPS coordinates are being acquired or transmitted (e.g., if the user enters a sub-level basement where GPS signals are lost, the notification remains visible while coordinates fail or get rejected by the accuracy filter) `[CONFIRMED_STATIC]`.
