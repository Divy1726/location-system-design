# 13 — Movement and Stay Detection Algorithms

**Target Package:** `ltd.love8.couples.relationship`  
**Base APK SHA-256:** `def1b3686e4da2e4f36bcf85e68b155b2e4b1a7ef134c5f3e803353450689619`  
**Evidence Standard:** Forensic decompilation of `LocationManager.java`, `PlaceTransitionConfirmation.java`, `GeofencePlacesSendManager.java`, and `StayLocationNewBean.java`.  
**Evidentiary Tags:** `[CONFIRMED_STATIC]`, `[OBSERVED_RUNTIME]`, `[INFERRED]`, `[UNKNOWN]`

---

## 1. Provider Speed vs. Derived Speed Analysis

A central question in mobile telemetry is whether hardware-reported speed or derived displacement speed is preferred:

| Speed Type | Calculation Source | Code Location | Primary Purpose in Love8 |
| :--- | :--- | :--- | :--- |
| **Hardware-Reported Speed** | `location.getSpeed()` (GNSS Doppler shift / accelerometer) | `LocationManager.java` (Line 1665) | Stored in `LocalLocationBean.speed` and transmitted to partner. Evaluated in stationary Wi-Fi check (`speed >= 5.0f && hasSpeed()`) `[CONFIRMED_STATIC]`. |
| **Derived Displacement Speed** | `(distance / deltaSec) * 3.6f` | `LocationManager.java` (Line 811) | Used strictly as an internal physical anomaly filter to discard teleportation spikes ($> 200.0\text{ km/h}$) `[CONFIRMED_STATIC]`. |
| **iOS-Compatible Normalized Speed**| `EXTKt.getCompatibleIosSpeed(f)` | `EXTKt.java` (Line 2874) | Normalizes negative Doppler velocities (`if (f <= 0.0f) return 0.0f; return f;`) `[CONFIRMED_STATIC]`. |
| **UI Display Speed** | `EXTKt.getSpeedStringPair(f)` | `EXTKt.java` (Line 4754) | Converts m/s to km/h ($\times 3.6$) or mph ($\times 2.23694$). If calculated value $< 0.5$, clamps display to `0.0` `[CONFIRMED_STATIC]`. |

---

## 2. Place Transition State Machine (`PlaceTransitionConfirmation`)

To prevent boundary flapping (where GPS drift causes repeated arrival and departure notifications when walking near a fence), Love8 uses a dedicated stateful confirmation engine: `com.lovemaps.core.manage.PlaceTransitionConfirmation` (`classes6.dex`).

### Candidate Model & Hardcoded Constants
```java
// PlaceTransitionConfirmation.java (Lines 83-85, 112-115)
public PlaceTransitionConfirmation(int i5, long j8, int i7, DefaultConstructorMarker defaultConstructorMarker) {
    this((i7 & 1) != 0 ? 3 : i5,           // Default: 3 points
         (i7 & 2) != 0 ? 60000L : j8);     // Default: 60,000 ms (60 seconds)
}
```
`[CONFIRMED_STATIC]`
- **`requiredPointCount`:** `3` consecutive fixes.
- **`requiredDurationMillis`:** `60,000 ms` (1 full minute).
- **Supported Transitions:** `4` = `ENTER / DWELL`, `2` = `EXIT`.

### Exact Confirmation Algorithm (`PlaceTransitionConfirmation.a`)
```java
// PlaceTransitionConfirmation.java (Lines 87-110)
public final Integer a(int placeId, Integer transitionType, long locationTimeMillis, boolean isAccuracyValid) {
    if (!isAccuracyValid) return null;
    if (transitionType == null) {
        this.f50954c = null; // Invalidate candidate
        return null;
    }
    Candidate candidate = this.f50954c;
    // New place or direction reversal
    if (candidate == null || candidate.placeId != placeId || candidate.transitionType != transitionType.intValue()) {
        this.f50954c = new Candidate(placeId, transitionType.intValue(), locationTimeMillis, locationTimeMillis, 1);
        return null; // Not yet confirmed
    }
    if (locationTimeMillis <= candidate.lastLocationTimeMillis) {
        return null; // Reject non-monotonic timestamps
    }
    // Accumulate points and update timestamp
    Candidate candidate2 = new Candidate(candidate.placeId, candidate.transitionType, candidate.startedAtMillis, locationTimeMillis, candidate.pointCount + 1);
    this.f50954c = candidate2;
    
    // DUAL-CRITERIA EVALUATION
    if (candidate2.pointCount < this.f50952a || (candidate2.lastLocationTimeMillis - candidate2.startedAtMillis) < this.f50953b) {
        return null; // Suppressed until BOTH criteria are met
    }
    this.f50954c = null; // Reset candidate
    return Integer.valueOf(candidate2.transitionType); // CONFIRMED!
}
```
`[CONFIRMED_STATIC]`

