# 03 — Location Acquisition and Accuracy

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Forensic decompilation and static analysis of `GoogleLocation`, `GoogleLocationConfig`, `NativeLocation`, and `LocationManager`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Provider Selection Architecture

Love8 employs a dual-provider tiered architecture orchestrated by the singleton `com.lovemaps.core.manage.LocationManager`:

1. **Primary Provider — Google Play Services Fused Location (`GoogleLocation`):**
   - Implemented in: `com.lovemaps.core.manage.location.GoogleLocation`
   - Leverages `com.google.android.gms.location.FusedLocationProviderClient`.
   - Selected by default on devices equipped with active Google Mobile Services (GMS) `[CONFIRMED_STATIC]`.
2. **Fallback Provider — Android Framework Location (`NativeLocation`):**
   - Implemented in: `com.lovemaps.core.manage.location.NativeLocation`
   - Uses `android.location.LocationManager` with `GPS_PROVIDER` and `NETWORK_PROVIDER`.
   - Utilized when GMS is absent, disabled, or throws an unrecoverable GooglePlayServicesNotAvailableException `[CONFIRMED_STATIC]`.

---

## 2. Location Request Parameters & Configuration

The configuration parameters governing continuous location tracking are encapsulated in the serializable parcelable data class `com.lovemaps.core.manage.location.GoogleLocationConfig` (`classes2.dex`):

### Exact Decompiled Default Values (`GoogleLocationConfig.java`, Lines 64-91)
```java
public /* synthetic */ GoogleLocationConfig(
    int i5, int i7, long j8, long j9, float f3, boolean z10, boolean z11, e0 e0Var
) {
    this.priority = (i5 & 1) == 0 ? 100 : i7;                 // Default: 100 (PRIORITY_HIGH_ACCURACY)
    this.intervalMillis = (i5 & 2) == 0 ? 5000L : j8;           // Default: 5000 ms (5 seconds)
    this.minUpdateIntervalMillis = (i5 & 4) == 0 ? 2500L : j9;  // Default: 2500 ms (2.5 seconds)
    this.minUpdateDistanceMeters = (i5 & 8) == 0 ? 0.0f : f3;   // Default: 0.0 meters
    this.revealAllTheDetailsConfig = (i5 & 16) == 0 ? false : z10;
    this.conversionGps = (i5 & 32) == 0 ? true : z11;           // Default: true (GCJ-02 conversion)
}
```
`[CONFIRMED_STATIC]`

### Request Construction in `GoogleLocation.c` (Lines 135-145)
```java
LocationRequest locationRequestBuild = new LocationRequest.Builder(googleLocationConfig.getIntervalMillis())
    .setPriority(100)
    .setIntervalMillis(googleLocationConfig.getIntervalMillis())
    .setMinUpdateIntervalMillis(googleLocationConfig.getMinUpdateIntervalMillis())
    .setMinUpdateDistanceMeters(googleLocationConfig.getMinUpdateDistanceMeters())
    .build();

Task<Void> taskRequestLocationUpdates = ((FusedLocationProviderClient) this.f51024b.getValue())
    .requestLocationUpdates(
        locationRequestBuild, 
        (LocationCallback) this.f51025c.getValue(), 
        Looper.getMainLooper()
    );
```
`[CONFIRMED_STATIC]`

### Parameter Specifications & Implications:
- **Priority `100` (`PRIORITY_HIGH_ACCURACY`):** Forces hardware GPS receiver activation alongside cellular and Wi-Fi assisted positioning `[CONFIRMED_STATIC]`.
- **Nominal Interval `5000 ms` (5.0s):** Location fixes are requested every 5 seconds `[CONFIRMED_STATIC]`.
- **Fastest Interval `2500 ms` (2.5s):** The app is willing to ingest fixes as frequently as every 2.5 seconds if another application triggers them `[CONFIRMED_STATIC]`.
- **Displacement Filter `0.0m`:** No displacement filter is applied at the OS driver level. All fixes are delivered to the app, delegating all stationary filtering and outlier rejection to the application's native code `[CONFIRMED_STATIC]`.

---

## 3. Coordinate Transformation: WGS84 to GCJ-02 Projection

In `LocationManager.h` (`handleLocation`), raw coordinates emitted by the hardware GNSS receiver (WGS84) undergo an explicit coordinate transformation before local storage, geofencing, or network transmission:

```java
// LocationManager.java (Lines 759-762)
LatLng latLng17 = (
    Intrinsics.areEqual(location.getProvider(), "mock_test_location") || 
    Intrinsics.areEqual(location.getProvider(), "fcm_location_provider")
) ? new LatLng(location.getLatitude(), location.getLongitude()) 
  : EXTKt.gpsToGcj02(new LatLng(location.getLatitude(), location.getLongitude()));

String str10 = "原始坐标:" + location.getLatitude() + "," + location.getLongitude() 
    + "\n转完坐标:" + latLng17.latitude + "," + latLng17.longitude 
    + "\n时区:" + TimeZone.getDefault().getID();

location.setLatitude(latLng17.latitude);
location.setLongitude(latLng17.longitude);
```
`[CONFIRMED_STATIC]`

### Forensic Observations:
1. **GCJ-02 Conversion (`EXTKt.gpsToGcj02`):** Raw WGS-84 coordinates are transformed into the Chinese national GCJ-02 ("Mars coordinates") datum system `[CONFIRMED_STATIC]`.
2. **Provider Exceptions:** Coordinates received from test simulations (`"mock_test_location"`) or push synchronizations (`"fcm_location_provider"`) bypass transformation because they are assumed to be already pre-adjusted `[CONFIRMED_STATIC]`.
3. **Forensic Logging:** Love8 logs the raw latitude/longitude, converted latitude/longitude, and default timezone ID into its diagnostic log stream `[CONFIRMED_STATIC]`.

---

## 4. Ingestion-Stage Validation & Accuracy Rejection

Prior to admitting any location fix into local storage, geofence evaluators, or upload queues, `LocationManager.handleLocation` evaluates two strict preliminary gates:

### Gate 1: Horizontal Accuracy Threshold Filter
```java
// LocationManager.java (Lines 769-777)
if (location.getAccuracy() > 200.0f) {
    LoggerExtKt.needUploadLoggerE(
        "排除该位置 " + str + "-> accuracy == " + location.getAccuracy() 
        + " latLng:" + location.getLatitude() + "," + location.getLongitude(), 
        "LocationManager", 555
    );
    locationManager.f50903b = locationManager.f50903b + 1; // Increment consecutive filter count
    return Boolean.FALSE;
}
```
`[CONFIRMED_STATIC]`
- **Cutoff:** Exactly `200.0 meters`. Any fix with reported radius $> 200\text{ m}$ is rejected as inaccurate.
- **Side Effect:** Increments `mConsecutiveFilterCount` (`f50903b`).

### Gate 2: Monotonic Timestamp Consistency Filter
```java
// LocationManager.java (Lines 784-799)
if (location.getTime() < location4.getTime()) {
    long time = location.getTime();
    long time2 = location4.getTime();
    StringBuilder sbR = r8.r(time, "排除该位置 ", str, 
        "-> 新位置时间小于最后缓存位置的时间 location.time == ");
    sbR.append(time);
    sbR.append(" lastLocation.time == ");
    sbR.append(time2);
    sbR.append(" latLng:");
    sbR.append(location.getLatitude());
    sbR.append(",");
    sbR.append(location.getLongitude());
    LoggerExtKt.needUploadLoggerE(sbR.toString(), "LocationManager", 555);
    locationManager.f50903b++;
    return Boolean.FALSE;
}
```
`[CONFIRMED_STATIC]`
- **Cutoff:** Fixes with a timestamp older than the current `lastLocation` (`f50906f`) are rejected to prevent out-of-order delivery corruption from parallel provider callbacks.

---

## 5. Single-Shot Location Acquisition

In addition to continuous streaming, Love8 supports on-demand asynchronous coordinate resolution:

1. **`getCurrentLocationWork(String from)`:**
   - Wraps `FusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken)`.
   - Used by WorkManager jobs (`RequestCurrentLocationWork`, `SendLocationInfoWorker`) when fresh coordinates are needed immediately during background wakeups `[CONFIRMED_STATIC]`.
2. **`getLastLocationWork()`:**
   - Wraps `FusedLocationProviderClient.getLastLocation()`.
   - Checks permission gate `com.lovemaps.core.permission.i.l()`. Returns cached system fix without spinning up GPS hardware if recent `[CONFIRMED_STATIC]`.
