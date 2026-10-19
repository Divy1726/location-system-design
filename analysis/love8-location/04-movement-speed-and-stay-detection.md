# 04 — Movement Speed and Stay Detection

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Forensic decompilation of `LocationManager.java`, `PlaceTransitionConfirmation.java`, `GeofencePlacesSendManager.java`, and `StayLocationNewBean.java`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Algorithmic Movement & Velocity Filtering Pipeline

In `LocationManager.java`, once a location passes basic horizontal accuracy ($\le 200\text{ m}$) and timestamp monotonic ordering, it enters an algorithmic movement analysis stage:

```
[Raw Location Sample]
       │
       ▼
[Accuracy <= 200m] ──No──► [Reject: accuracy > 200m; f50903b++]
       │ Yes
       ▼
[Monotonic Time >= lastLocation.time] ──No──► [Reject: timestamp regression; f50903b++]
       │ Yes
       ▼
[Consecutive Filter Count >= 5 ?] ──Yes──► [FORCE ACCEPT: Reset f50903b = 0]
       │ No
       ▼
[Calculate Theoretical Velocity]
  distance = Haversine(lastLoc, newLoc)
  deltaSec = (newLoc.time - lastLoc.time) / 1000.0
  speedKmH = (distance / deltaSec) * 3.6
       │
       ├─► speedKmH > 200.0 km/h ──► [Reject: Physical Anomaly; f50903b++]
       │
       ▼
[Stationary Wi-Fi Jump Detection]
  Is distance > 500m OR reported speed >= 5.0 m/s?
       │
       ├── Yes: Is user in Frequent Place (status == 0) AND on Wi-Fi (netInfo == 1) AND distance >= placeRadius?
       │          ├── Yes ──► [Reject: Indoor Wi-Fi GPS Drift Jump; f50903b++]
       │          └── No  ──► [ACCEPT LOCATION; Reset f50903b = 0]
       │
       └── No: Speed / Displacement Inconsistent ──► [Reject: Unreasonable jump; f50903b++]
```
`[CONFIRMED_STATIC]`

---

## 2. Velocity & Anomaly Rejection Implementation

### A. Theoretical Speed Ceiling Filter
```java
// LocationManager.java (Lines 808-815)
float distance2 = EXTKt.getDistance(
    new LatLng(location4.getLatitude(), location4.getLongitude()), 
    new LatLng(location.getLatitude(), location.getLongitude())
);
float time3 = (location.getTime() - location4.getTime()) / 1000.0f;
if (time3 > 0.0f) {
    float f10 = (distance2 / time3) * 3.6f; // m/s converted to km/h
    if (f10 > 200.0f) {
        LoggerExtKt.needUploadLoggerE(
            "排除该位置 " + str + "-> 理论速度过高: " + EXTKt.getRoundFloat(f10) 
            + " km/h latLng:" + location.getLatitude() + "," + location.getLongitude(), 
            "LocationManager", 555
        );
        locationManager.f50903b = locationManager.f50903b + 1;
        return Boolean.FALSE;
    }
    ...
```
`[CONFIRMED_STATIC]`
- **Threshold:** Exactly `200.0 km/h` ($55.56\text{ m/s}$).
- **Mechanism:** Computes the great-circle displacement divided by elapsed delta-time. If the calculated physical velocity exceeds $200\text{ km/h}$, the sample is discarded as a teleportation/multipath artifact `[CONFIRMED_STATIC]`.

