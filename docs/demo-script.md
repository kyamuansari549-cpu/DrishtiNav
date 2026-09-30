# DrishtiNav — 3-Minute Demo Script

For hackathons, project expos, and placement interviews. One phone
(Redmi Note 11 Pro 5G or any ARCore + Depth API device), internet on for
the navigation half.

## 0:00–0:20 — The problem (say this, don't read it)

"1.3 billion people live with vision impairment. A white cane detects
what's at ground level, an arm's length away. DrishtiNav is a phone app
that extends that awareness — it sees obstacles ahead and speaks them,
with real distance: *'chair, 1.5 meters, ahead.'*"

## 0:20–1:10 — Phase 1: obstacle alerts (live)

1. Open the app, tap **START SCANNING**, hold the phone at chest height,
   camera forward.
2. Walk slowly toward a chair ~2 m away → app speaks
   *"chair, 1.8 meters, ahead"* (queued tone + double-tap vibration).
3. Keep walking to < 1 m → *"Stop. chair, 0.7 meters, ahead."*
   (interrupts speech + strong triple buzz).
4. Move the chair to your side → **silence** (not in the walking path).
   Point for the judges: "It doesn't narrate the world — it only speaks
   when something is in your way. Speech is a scarce channel."

## 1:10–2:10 — Phase 2: navigation (live)

1. Tap **NAVIGATE** → search a nearby landmark → tap it.
2. *"Route found, 800 meters. Head north on …"* — show the instruction
   strip updating.
3. Walk; prompts arrive at ~100/50/20 m: *"In 50 meters, turn left onto …"*
4. Deliberately take a wrong turn → *"Rerouting."* → new route planned
   from the current position.
5. The key line: "And the whole time, the obstacle scanner keeps running
   *underneath* the navigation — an urgent obstacle interrupts the
   turn-by-turn prompt. Navigation never outranks safety."

## 2:10–2:40 — Settings (fast)

Open **SETUP**: speech rate, urgent/near distance bands, keep-screen-on,
side-obstacle announcements. "A blind commuter in a quiet lane and one
on a noisy road need different alert distances — that's not a feature
list, it's the difference between usable and uninstallable."

## 2:40–3:00 — Honest close

"What it is: an assistive aid — never a replacement for a cane or guide
dog. What's real: on-device detection, ARCore depth in millimetres,
walking routes with auto-reroute, all working on a mid-range phone.
What's next: curb/staircase detection and indoor anchors."

## If something fails live

- No GPS indoors → navigate with a `lat,lng` typed manually; or demo
  rerouting logic verbally.
- No internet → obstacle scanning is fully on-device; do Phase 1 only
  and say "navigation needs the route service".
- Never fake it — judges reward the honest fallback over a staged video.
