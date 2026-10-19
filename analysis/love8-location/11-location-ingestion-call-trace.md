# 11 — Complete Location Ingestion Call Trace

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Exact source file, method signature, line range, and conditional branch trace.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Complete Ingestion Call Graph

```
[Google Play Services FusedLocationProviderClient]
       │
       ▼ (Callback on Main Looper)
GoogleLocation$mLocationCallback$2$1.onLocationResult(LocationResult)
       │
       ▼
LocalLocationListener.onLocationChanged(Location)
       │
       ▼
LocationManager.onLocationChanged(Location)
       │
       ▼ (Coroutine on Dispatchers.IO)
LocationManager.h(Location, "Google持续定位", false, Continuation)  [handleLocation]
       │
       ├─► 1. Mock Test Evaluation
       │      com.lovemaps.core.manage.location.marker.e.f51137a.r()
       │
       ├─► 2. LiveData Diagnostic Dispatch
       │      EventViewModel.f50102n0.setValue(location.accuracy)
       │      EventViewModel.T.setValue(true)
       │
       ├─► 3. Coordinate Transformation Gate
       │      EXTKt.gpsToGcj02(LatLng)
       │      ├── Branch A: Debug override (t.q() == true) ──► Convert WGS84 -> GCJ-02
       │      ├── Branch B: Device NOT in China (!isCN())   ──► RETURN RAW WGS84
       │      └── Branch C: Device in China (isCN() == true) ──► Convert WGS84 -> GCJ-02
       │
       ├─► 4. Horizontal Accuracy Gate
       │      location.getAccuracy() > 200.0f ──► REJECT (f50903b++)
       │
       ├─► 5. Monotonic Timestamp Gate
       │      location.getTime() < lastLocation.getTime() ──► REJECT (f50903b++)
       │
       ├─► 6. Consecutive Filter Recovery Gate
       │      f50903b >= 5 ──► RESET f50903b = 0 & FORCE ACCEPT
       │
       ├─► 7. Theoretical Velocity Anomaly Gate
       │      (distance / deltaSec) * 3.6f > 200.0f km/h ──► REJECT (f50903b++)
       │
       ├─► 8. Indoor Stationary Wi-Fi Drift Gate
       │      distance > 500m || (speed >= 5.0m/s && hasSpeed())
       │      ├── inPlace == 0 && netInfo == 1 && distance >= placeRadius ──► REJECT (f50903b++)
       │      └── Otherwise ──► ACCEPT (f50903b = 0)
       │
       ├─► 9. Internal Anchor State Update
       │      mLastLocation = location (f50906f)
       │      mLastLatLng = latLng (f50907g)
       │
       ├─► 10. Geofence Places Evaluation
       │      GeofencePlacesSendManager.sendPlaceMessage(latLng, accuracy, time, isComplete)
       │
       ├─► 11. Observer Notification Dispatch
       │      LocationManager$handleLocation$2 dispatches on Main Dispatcher:
       │      ├── AppViewModel.onLocation(location, "Google持续定位")
       │      └── UserStatusManager.onLocation(location, "Google持续定位")
       │             │
       │             ▼
       │         UserStatusManager.n() ──► Channel.send(UserStatusEvent.LocationChanged)
       │
       ├─► 12. Local SQLite Persistence
       │      Room: LocationDao_Impl.insertLocation(LocalLocationBean) [tableName: location_dao]
       │
       └─► 13. Outbound Peer-to-Peer Transmission
              UserStatusManager.o() ──► Channel.send(TimUserInfoUpdateRequest)
                     │
                     ▼
              TIMMessageManager.updateFriendLocation(partnerImId, locationJson)
```
`[CONFIRMED_STATIC]`

---

## 2. Step-by-Step Source Verification Trace

### Step 1: Callback Reception
- **Class:** `com.lovemaps.core.manage.location.GoogleLocation`
- **Method:** `GoogleLocation.c` (Lines 140–145)
- **Source Code:**
  ```java
  Task<Void> taskRequestLocationUpdates = ((FusedLocationProviderClient) this.f51024b.getValue())
      .requestLocationUpdates(locationRequestBuild, (b) this.f51025c.getValue(), Looper.getMainLooper());
  ```
  The GMS location callback `(b) this.f51025c.getValue()` dispatches fixes to `LocalLocationListener.onLocationChanged(location)` `[CONFIRMED_STATIC]`.

### Step 2: LocationManager Hand-off
- **Class:** `com.lovemaps.core.manage.LocationManager`
- **Method:** `onLocationChanged(Location location)` (Lines 315–321, 357–361)
- **Source Code:**
  ```java
  C04251 c04251 = new C04251(LocationManager.this, this.$location, null);
  // Invokes handleLocation method 'h'
  locationManager.h(location, "Google持续定位", false, this);
  ```
  Transitions from the Main Looper onto `Dispatchers.IO` via Kotlin coroutine `r.G(y.f80569a, ...)` `[CONFIRMED_STATIC]`.

### Step 3: Regional Coordinate Transformation (`gpsToGcj02`)
- **Class:** `com.lovemaps.core.ext.EXTKt`
- **Method:** `gpsToGcj02(LatLng latLng)` (Lines 5383–5395)
- **Source Code:**
  ```java
  public static final LatLng gpsToGcj02(LatLng latLng) {
      latLng.getClass();
      com.lovemaps.core.datestore.t.f49657a.getClass();
      if (com.lovemaps.core.datestore.t.q()) {
          GCJPointer gCJPointerC = new WGSPointer(latLng.latitude, latLng.longitude).c();
          return new LatLng(gCJPointerC.a(), gCJPointerC.b());
      }
      if (!isCN()) {
          return latLng; // <--- CRITICAL: OUTSIDE CHINA, RAW WGS84 IS RETURNED UNALTERED!
      }
      GCJPointer gCJPointerC2 = new WGSPointer(latLng.latitude, latLng.longitude).c();
      return new LatLng(gCJPointerC2.a(), gCJPointerC2.b());
  }
  ```
  `[CONFIRMED_STATIC]`
