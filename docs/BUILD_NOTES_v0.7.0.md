DrishtiNav v0.7.0 — Build & Test Notes
========================================

WHAT'S NEW (4 upgrades, built 2026-10-17)
-----------------------------------------
1. MAPS/PLACES FIXED (nav/Geocoder.kt rewritten)
   - Search ab India-biased hai (countrycodes=in) + tumhari GPS location
     ke aas-paas viewbox bias — local dukaan/street ab milegi, sirf
     famous places nahi.
   - Photon API fallback agar Nominatim fail ho.
   - Hindi queries pe Hindi naam prefer karta hai.

2. UI OVERHAUL (Material 3 dark, teal #5bb5a5 kept)
   - Naya bottom control deck, bade touch targets (72dp pills),
     redesigned destination dialog (distance hints: "850 m away"),
     grouped Settings screen, glowing sonar with urgent shockwave.
   - TalkBack accessibility fully preserved.

3. BLUR-ON-OPEN FIXED (ar/ArCoreManager.kt)
   - Root cause: ARCore ke shuruati 1-3 sec ke frames (camera focus
     converge hone se pehle) blurry hote hain — app unhe raw dikha
     raha tha. Ab pehle TRACKING state ka wait hota hai, tab tak
     clean "Starting camera..." placeholder.
   - Bonus fix: STOP -> START pe black preview (stale texture bug) bhi fixed.

4. ACCURACY BOOST (perception/*)
   - Temporal smoothing: object 2 consecutive frames me dikhe tabhi alert
     (flicker/phantom alerts khatm).
   - Per-class thresholds (chhoti cheezon pe strict, person pe 0.5).
   - Ranging fix: box centre ki jagah 3x3 grid ka 30th percentile —
     distance ab nearest surface ka, peeche ki deewar ka nahi.
   - NOTE: distances thodi nearer report hongi (yeh sahi hai collision
     ke liye) — ek field walk karke feel confirm kar lena.

HOW TO BUILD (laptop pe Android Studio me)
------------------------------------------
1. Is zip ko extract karo.
2. Android Studio me "Open" -> extracted folder select karo.
3. Gradle sync ka wait karo (pehli baar 5-10 min lag sakta hai).
4. Build > Build Bundle(s)/APK(s) > Build APK(s).
5. app/build/outputs/apk/debug/app-debug.apk phone me install karo.

TEST CHECKLIST (phone pe)
--------------------------
[ ] App kholo -> START dabao -> 1-2 sec "Starting camera..." phir SHARP
    camera feed (koi blur nahi).
[ ] STOP -> START dobara -> camera feed wapas aana chahiye (black nahi).
[ ] NAVIGATE -> apne area ki koi local jagah search karo -> sahi local
    results aane chahiye (famous global matches nahi).
[ ] Results me distance hints dikhne chahiye ("850 m away").
[ ] Camera kisi cheez pe rakho -> alerts stable aane chahiye, flicker nahi.
[ ] Nayi UI dekho — koi button/text cut raha ho to screenshot bhejo.

VERSION: v0.7.0 (2026-10-17) — previous release v0.6.1