### B. Stationary Wi-Fi Jump Filter (Indoor GPS Drift Suppression)
```java
// LocationManager.java (Lines 815-834)
} else if (distance2 > 500.0f || (location.getSpeed() >= 5.0f && location.hasSpeed())) {
    int iIntValue = ((Number) EXTKt.getMineNetInfo().getFirst()).intValue(); // 1 = Wi-Fi
    UserStatusManager.f51087w.getClass();
    UserStatusManager userStatusManager = com.lovemaps.core.manage.location.marker.l.f51151a;
    int i5 = userStatusManager.f51095i; // 0 = in frequent place
    int i10 = userStatusManager.f51097k; // Frequent place boundary radius (meters)
    
    if (i5 != 0 || iIntValue != 1 || i10 < 100 || distance2 < i10) {
        locationManager.f50903b = 0; // Accept location
    } else {
        LoggerExtKt.needUploadLoggerE(
            "排除该位置 " + str + "-> 在常用地点并且连接wifi的情况下: 距离 " 
            + distance2 + " 米,地点范围:" + i10 + " 米，latLng:" 
            + location.getLatitude() + "," + location.getLongitude(), 
            "LocationManager", 555
        );
        locationManager.f50903b = locationManager.f50903b + 1;
        return Boolean.FALSE;
    }
} else {
    LoggerExtKt.needUploadLoggerE(
        "排除该位置 " + str + "-> 位置跳跃: 距离 " + distance2 
        + " 米，但速度不合理: " + EXTKt.getRoundFloat(location.getSpeed()) 
        + " m/s latLng:" + location.getLatitude() + "," + location.getLongitude(), 
        "LocationManager", 555
    );
    locationManager.f50903b = locationManager.f50903b + 1;
    return Boolean.FALSE;
}
```
`[CONFIRMED_STATIC]`

#### Forensic Insights on Wi-Fi Stationary Logic:
1. When a device is indoors, GPS satellite visibility drops, causing GNSS multipath reflections that appear as sudden 500m+ jumps.
2. Love8 checks:
   - Is device connected to Wi-Fi (`netInfo == 1`)?
   - Is the user currently situated at an established Frequent Place (`userStatusManager.f51095i == 0`, e.g., Home or Workplace)?
   - Is the jump distance larger than the place radius (`distance2 >= i10`)?
3. If all three hold true, Love8 explicitly categorizes the jump as indoor Wi-Fi drift and discards the sample `[CONFIRMED_STATIC]`.

### C. Consecutive Filter Recovery (Anti-Latching Fail-Safe)
```java
// LocationManager.java (Lines 804-807)
} else if (locationManager.f50903b >= 5) {
    LoggerExtKt.needUploadLoggerE(
        "连续过滤次数过多，可能基准位置错误，接受当前位置 " + str 
        + "-> 重置过滤计数 latLng:" + location.getLatitude() + "," + location.getLongitude(), 
        "LocationManager", 555
    );
    locationManager.f50903b = 0;
    // Bypasses filtering and proceeds to accept location
}
```
`[CONFIRMED_STATIC]`
- **The Problem Solved:** If an initial erroneous location was accepted as the baseline `lastLocation`, all subsequent legitimate location updates might be falsely rejected for exceeding the $200\text{ km/h}$ speed limit, permanently trapping the tracking engine.
- **The Solution:** If the engine rejects 5 consecutive incoming samples (`f50903b >= 5`), it assumes the cached baseline itself is flawed, resets the counter to 0, and forces acceptance of the 5th sample `[CONFIRMED_STATIC]`.

---

## 3. Place and Stay Detection: `PlaceTransitionConfirmation`

Transition events between movement and stationary dwelling are arbitrated by `com.lovemaps.core.manage.PlaceTransitionConfirmation`:

### Core State & Parameter Definitions (`PlaceTransitionConfirmation.java`)
- **Default Required Points:** `requiredPointCount = 3` `[CONFIRMED_STATIC]`
- **Default Required Duration:** `requiredDurationMillis = 60000L` (60,000 ms = 60 seconds) `[CONFIRMED_STATIC]`
- **Candidate Data Model:**
  ```java
  public static final class Candidate {
      public final int placeId;                // Identifier of geofenced place
      public final int transitionType;         // 4 = ENTER / DWELL, 2 = EXIT
      public final long startedAtMillis;       // Timestamp of first qualifying sample
      public final long lastLocationTimeMillis;// Timestamp of most recent sample
      public final int pointCount;             // Accumulated qualifying fixes
  }
  ```
  `[CONFIRMED_STATIC]`

