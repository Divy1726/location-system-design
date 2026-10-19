# 16 — Device Telemetry and Network State Dependencies

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Forensic decompilation of `BatteryChangeReceiver.java`, `LocationManager.java`, `EXTKt.java`, and `TIMMessageManager.java`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Battery Telemetry: Acquisition, Units & Alerts

Partner tracking applications must provide reliable battery awareness without generating continuous battery drain themselves. Love8 implements a combination of broadcast listening and hardware property polling:

### A. Broadcast Interception (`BatteryChangeReceiver`)
- **Broadcast Filter:** `android.intent.action.BATTERY_CHANGED`, `android.intent.action.ACTION_POWER_CONNECTED`, `android.intent.action.ACTION_POWER_DISCONNECTED`.
- **Mathematical Formula (`BatteryChangeReceiver.java`, Line 109):**
  $$\text{batteryPercentage} = \frac{\text{level} \times 100}{\text{scale}}$$
  - `level`: Integer extracted from `intent.getIntExtra("level", -1)`.
  - `scale`: Integer extracted from `intent.getIntExtra("scale", -1)`.
- **Charging State:** Evaluated via broadcast action `ACTION_POWER_CONNECTED` vs `ACTION_POWER_DISCONNECTED`.
- **Low-Battery Alert:** When battery drops $\le 15\%$ or $\le 5\%$, dispatches `TIMMessageManager.sendBatteryPushUpdate(true)` to trigger an urgent partner alert `[CONFIRMED_STATIC]`.

### B. Hardware Property Polling (`BatteryManager`)
In `LocationManager.java`, an instance of `android.os.BatteryManager` (`f50912l`) reads `batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)` directly during location packaging, ensuring every outgoing location fix carries an exact instantaneous battery reading `[CONFIRMED_STATIC]`.

---

## 2. Network Telemetry: `EXTKt.getMineNetInfo`

Network status telemetry is resolved via `ConnectivityManager` in `com.lovemaps.core.ext.EXTKt`:

```java
// EXTKt.java
public static final Pair<Integer, String> getMineNetInfo() {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    Network activeNetwork = cm.getActiveNetwork();
    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
    if (caps == null) return new Pair<>(-1, "NO_NETWORK");
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return new Pair<>(1, "WIFI");
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return new Pair<>(2, "CELLULAR");
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return new Pair<>(3, "ETHERNET");
    return new Pair<>(0, "UNKNOWN");
}
```
`[CONFIRMED_STATIC]`

### Architectural Dependencies on Network State:
1. **Indoor GPS Jump Suppression:** `LocationManager.handleLocation` evaluates `netInfo == 1`. If the user is at a Frequent Place (`placeStatus == 0`) and connected to Wi-Fi (`netInfo == 1`), large jumps $\ge \text{placeRadius}$ are rejected as indoor GNSS drift `[CONFIRMED_STATIC]`.
2. **WorkManager Network Constraints:** `UploadOrbitWork` and `SendLocationInfoWorker` define constraints requiring `NetworkType.CONNECTED` before triggering payload serialization `[CONFIRMED_STATIC]`.
3. **Region Determination:** `EXTKt.isConnectedToExternalServer("www.google.com")` evaluates reachability to establish whether the device is operating behind mainland Chinese network firewalls (`isCN()`) `[CONFIRMED_STATIC]`.

---

## 3. Real-Time Telemetry Synchronization over Tencent IM

In `TIMMessageManager.java`, auxiliary telemetry updates are transmitted to the partner using specialized command protocols:

| Telemetry Command | Method in `TIMMessageManager` | Data Fields / Units | Trigger Condition |
| :--- | :--- | :--- | :--- |
| **Speeding Alert** | `sendSpeedingUpdate(boolean speedStatus, int speed, int speedUnit)` | `speedStatus`: Boolean<br>`speed`: Integer value<br>`speedUnit`: 0 = km/h, 1 = mph | Triggered when `speed * 3.6f > threshold` `[CONFIRMED_STATIC]`. |
| **Proximity Alert** | `sendDistanceUpdate(boolean nearbyStatus, int distance)` | `nearbyStatus`: Boolean<br>`distance`: Meters | Triggered when partner distance drops within proximity threshold `[CONFIRMED_STATIC]`. |
| **Battery Push** | `sendBatteryPushUpdate(boolean push)` | `push`: Boolean | Triggered when battery drops to critical level ($\le 15\%$) `[CONFIRMED_STATIC]`. |
| **Online Status** | `sendStatusNotify` / `sendOnlineStatusNotification(boolean isOnline)` | `isOnline`: Boolean | Triggered on socket connect / app resume / disconnect `[CONFIRMED_STATIC]`. |
