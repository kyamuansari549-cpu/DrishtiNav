# DrishtiNav — AR Navigation Aid for the Visually Impaired

An **AR-assisted navigation aid for visually impaired users**, built
phase by phase as a real project:

- **Phase 1:** point the phone camera forward → spoken + haptic obstacle
  alerts with real metric distance — fully on-device, no network needed.
- **Phase 2:** press NAVIGATE → pick a destination (search, `lat,lng`, or a
  saved place) → walking route with spoken turn-by-turn guidance, layered
  *under* the obstacle alerts (urgent obstacles still interrupt navigation
  speech).
- **Phase 3:** settings — speech rate, alert distance bands, keep-screen-on,
  side-obstacle announcements; saved places management.
- **Phase 4:** release polish — launcher icon, demo script, interview prep,
  privacy policy, Play Store checklist.

> Safety: this is an **assistive aid, not a replacement** for a cane, guide dog,
> or sighted assistance. It augments awareness; it does not guarantee safety.

## What Phase 1 does

- Opens an ARCore session with the **Depth API** (per-pixel distance in mm)
- Runs **on-device object detection** (MediaPipe, EfficientDet-Lite0, ~14 MB model
  bundled in `assets/`) at ~2.5 fps, throttled so the pipeline never lags the camera
- Fuses each detection with depth: label + distance (m) + left/center/right zone
- **Speaks** alerts via TTS (`"Stop. chair, 0.8 meters, ahead."`) and fires
  **distinct haptic patterns** per urgency level
- Alert policy with teeth: urgent (< 1 m) interrupts speech; near (1–2 m, walking
  path only) queues; everything else stays silent with cooldowns so speech never
  becomes noise

## Architecture

```
Camera (ARCore session, Depth API on)
   │  GL thread: BackgroundRenderer draws preview, acquires frames
   ▼
Perception thread (HandlerThread, max 1 frame in flight — extras dropped)
   │  FrameConverter: YUV_420_888 → Bitmap → rotate to portrait
   │  ObstacleDetector (MediaPipe, EfficientDet-Lite0, bundled .tflite)
   │  ObstacleFusion: box centre → depth patch median → distance + zone
   ▼
AlertPolicy (cooldowns, priority queue)
   ├── SpeechEngine (TTS, urgent = QUEUE_FLUSH, near = QUEUE_ADD)
   └── HapticEngine (urgent = triple buzz, near = double tap)

Phase 2 — navigation layer (runs alongside the pipeline above):

```
NavigateDialog (search via Nominatim / raw lat,lng / saved places)
   │  RoutePlanner: OSRM walking route → Route(steps)
   ▼
NavigationEngine (GPS fixes from LocationTracker, high-accuracy, nav-only)
   │  100/50/20 m prompts → speakQueued ("In 50 meters, turn left…")
   │  step advance < 12 m · arrival < 20 m · off-route → auto-reroute
   ▼
SpeechEngine (QUEUE_ADD — obstacle URGENT still preempts via QUEUE_FLUSH)
```

SavedPlaces persists named destinations (home, office…) as JSON in the
app's private files dir; long-press a dialog entry to save/remove.
```

## Requirements

- Android Studio Ladybug (2024.2) or newer — bundles JDK 17, which AGP 8.7 needs
- A physical ARCore + Depth API phone (verified: **Redmi Note 11 Pro 5G** ✅)
- USB debugging enabled on the phone

## Build & run

1. In Android Studio: **File → Open** → select the `drishtinav/` folder.
2. Let Gradle sync finish (first sync downloads AGP 8.7.3, Kotlin 2.1.0, SDK 35 —
   needs internet, ~5–10 min).
3. Connect the phone via USB, enable USB debugging, accept the RSA prompt.
4. Press **Run ▶**. Grant the camera permission; approve the
   "Google Play Services for AR" install/update prompt if shown.
5. Tap **START SCANNING**, hold the phone at chest height, camera forward.

## Field test protocol (do these in order)

1. **Left/right calibration.** Hand on the left side of the frame → app must say
   "to your left". If mirrored, flip the `sensorV` mapping in
   `ObstacleFusion` (marked with a comment).
