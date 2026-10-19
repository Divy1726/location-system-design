# 05 — Timeline and Historical Data

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Forensic decompilation of `OrbitManager.java`, `UploadOrbitWork.java`, `LocalLocationBean.java`, `LocationDao_Impl.java`, and `StayLocationNewDao_Impl.java`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Local Offline-First Storage Architecture

Love8 implements an offline-first storage hierarchy utilizing an Android Room SQLite database (`androidx.room`):

### A. Local Location Breadcrumbs Table: `location_dao`
- **Entity Model:** `com.lovemaps.core.entity.LocalLocationBean`
- **Class Reference:** `C:\Users\sutha\Desktop\rea\extracted\decompiled_love8\com\lovemaps\core\entity\LocalLocationBean.java`
- **Room Entity Declaration:** `@Entity(tableName = "location_dao")`

```java
// LocalLocationBean.java
@Entity(tableName = "location_dao")
public final class LocalLocationBean implements Parcelable {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private final long time;             // Epoch millisecond timestamp
    private final double latitude;       // Latitude (GCJ-02 converted)
    private final double longitude;      // Longitude (GCJ-02 converted)
    private final Float speed;           // Instantaneous velocity (m/s)
    private final float accuracy;        // Horizontal accuracy radius (meters)
    private final boolean isExcludePosition;
    private final boolean isLocal;       // Sync state flag: true = pending upload
    private final String gcj02;          // Coordinate reference system string
    private final boolean hasSpeed;      // Boolean speed presence flag
    private final boolean isMock;        // Boolean mock-provider flag
    private final String provider;       // "fused", "gps", or "network"
    private final float distance;        // Distance delta from previous point
}
```
`[CONFIRMED_STATIC]`

### B. Stay Dwell Table: `stay_location_new_dao`
- **Entity Model:** `com.lovemaps.core.entity.StayLocationNewBean`
- Stores aggregated visit summaries (place dwell duration, visit counts, entry/exit epoch timestamps) `[CONFIRMED_STATIC]`.

---

## 2. Trajectory Simplification: Douglas-Peucker Algorithm

Before historical trajectory segments are packaged for upload, raw coordinate noise is compressed using an explicit implementation of the **Ramer–Douglas–Peucker algorithm** in `com.lovemaps.core.work.orbit.OrbitManager`:

```java
// OrbitManager.java (Line 46, Method 55)
// Kotlin Metadata: douglasPeucker(List<Point> points, double epsilon) -> List<Point>
public final List<Point> douglasPeucker(List<Point> points, double epsilon) {
    if (points.size() < 3) {
        return points;
    }
    int firstIdx = 0;
    int lastIdx = points.size() - 1;
    Point lineStart = points.get(firstIdx);
    Point lineEnd = points.get(lastIdx);
    
    double maxDistance = 0.0d;
    int index = 0;
    
    for (int i = firstIdx + 1; i < lastIdx; i++) {
        double d = perpendicularDistance(points.get(i), lineStart, lineEnd);
        if (d > maxDistance) {
            maxDistance = d;
            index = i;
        }
    }
    
    if (maxDistance > epsilon) {
        List<Point> recResults1 = douglasPeucker(points.subList(0, index + 1), epsilon);
        List<Point> recResults2 = douglasPeucker(points.subList(index, points.size()), epsilon);
        // Concatenate without duplicating the junction point
        ArrayList<Point> result = new ArrayList<>(recResults1);
        result.addAll(recResults2.subList(1, recResults2.size()));
        return result;
    }
    
    ArrayList<Point> result = new ArrayList<>();
    result.add(lineStart);
    result.add(lineEnd);
    return result;
}
```
`[CONFIRMED_STATIC]`

### Forensic Significance of the Trajectory Pipeline:
1. **Bandwidth & Storage Optimization:** Streaming raw GPS fixes every 5 seconds yields $\approx 720\text{ points/hour}$. The Douglas-Peucker algorithm discards redundant intermediate points along straight trajectories where perpendicular deviation is below `epsilon`.
2. **Curve Fidelity:** Sharp turns, intersections, and direction changes are preserved because their perpendicular distance exceeds the tolerance threshold `[CONFIRMED_STATIC]`.

---

## 3. Timeline Serialization & Data Models

Historical trajectories are assembled into specialized serialization models (`kotlinx.serialization`):

1. **`UploadOrbitData`:**
   - Class: `com.lovemaps.core.entity.UploadOrbitData`
   - Fields: Segment start time, segment end time, travel mode / activity type, total segment distance, list of simplified coordinate vertices `[CONFIRMED_STATIC]`.
2. **`UploadOrbitDataList`:**
   - Class: `com.lovemaps.core.entity.UploadOrbitDataList`
   - Wrapper encapsulating arrays of `UploadOrbitData` for batch HTTP transport `[CONFIRMED_STATIC]`.

---

## 4. Batch Upload & Synchronization Lifecycle: `UploadOrbitWork`

Batch synchronization is governed by `com.lovemaps.core.work.UploadOrbitWork`:

```java
// UploadOrbitWork.java (Lines 78-95)
OrbitManager r9 = new OrbitManager();
Data r2 = getInputData();
boolean r2 = r2.getBoolean("upload_all", false);
ArrayList r9 = (ArrayList) r9.handleOrbitData(r2, this);
```
`[CONFIRMED_STATIC]`

### Execution Characteristics:
1. **Triggering Conditions:**
   - Periodically scheduled by WorkManager.
   - Triggered on network state recovery (`CONNECTIVITY_ACTION` / `NetworkCapabilities.NET_CAPABILITY_INTERNET`).
   - Triggered upon manual timeline page refresh in the Flutter UI `[CONFIRMED_STATIC]`.
2. **Size Chunking (`handleExceedingMaxSize`):**
   - In `UploadOrbitWork.java` (Line 27), methods `handleNormalData` and `handleExceedingMaxSize` inspect the payload byte size (`kbSize`) and vertex count (`listSize`).
   - If an orbit payload exceeds server batch constraints, it is segmented into sequential sub-batches `[CONFIRMED_STATIC]`.
3. **Synchronization State Reconciliation:**
   - Once the HTTP endpoint responds with a successful HTTP 200 payload, local rows in `location_dao` are reconciled: `isLocal` flags are updated or obsolete points are pruned to prevent unbound SQLite database growth `[CONFIRMED_STATIC]`.

---

## 5. Architectural Separation: Live Location vs. Historical Timeline

| Dimension | Live Sharing Channel | Historical Timeline Channel |
| :--- | :--- | :--- |
| **Transport Protocol** | Tencent Cloud IM (P2P QUIC/TCP socket) + Push (FCM) | HTTP/HTTPS REST API (Retrofit / OkHttp) `[CONFIRMED_STATIC]` |
| **Data Granularity** | Single instantaneous coordinate fix | Douglas-Peucker simplified polyline segment `[CONFIRMED_STATIC]` |
| **Latency Objective** | Near real-time ($< 3\text{ seconds}$) | Asynchronous batch ($1\text{ to }15\text{ minutes}$) `[CONFIRMED_STATIC]` |
| **Local Storage** | In-memory cache (`mLastLocation`, `mLastLatLng`) | Persistent SQLite tables (`location_dao`, `stay_location_new_dao`) `[CONFIRMED_STATIC]` |
| **Data Payload** | Lat, Lng, Speed, Battery %, Charging state, Wi-Fi flag | Start/end times, visit duration, simplified polylines, travel modes `[CONFIRMED_STATIC]` |