- **Region Detection (`isCN()`):** Reads `com.lovemaps.core.h.a().R` (initialized via geo-IP and reachability tests against `www.google.com`). If `!isCN()`, coordinate conversion is completely bypassed.

### Step 4 & 5: Accuracy & Monotonic Timestamp Rejection
- **Class:** `com.lovemaps.core.manage.LocationManager`
- **Method:** `h` (Lines 769–798)
- **Source Code:**
  ```java
  if (location.getAccuracy() > 200.0f) {
      LoggerExtKt.needUploadLoggerE("排除该位置 " + str + "-> accuracy == " + location.getAccuracy(), "LocationManager", 555);
      locationManager.f50903b++;
      return Boxing.boxBoolean(false);
  }
  if (location.getTime() < location4.getTime()) {
      LoggerExtKt.needUploadLoggerE("排除该位置 " + str + "-> 新位置时间小于最后缓存位置的时间", "LocationManager", 555);
      locationManager.f50903b++;
      return Boxing.boxBoolean(false);
  }
  ```
  `[CONFIRMED_STATIC]`

### Step 6: 5-Count Consecutive Rejection Anti-Lock Recovery
- **Class:** `com.lovemaps.core.manage.LocationManager`
- **Method:** `h` (Lines 804–807)
- **Source Code:**
  ```java
  else if (locationManager.f50903b >= 5) {
      LoggerExtKt.needUploadLoggerE("连续过滤次数过多，可能基准位置错误，接受当前位置 " + str + "-> 重置过滤计数", "LocationManager", 555);
      locationManager.f50903b = 0;
      // Proceeds to accept current point as fresh anchor
  }
  ```
  `[CONFIRMED_STATIC]`

### Step 7 & 8: Theoretical Velocity & Stationary Wi-Fi Drift Filter
- **Class:** `com.lovemaps.core.manage.LocationManager`
- **Method:** `h` (Lines 808–834)
- **Source Code:**
  ```java
  float distance2 = EXTKt.getDistance(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), new LatLng(location.getLatitude(), location.getLongitude()));
  float time3 = (location.getTime() - lastLocation.getTime()) / 1000.0f;
  if (time3 > 0.0f) {
      float f10 = (distance2 / time3) * 3.6f;
      if (f10 > 200.0f) {
          LoggerExtKt.needUploadLoggerE("排除该位置 " + str + "-> 理论速度过高: " + f10 + " km/h", "LocationManager", 555);
          locationManager.f50903b++;
          return Boxing.boxBoolean(false);
      } else if (distance2 > 500.0f || (location.getSpeed() >= 5.0f && location.hasSpeed())) {
          int netInfo = ((Number) EXTKt.getMineNetInfo().getFirst()).intValue(); // 1 = Wi-Fi
          int placeStatus = userStatusManager.f51095i; // 0 = in frequent place
          int placeRadius = userStatusManager.f51097k;
          if (placeStatus == 0 && netInfo == 1 && placeRadius >= 100 && distance2 >= placeRadius) {
              LoggerExtKt.needUploadLoggerE("排除该位置 " + str + "-> 在常用地点并且连接wifi的情况下: 距离 " + distance2 + " 米", "LocationManager", 555);
              locationManager.f50903b++;
              return Boxing.boxBoolean(false);
          } else {
              locationManager.f50903b = 0; // Accepted!
          }
      }
  }
  ```
  `[CONFIRMED_STATIC]`

### Step 9, 10, & 11: Geofence and Observer Dispatches
- **Geofence Hand-off:** Line 886 calls `geofencePlacesSendManager.sendPlaceMessage(latLng19, accuracy, time4, zIsComplete, continuation)`.
- **Observer Fan-out:** Lines 1639–1652 dispatches `LocationManager$handleLocation$2` to `mLocationListeners` (`f50904c`):
  1. `AppViewModel.onLocation(location, str)` (Line 1894): Updates current `LatLng F` and triggers route animator.
  2. `UserStatusManager.onLocation(location, str)`: Creates `LocationSnapshot`, calls `n()`, and posts `UserStatusEvent.LocationChanged` to coroutine Channel `f51089b` `[CONFIRMED_STATIC]`.

### Step 12: Database Insertion
- **Class:** `com.lovemaps.core.dao.LocationDao_Impl`
- **Entity:** `LocalLocationBean` (Lines 1674–1699)
- **Source Code:**
  ```java
  localLocationBean = new LocalLocationBean(
      0L, time12, latitude9, longitude9, fBoxFloat8, accuracy9, 
      false, true, strG8, zHasSpeed8, z20, location3.getProvider(), f8, 193, null
  );
  // Room database insertion
  LocationDao_Impl locationDao_Impl8 = (LocationDao_Impl) sVarW8;
  androidx.room.util.a.d(locationManager$handleLocation$3, locationDao_Impl8.f48103a, false, true, tVar);
  ```
  `[CONFIRMED_STATIC]`

### Step 13: Live Sharing Peer Transmission
- **Class:** `com.lovemaps.core.manage.location.marker.UserStatusManager`
- **Method:** `o(long j8, UploadLocationBean uploadLocationBean, String str)`
- Dispatches `TimUserInfoUpdateRequest` into Channel `f51090c`. The consumer coroutine formats the custom C2C message and invokes `TIMMessageManager.updateFriendLocation(partnerImId, locationJson)` `[CONFIRMED_STATIC]`.
