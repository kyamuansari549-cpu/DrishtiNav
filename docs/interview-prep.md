# DrishtiNav — Interview Prep

## The 30-second pitch

"DrishtiNav is an Android navigation aid for visually impaired users.
It combines ARCore depth sensing with on-device object detection to
speak obstacle alerts with real metric distance, and layers GPS
turn-by-turn walking navigation underneath — urgent obstacles always
interrupt navigation prompts. Everything time-critical runs on-device;
only route planning uses the network."

## Architecture (draw this on the board)

```
Camera (ARCore session, Depth API)
  → GL thread: preview + frame acquire
  → Perception thread (1 frame in flight, extras dropped)
      YUV→Bitmap→portrait → MediaPipe EfficientDet-Lite0 (~14 MB, bundled)
      → depth-patch median per detection → label + metres + left/center/right
  → AlertPolicy (cooldowns, priority)
      → TTS (urgent=FLUSH, near=ADD) + haptics

GPS (FusedLocationProvider, nav-only)
  → NavigationEngine: 100/50/20 m prompts, step advance, arrival, off-route
  → auto-reroute via OSRM → speaks through the same TTS at lower priority
```

## Questions you'll get, and the answers

**Why ARCore depth instead of just the detection box size?**
Box size is a proxy — a small nearby object and a large far one look
identical. Depth gives millimetre distance per pixel, so "1.5 meters" is
measured, not guessed. The Depth API is available on mid-range phones
like the Redmi Note 11 Pro 5G, no LiDAR needed.

**Why throttled to ~2.5 fps detection?**
Detection is the expensive step; depth runs every frame. Throttling plus
dropping frames when one is in flight guarantees the pipeline never
lags the camera — stale alerts are worse than no alerts for a blind user.

**How do you avoid TTS spam?**
Three mechanisms: urgency bands (only < near-distance, walking path),
per-object cooldowns (2.5 s urgent / 5 s near), and a global 1.2 s gap
between non-urgent utterances. Urgent uses QUEUE_FLUSH so "Stop!" always
preempts.

**How does navigation interact with obstacle alerts?**
Shared TTS with strict priority: navigation prompts are QUEUE_ADD,
urgent obstacles are QUEUE_FLUSH. The NavigationEngine also has a 4 s
minimum gap between prompts. Safety information can never be starved
by navigation chatter.

**Off-route detection without map-matching?**
Distance-to-maneuver trend: if it grows by > 30 m or exceeds 80 m, the
user has diverged → "Rerouting", replan from the current fix, 15 s
cooldown so it can't thrash.

**Why OSRM + Nominatim instead of Google Maps SDK?**
No API key, no billing, no quota anxiety — appropriate for an
assistive app and a student project. Trade-off is honest: the public
demo server has usage limits; a production version would self-host OSRM.

**Biggest technical risk you managed?**
Left/right mirroring after the 90° portrait rotation — the depth
lookup uses the inverse rotation mapping, with a marked calibration
constant verified by a field test (hand on the left → "to your left").

**What would you do with 4 more weeks?**
Curb/staircase detection model, indoor navigation via ARCore anchors,
and a foreground-service mode so scanning survives screen-off.

## Numbers to have ready

- ~14 MB on-device model, zero network for detection
- ~2.5 fps detection, depth every frame
- < 1 m urgent / 1–2 m near bands (user-tunable in Phase 3)
- 100/50/20 m navigation prompt thresholds
- minSdk 29, targetSdk 35, Kotlin, ARCore 1.54