2. **Static obstacle.** Chair 2 m ahead → within ~1 s: "chair, 2.0 meters, ahead."
   Walk closer → under 1 m: "Stop. chair, …" + strong vibration.
3. **Side discipline.** Object 1.5 m to the side → silent (not in walking path).
   Move it in front → announced.
4. **Cooldown check.** Stand still facing a chair → it speaks once, then stays
   quiet (~5 s), not a loop.
5. **No-depth fallback.** Cover the lens briefly / dark room → status shows
   "no depth yet", no crash, recovers when uncovered.

Report back: which tests pass, any crash logs from Logcat (filter `DrishtiNav`).

## Phase 2 test protocol (needs GPS + internet)

1. **Destination search.** NAVIGATE → search your area → tap a result →
   hear "Route found, X meters/kilometers. Head …".
2. **Turn prompts.** Walk the route → prompts at ~100/50/20 m before each
   turn ("In 50 meters, turn left onto MG Road").
3. **Off-route.** Deliberately take a wrong turn → "Rerouting." → new route
   from your position.
4. **Arrival.** Reach the destination → "You have arrived at your destination."
5. **Obstacle priority.** While navigating, walk at a chair < 1 m →
   "Stop. chair…" must interrupt the navigation prompt.
6. **Offline fallback.** Airplane mode on → NAVIGATE → enter `lat,lng`
   manually works for the dialog, route planning reports the network error
   cleanly (no crash).

## Phase 3 — settings & personalization

- **SETUP button** (top-right) → big-text, TalkBack-friendly settings screen:
  - Speech rate (0.8x–1.5x)
  - Urgent distance (0.5–2.0 m, interrupts speech)
  - Near distance (1.0–4.0 m, announced ahead; always wider than urgent)
  - Keep screen on while scanning (default on — no mid-walk lock)
  - "Announce side obstacles too" (default off — only the walking path speaks)
  - **Hindi guidance** (default off — English): when on, every spoken prompt
    switches to Hindi — obstacle alerts ("सामने 1.5 मीटर पर कुर्सी है"),
    navigation prompts ("50 मीटर में, बाईं ओर मुड़ें"), arrival, rerouting.
    Object labels use a built-in 80-word Hindi map; OSRM turn verbs are
    translated on-device (street names stay as-is). Needs the Hindi (hi-IN)
    voice in Android's Text-to-speech settings; falls back to English with
    a notice when it is missing.
- All settings apply live when you return to the main screen; they persist
  across restarts.
- Saved places: long-press any destination result to save it (★), long-press
  a saved place to remove it. Saved places appear at the top of the dialog.

## Known limitations

- Alert distances are tuned for walking speed; running/cycling not supported.
- Detection classes are COCO-80 (chair, person, car…); no curb/staircase model yet.
- Portrait-locked; left/right mapping has a marked calibration point
  (`ObstacleFusion`, verified by the field-test protocol).
- `tasks-vision:0.10.35` native libs are 4 KB page-aligned; before a Play Store
  release targeting Android 15+, bump to `tasks-vision:1.0.0` (16 KB aligned).

## Play Store release checklist

- [ ] Field-test protocols (Phase 1 + Phase 2) pass on the Redmi Note 11 Pro 5G
- [ ] Bump `tasks-vision` to 1.0.0 for 16 KB page alignment (Android 15+)
- [ ] Fill the contact email in `docs/privacy-policy.md`, link it in the listing
- [ ] Play Console: app category, content rating questionnaire, data-safety
      form (location = navigation, camera = on-device only)
- [ ] Internal testing track → 12+ testers → production
- [ ] Store listing: feature graphic, 2+ screenshots, short description
      ("AR navigation aid: speaks obstacles with real distance + walking guidance")

## Tech

Kotlin · ARCore 1.54 (Depth API) · MediaPipe Tasks Vision 0.10.35
(EfficientDet-Lite0, on-device) · Android TTS · VibrationEffect · OpenGL ES 2.0
camera passthrough · Play Services Location (FusedLocationProvider) ·
OSRM walking routes + Nominatim search (no API keys) ·
minSdk 29 / targetSdk 35
