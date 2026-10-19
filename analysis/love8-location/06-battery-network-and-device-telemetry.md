# 06 — Battery, Network, and Device Telemetry

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Forensic decompilation of `BatteryChangeReceiver.java`, `LocationManager.java`, `EXTKt.java`, and `TIMMessageManager.java`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Battery Telemetry Acquisition & Monitoring

Partner apps require real-time knowledge of device battery percentage and charging status (e.g., to notify when a partner's phone is dying or currently plugged in). Love8 implements dual monitoring:

### A. Dynamic Broadcast Interception (`BatteryChangeReceiver`)
- **Class Reference:** `com.lovemaps.core.broadcast.BatteryChangeReceiver`
- **Class Path:** `C:\Users\sutha\Desktop\rea\extracted\decompiled_love8\com\lovemaps\core\broadcast\BatteryChangeReceiver.java`

```java
// BatteryChangeReceiver.java (Lines 105-112, 133-149)
String action = intent.getAction();
if (Intrinsics.areEqual(action, "android.intent.action.BATTERY_CHANGED") ||
    Intrinsics.areEqual(action, "android.intent.action.ACTION_POWER_CONNECTED") ||
    Intrinsics.areEqual(action, "android.intent.action.ACTION_POWER_DISCONNECTED")) {
    
    int intExtra = intent.getIntExtra("level", -1);
    int intExtra2 = intent.getIntExtra("scale", -1);
    
    if (intExtra2 > 0 && intExtra >= 0 && intExtra <= intExtra2) {
        int percentage = (int) ((((long) intExtra) * 100L) / ((long) intExtra2));
        // Dispatches to coroutine scope for low-battery alert or partner sync
        BatteryChangeReceiver.this.e.invoke(
            Boxing.boxInt(percentage), 
            Boxing.boxInt(batterySnapshot.f50848b), 
            Boxing.boxInt(this.$userId)
        );
    }
}
```
`[CONFIRMED_STATIC]`

### B. Hardware Polling via `BatteryManager` (`LocationManager`)
In `com.lovemaps.core.manage.LocationManager`, an instance of `android.os.BatteryManager` is lazily resolved (`f50912l`):
- Calls `batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)` to attach immediate battery percentage directly to outgoing location packets `[CONFIRMED_STATIC]`.

---

## 2. Network Connectivity Telemetry: `EXTKt.getMineNetInfo`

Network status telemetry serves dual roles: diagnosing connection health and gating location filtering logic:

```java
// EXTKt.java (Decompiled Network Helper)
public static final Pair<Integer, String> getMineNetInfo() {
    ConnectivityManager connectivityManager = (ConnectivityManager) 
        context.getSystemService(Context.CONNECTIVITY_SERVICE);
    Network activeNetwork = connectivityManager.getActiveNetwork();
    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
    
    if (capabilities == null) {
        return new Pair<>(-1, "NO_NETWORK");
    }
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        return new Pair<>(1, "WIFI"); // Type 1 = Wi-Fi
    }
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
        return new Pair<>(2, "CELLULAR"); // Type 2 = Cellular / Mobile Data
    }
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
        return new Pair<>(3, "ETHERNET");
    }
    return new Pair<>(0, "UNKNOWN");
}
```
`[CONFIRMED_STATIC]`

### Algorithmic Integration:
- **Stationary Gatekeeper:** When `getMineNetInfo().getFirst() == 1` (Wi-Fi), `LocationManager` activates its indoor drift filter (`distance > 500m && inPlace`) to suppress spurious jumps `[CONFIRMED_STATIC]`.
- **Upload Queue Modulation:** WorkManager workers (`UploadOrbitWork`, `SendLocationInfoWorker`) evaluate network constraints (`NetworkType.CONNECTED`) before initiating payload serialization `[CONFIRMED_STATIC]`.

---

## 3. Real-Time Telemetry Synchronization over Tencent IM

In `com.lovemaps.core.manage.tim.TIMMessageManager`, telemetry updates are pushed to the partner device using dedicated command protocols:

### A. Speed & Overspeed Telemetry
```java
// TIMMessageManager.java (Line 68, Method 33)
// Signature: sendSpeedingUpdate(boolean speedStatus, int speed, int speedUnit) -> void
public final void sendSpeedingUpdate(boolean speedStatus, int speed, int speedUnit) {
    // Serializes overspeed event into TimCmdBean and dispatches over V2TIMManager
}
```
`[CONFIRMED_STATIC]`
- **`speedStatus`:** Boolean indicating whether user exceeded the couple driving safety speed threshold.
- **`speed`:** Instantaneous speed value.
- **`speedUnit`:** Unit identifier (0 = km/h, 1 = mph) `[CONFIRMED_STATIC]`.

### B. Proximity & Distance Telemetry
```java
// TIMMessageManager.java (Line 68, Method 34)
// Signature: sendDistanceUpdate(boolean nearbyStatus, int distance) -> void
public final void sendDistanceUpdate(boolean nearbyStatus, int distance) {
    // Dispatches mutual proximity alert when distance drops within threshold
}
```
`[CONFIRMED_STATIC]`
- Alerts partner when devices are within walking proximity of each other.

### C. Battery Push Alerts
```java
// TIMMessageManager.java (Line 68, Method 39)
// Signature: sendBatteryPushUpdate(boolean push) -> void
public final void sendBatteryPushUpdate(boolean push) {
    // Sends urgent push alert if battery drops below critical threshold (e.g. <= 15% or <= 5%)
}
```
`[CONFIRMED_STATIC]`

---

## 4. Presence and Online Status Policy: `PartnerOnlineStatusPolicy`

- **Class Reference:** `com.lovemaps.core.manage.presence.PartnerOnlineStatusPolicy`
- **Class Path:** `C:\Users\sutha\Desktop\rea\extracted\decompiled_love8\com\lovemaps\core\manage\presence\PartnerOnlineStatusPolicy.java`
- **Source Tracking:** `PartnerOnlineStatusSource` correlates three distinct presence inputs:
  1. Tencent IM WebSocket/TCP socket connection state (`V2TIMUserStatus`).
  2. Recent location upload timestamp freshness ($< 5\text{ minutes}$).
  3. FCM heartbeat ping reception `[CONFIRMED_STATIC]`.
- **UI Degradation:** If no fresh location or TIM socket heartbeat is received within the expiration window, the partner marker transitions from "Active / Moving" to "Offline / Last seen X min ago" `[CONFIRMED_STATIC]`.