### Transition Confirmation Algorithm (`PlaceTransitionConfirmation.a`)
```java
// PlaceTransitionConfirmation.java (Lines 87-110)
public final Integer a(int i5, Integer num, long j8, boolean z10) {
    if (!z10) return null; // Location accuracy must be valid
    if (num == null) {
        this.f50954c = null;
        return null;
    }
    Candidate candidate = this.f50954c;
    // New place candidate or altered transition type
    if (candidate == null || candidate.f50955a != i5 || candidate.f50956b != num.intValue()) {
        this.f50954c = new Candidate(i5, num.intValue(), j8, j8, 1);
        return null; // Not yet confirmed
    }
    if (j8 <= candidate.f50958d) return null; // Reject stale/duplicate timestamps
    
    // Accumulate point count and update last timestamp
    Candidate candidate2 = new Candidate(candidate.f50955a, candidate.f50956b, candidate.f50957c, j8, candidate.e + 1);
    this.f50954c = candidate2;
    
    // CONFIRMATION CONDITION: Both count >= 3 AND duration >= 60,000ms
    if (candidate2.e < this.f50952a || candidate2.f50958d - candidate2.f50957c < this.f50953b) {
        return null; // Suppress notification until fully sustained
    }
    
    this.f50954c = null; // Reset candidate state
    return Integer.valueOf(candidate2.f50956b); // Return confirmed transition
}
```
`[CONFIRMED_STATIC]`

### Anti-Flapping & Hysteresis Guarantees:
1. **Debounce Period:** A candidate arrival or departure event cannot trigger immediately upon crossing a geofence border.
2. **Dual-Constraint Verification:**
   - At least **3 distinct consecutive location samples** must be recorded within the candidate state.
   - A minimum elapsed duration of **60 seconds** must elapse between the first and last sample.
3. This eliminates false arrival/departure alerts caused by momentary GPS drift or passing by a location in a vehicle `[CONFIRMED_STATIC]`.

---

## 4. Race Condition Prevention: `GeofencePlacesSendManager`

When a place transition is confirmed, `GeofencePlacesSendManager` dispatches background synchronization via WorkManager:

```java
// GeofencePlacesSendManager.java (Lines 56-80)
if (i5 == 4) { // 4 = ENTER / DWELL
    ExistingWorkPolicy existingWorkPolicy = ExistingWorkPolicy.REPLACE;
    OneTimeWorkRequest.Builder builder2 = new OneTimeWorkRequest.Builder(PlacesSendEnterWork.class)
        .d(BackoffPolicy.EXPONENTIAL, 30L);
    builder2.f29000c.e = dataA;
    workManagerImplA.a("places_send_enter_work", existingWorkPolicy, builder2.a());
} else { // 2 = EXIT
    ExistingWorkPolicy existingWorkPolicy2 = ExistingWorkPolicy.REPLACE;
    OneTimeWorkRequest.Builder builder3 = new OneTimeWorkRequest.Builder(PlacesSendEnterWork.class)
        .d(BackoffPolicy.EXPONENTIAL, 30L);
    builder3.f29000c.e = dataA;
    workManagerImplA.a("places_send_exit_work", existingWorkPolicy2, builder3.a());
}
```
`[CONFIRMED_STATIC]`

- **`cancelOppositeWork(int transitionType)`:** Before enqueueing an `ENTER` notification, Love8 actively executes `WorkManager.cancelUniqueWork("places_send_exit_work")`, and vice versa `[CONFIRMED_STATIC]`.
- This ensures that if a user briefly steps outside and returns within the 30-second backoff window, the outdated departure event is revoked before reaching the partner `[CONFIRMED_STATIC]`.

---

## 5. Stay Location Record Persistence (`StayLocationNewBean`)

Confirmed dwell periods and stay locations are persisted in the local Room database table `stay_location_new_dao`:

```java
// StayLocationNewBean.java
@Entity(tableName = "stay_location_new_dao")
public final class StayLocationNewBean implements Parcelable {
    @PrimaryKey(autoGenerate = true)
    private long roomId;
    private final double latitude;
    private final double longitude;
    private final int userId;
    private final long markerTagClickTime;
    private final long topTipCloseTime;
    private final long headerCloseTime;
    private final int stayCount; // Accumulated visits/dwell count
}
```
`[CONFIRMED_STATIC]`
- Tracks dwell count (`stayCount`), coordinates, user ID, and interaction timestamps for rendering stay duration bubbles and timeline visit histories in the UI `[CONFIRMED_STATIC]`.