---

## 3. State Transition Matrix

| Current Engine State | Incoming Event | Condition Evaluated | Next Engine State | Outbound Action |
| :--- | :--- | :--- | :--- | :--- |
| **IDLE / NO CANDIDATE** | Inside Place Boundary | `SphericalUtil.computeDistance <= radius` | `CANDIDATE_ENTER`<br>(`count=1`, `startedAt=t`) | None (alert suppressed) `[CONFIRMED_STATIC]` |
| **CANDIDATE_ENTER** | Inside Place Boundary | `count < 3` OR `(t - startedAt) < 60s` | `CANDIDATE_ENTER`<br>(`count++`, `lastTime=t`) | None (accumulating) `[CONFIRMED_STATIC]` |
| **CANDIDATE_ENTER** | Inside Place Boundary | `count >= 3` AND `(t - startedAt) >= 60s` | **CONFIRMED_ENTER**<br>(candidate reset to null) | Enqueue `places_send_enter_work`<br>Cancel `places_send_exit_work` `[CONFIRMED_STATIC]` |
| **CANDIDATE_ENTER** | Moves Outside Boundary | Direction reversal before 60s | `CANDIDATE_EXIT`<br>(`count=1`, `startedAt=t`) | Prior Enter cancelled; Enter alert never sent! `[CONFIRMED_STATIC]` |
| **INSIDE PLACE** | Moves Outside Boundary | `SphericalUtil.computeDistance > radius` | `CANDIDATE_EXIT`<br>(`count=1`, `startedAt=t`) | None (alert suppressed) `[CONFIRMED_STATIC]` |
| **CANDIDATE_EXIT** | Moves Outside Boundary | `count >= 3` AND `(t - startedAt) >= 60s` | **CONFIRMED_EXIT**<br>(candidate reset to null) | Enqueue `places_send_exit_work`<br>Cancel `places_send_enter_work` `[CONFIRMED_STATIC]` |
| **CANDIDATE_EXIT** | Moves Back Inside Boundary | Re-enters place before 60s | `CANDIDATE_ENTER`<br>(`count=1`, `startedAt=t`) | Prior Exit cancelled; Exit alert never sent! `[CONFIRMED_STATIC]` |

---

## 4. WorkManager Opposite Job Cancellation

When a transition is confirmed by `PlaceTransitionConfirmation.a`, `GeofencePlacesSendManager.a` executes reciprocal cancellation:

```java
// GeofencePlacesSendManager.java (Lines 101-105)
String str = (transitionType == 4) ? "places_send_exit_work" : "places_send_enter_work";
WorkManager.Companion.a(applicationA).cancelUniqueWork(str);
```
`[CONFIRMED_STATIC]`

- **Why this is critical:** If a user briefly walks outside for 45 seconds to pick up mail and walks back in, any pending exit task scheduled with backoff is permanently cancelled before its network packet can reach the partner's device `[CONFIRMED_STATIC]`.

---

## 5. Stay Record Persistence (`StayLocationNewBean`)

Confirmed visits and dwell durations are persisted locally in Room database table `stay_location_new_dao`:
- **Model:** `com.lovemaps.core.entity.StayLocationNewBean`
- **Fields:**
  - `roomId`: Auto-incrementing primary key.
  - `latitude`, `longitude`: Place centroid coordinates.
  - `userId`: Identifier of partner or self.
  - `stayCount`: Total visits recorded at this place.
  - `topTipCloseTime`, `headerCloseTime`: User interaction UI dismissal timestamps `[CONFIRMED_STATIC]`.
- **Stay Duration Calculation:** Determined by subtracting the confirmed entry timestamp from the confirmed exit timestamp `[CONFIRMED_STATIC]`.
