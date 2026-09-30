package com.drishtinav.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.drishtinav.app.ar.ArCoreManager
import com.drishtinav.app.ar.BackgroundRenderer
import com.drishtinav.app.nav.LocationTracker
import com.drishtinav.app.nav.NavigateDialog
import com.drishtinav.app.nav.NavigationEngine
import com.drishtinav.app.nav.RoutePlanner
import com.drishtinav.app.nav.SavedPlaces
import com.drishtinav.app.output.AlertPolicy
import com.drishtinav.app.output.AppStrings
import com.drishtinav.app.output.HapticEngine
import com.drishtinav.app.output.SpeechEngine
import com.drishtinav.app.settings.AppSettings
import com.drishtinav.app.settings.SettingsActivity
import com.drishtinav.app.ui.SonarView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.drishtinav.app.perception.FrameConverter
import com.drishtinav.app.perception.ObstacleDetector
import com.drishtinav.app.perception.ObstacleFusion
import android.media.Image
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Phase 1: real-time obstacle alerts.
 * Phase 2: + outdoor GPS navigation with turn-by-turn audio.
 *
 * Point the camera forward → on-device detection + ARCore depth →
 * spoken ("chair, 1.5 meters, ahead") + haptic alerts.
 * Press NAVIGATE → pick a destination → walking route with spoken
 * turn-by-turn guidance, layered under the obstacle alerts.
 */
class MainActivity : AppCompatActivity(),
    ArCoreManager.FrameListener,
    LocationTracker.Listener,
    NavigationEngine.Listener {

    private lateinit var glView: GLSurfaceView
    private lateinit var statusText: TextView
    private lateinit var alertText: TextView
    private lateinit var navText: TextView
    private lateinit var toggleButton: MaterialButton
    private lateinit var navigateButton: MaterialButton
    private lateinit var setupButton: MaterialButton
    private lateinit var sonarView: SonarView
    private lateinit var navCard: MaterialCardView

    private lateinit var arCore: ArCoreManager
    private lateinit var backgroundRenderer: BackgroundRenderer
    private lateinit var speech: SpeechEngine
    private lateinit var haptics: HapticEngine
    private lateinit var alertPolicy: AlertPolicy
    private lateinit var settings: AppSettings

    // Phase 2 navigation.
    private lateinit var locationTracker: LocationTracker
    private lateinit var navEngine: NavigationEngine
    private lateinit var savedPlaces: SavedPlaces

    // Heavy perception work stays off both the UI and GL threads.
    private val perceptionThread = HandlerThread("perception").apply { start() }
    private val perceptionHandler = Handler(perceptionThread.looper)
    private val frameInFlight = AtomicBoolean(false)
    private val lastDetectAt = AtomicLong(0)

    @Volatile
    private var detector: ObstacleDetector? = null

    private var running = false
    private var pendingStart = false

    /** Destination picked while GPS had no fix yet — planned on first fix. */
    private var pendingNavTarget: Triple<Double, Double, String>? = null
    private var awaitingFirstFix = false

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startAr()
            } else {
                status("Camera permission is required — grant it and press START again.")
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                pendingNavTarget?.let { (lat, lng, name) ->
                    pendingNavTarget = null
                    startNavigation(lat, lng, name)
                }
            } else {
                navStatus("Location permission is required for navigation.")
            }
        }

    // ------------------------------------------------------------------ setup

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded launch animation; hands over to Theme.DrishtiNav automatically.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glView = findViewById(R.id.gl_surface)
        statusText = findViewById(R.id.status_text)
        alertText = findViewById(R.id.alert_text)
        navText = findViewById(R.id.nav_text)
        navCard = findViewById(R.id.nav_card)
        sonarView = findViewById(R.id.sonar_view)
        toggleButton = findViewById(R.id.toggle_button)
        navigateButton = findViewById(R.id.navigate_button)
        setupButton = findViewById(R.id.setup_button)

        settings = AppSettings(this)

        backgroundRenderer = BackgroundRenderer()
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                backgroundRenderer.createOnGlThread()
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                GLES20.glViewport(0, 0, width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                if (running) {
                    arCore.doFrame(backgroundRenderer)
                } else {
                    GLES20.glClearColor(0f, 0f, 0f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                }
            }
        })
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        speech = SpeechEngine(this)
        haptics = HapticEngine(this)
        alertPolicy = AlertPolicy(speech, haptics) { text, urgent ->
            runOnUiThread {
                alertText.text = text
                // Urgent alerts flash amber + fire a shockwave on the sonar.
                alertText.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (urgent) R.color.sonar_amber else R.color.teal
                    )
                )
                if (urgent) sonarView.pulseUrgent()
            }
        }
        arCore = ArCoreManager(this).also { it.frameListener = this }

        locationTracker = LocationTracker(this).also { it.listener = this }
        navEngine = NavigationEngine(speech, this)
        savedPlaces = SavedPlaces(this)

        toggleButton.setOnClickListener {
            haptics.tick()
            if (running) stop() else start()
        }
        navigateButton.setOnClickListener {
            haptics.tick()
            if (navEngine.isNavigating) {
                stopNavigation()
            } else {
                NavigateDialog(this, savedPlaces) { lat, lng, name ->
                    startNavigation(lat, lng, name)
                }.show()
            }
        }
        setupButton.setOnClickListener {
            haptics.tick()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        applySettings()
        updateButtons()
    }

    /** Applies user settings to the live engines. Called in onCreate/onResume. */
    private fun applySettings() {
        speech.speechRate = settings.speechRate
        val wantHindi = settings.speechLanguage == AppSettings.LANG_HI
        val hindiActive = speech.setSpeechLanguage(wantHindi)
        if (wantHindi && speech.isReady && !hindiActive && !running) {
            status("Hindi voice not installed — using English. " +
                "Install Hindi in Settings → Language → Text-to-speech.")
        }
        alertPolicy.announceSideNear = settings.announceSideNear
        updateKeepScreenOn()
    }

    private fun updateKeepScreenOn() {
        if (running && settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ------------------------------------------------------------------ start/stop

    private fun start() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startAr()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startAr() {
        try {
            if (!arCore.createSession()) {
                // ARCore install/update was requested; retry when the user returns.
                pendingStart = true
                status("ARCore setup requested — approve it, then press START again.")
                return
            }
        } catch (e: Exception) {
            status("ARCore unavailable on this device: ${e.message}")
            Log.e(TAG, "createSession failed", e)
            return
        }

        if (detector == null) {
            status(getString(R.string.status_loading_model))
            perceptionHandler.post {
                try {
                    detector = ObstacleDetector(this@MainActivity)
                    runOnUiThread { status("Model loaded — scanning.") }
                } catch (e: Exception) {
                    Log.e(TAG, "Detector init failed", e)
                    runOnUiThread {
                        status("Could not load detection model: ${e.message}")
                    }
                }
            }
        }

        running = true
        updateButtons()
        updateKeepScreenOn()
        sonarView.start()
        arCore.resume()
        glView.onResume()
        status("Scanning — hold the phone at chest height, camera forward.")
        speech.speakQueued(AppStrings.scanStarted())
    }

    private fun stop() {
        running = false
        pendingStart = false
        stopNavigation(silent = true)
        updateButtons()
        updateKeepScreenOn()
        sonarView.stop()
        glView.onPause()
        arCore.pause()
        speech.stop()
        alertText.text = ""
        alertText.setTextColor(ContextCompat.getColor(this, R.color.teal))
        status(getString(R.string.status_idle))
    }

    // -------------------------------------------------------------- navigation

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startNavigation(destLat: Double, destLng: Double, name: String) {
        if (!hasLocationPermission()) {
            pendingNavTarget = Triple(destLat, destLng, name)
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        // Obstacle alerts stay on under navigation — start scanning if needed.
        if (!running) start()

        val origin = locationTracker.lastLocation
        if (origin == null) {
            navStatus(getString(R.string.nav_waiting_gps))
            pendingNavTarget = Triple(destLat, destLng, name)
            awaitingFirstFix = true
            locationTracker.start()
            return
        }
        planAndStart(origin, destLat, destLng, name)
    }

    private fun planAndStart(origin: Location, destLat: Double, destLng: Double, name: String) {
        navStatus(getString(R.string.nav_planning))
        setNavVisible(true)
        lifecycleScope.launch {
            try {
                val route = RoutePlanner.plan(
                    origin.latitude, origin.longitude, destLat, destLng
                )
                locationTracker.start()
                navEngine.start(route, destLat, destLng)
                updateButtons()
            } catch (e: Exception) {
                Log.e(TAG, "Route planning failed", e)
                navStatus("Route failed: ${e.message}")
                speech.speakQueued(AppStrings.routeFailed())
                setNavVisible(false)
            }
        }
    }

    private fun stopNavigation(silent: Boolean = false) {
        if (!navEngine.isNavigating && pendingNavTarget == null && !awaitingFirstFix) {
            setNavVisible(false)
            return
        }
        navEngine.stop()
        locationTracker.stop()
        pendingNavTarget = null
        awaitingFirstFix = false
        setNavVisible(false)
        if (!silent) {
            navStatus(AppStrings.navigationStopped())
            speech.speakQueued(AppStrings.navigationStopped())
        }
        updateButtons()
    }

    override fun onLocation(location: Location) {
        if (awaitingFirstFix) {
            val target = pendingNavTarget
            if (target != null) {
                awaitingFirstFix = false
                pendingNavTarget = null
                planAndStart(location, target.first, target.second, target.third)
                return
            }
        }
        navEngine.onLocation(location)
    }

    override fun onInstruction(text: String) {
        runOnUiThread {
            setNavVisible(true)
            navText.text = text
        }
    }

    override fun onArrival() {
        runOnUiThread {
            locationTracker.stop()
            updateButtons()
        }
    }

    override fun onOffRoute() {
        val dest = navEngine.destinationCoords ?: return
        val origin = locationTracker.lastLocation ?: return
        speech.speakQueued(AppStrings.rerouting())
        navStatus(AppStrings.rerouting())
        lifecycleScope.launch {
            try {
                val route = RoutePlanner.plan(
                    origin.latitude, origin.longitude, dest.first, dest.second
                )
                navEngine.start(route, dest.first, dest.second)
            } catch (e: Exception) {
                Log.e(TAG, "Reroute failed", e)
                navStatus("Reroute failed: ${e.message}")
            }
        }
    }

    private fun navStatus(text: String) {
        runOnUiThread {
            setNavVisible(true)
            navText.text = text
        }
    }

    private fun setNavVisible(visible: Boolean) {
        navCard.visibility = if (visible) View.VISIBLE else View.GONE
    }

    // --------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        applySettings()
        if (pendingStart) {
            pendingStart = false
            startAr()
            return
        }
        if (running) {
            try {
                if (arCore.createSession()) {
                    arCore.resume()
                    glView.onResume()
                }
            } catch (e: Exception) {
                status("ARCore unavailable: ${e.message}")
                running = false
                updateButtons()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        arCore.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        try {
            locationTracker.stop()
        } catch (_: Exception) {
        }
        try {
            detector?.close()
        } catch (_: Exception) {
        }
        detector = null
        speech.shutdown()
        arCore.close()
        perceptionThread.quitSafely()
    }

    // -------------------------------------------------------------- perception

    override fun onTrackingFrame(cameraImage: Image, depthImage: Image?) {
        // Drop the frame if the previous one is still being processed —
        // the pipeline must never fall behind the camera.
        if (!frameInFlight.compareAndSet(false, true)) {
            cameraImage.close()
            depthImage?.close()
            return
        }
        perceptionHandler.post {
            try {
                val now = SystemClock.elapsedRealtime()
                if (now - lastDetectAt.get() >= DETECT_INTERVAL_MS) {
                    lastDetectAt.set(now)
                    processFrame(cameraImage, depthImage)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Perception failed", t)
            } finally {
                try {
                    cameraImage.close()
                } catch (_: Exception) {
                }
                try {
                    depthImage?.close()
                } catch (_: Exception) {
                }
                frameInFlight.set(false)
            }
        }
    }

    private fun processFrame(cameraImage: Image, depthImage: Image?) {
        val detector = detector ?: return // model still loading; skip silently

        val portrait = FrameConverter.toPortrait(FrameConverter.yuv420ToBitmap(cameraImage))
        try {
            val rawDetections = detector.detect(portrait)
            if (rawDetections.isEmpty()) return
            val obstacles =
                ObstacleFusion.fuse(
                    rawDetections, depthImage, portrait.width, portrait.height,
                    urgentM = settings.urgentDistanceM,
                    nearM = settings.nearDistanceM
                )
            runOnUiThread {
                val nearest = obstacles.firstOrNull()?.distanceMeters
                status(
                    if (nearest != null) {
                        "Tracking — nearest obstacle %.1f m".format(nearest)
                    } else {
                        "Tracking — ${obstacles.size} objects, no depth yet"
                    }
                )
            }
            alertPolicy.evaluate(obstacles)
        } finally {
            portrait.recycle()
        }
    }

    // ------------------------------------------------------------------- ui

    override fun onStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    private fun status(text: String) {
        runOnUiThread { statusText.text = text }
    }

    private fun updateButtons() {
        toggleButton.text = getString(if (running) R.string.stop else R.string.start)
        toggleButton.contentDescription =
            getString(if (running) R.string.stop_desc else R.string.start_desc)
        toggleButton.setIconResource(if (running) R.drawable.ic_stop else R.drawable.ic_play)
        navigateButton.text =
            getString(if (navEngine.isNavigating) R.string.stop_navigation else R.string.navigate)
    }

    companion object {
        private const val TAG = "DrishtiNav"
        /** Max detection rate — detection runs at ~2.5 fps, depth every frame. */
        private const val DETECT_INTERVAL_MS = 400L
    }
}
