package com.drishtinav.app.ar

import android.app.Activity
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import android.media.Image
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException

/**
 * Owns the ARCore [Session] and drives the per-frame loop.
 *
 * All [doFrame] calls happen on the GLSurfaceView render thread. Camera and
 * depth [Image]s are acquired there and handed to [FrameListener.onTrackingFrame],
 * which takes ownership and MUST close them (even on the error path).
 */
class ArCoreManager(private val activity: Activity) {

    interface FrameListener {
        fun onTrackingFrame(cameraImage: Image, depthImage: Image?)
        fun onStatus(text: String)
    }

    var frameListener: FrameListener? = null

    private var session: Session? = null
    private var textureName = -1
    private var depthSupported = false
    private var errorReported = false

    val isDepthSupported: Boolean get() = depthSupported

    /**
     * Creates and configures the ARCore session with the Depth API enabled
     * when the device supports it.
     *
     * @return true when the session is ready; false when an ARCore install /
     * update was requested — call again from onResume after the user returns.
     * @throws UnavailableException when ARCore cannot run on this device.
     */
    @Throws(UnavailableException::class)
    fun createSession(): Boolean {
        if (session != null) return true

        when (ArCoreApk.getInstance().requestInstall(activity, true)) {
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> return false
            ArCoreApk.InstallStatus.INSTALLED -> Unit
        }

        val newSession = Session(activity)
        val config = Config(newSession)
        depthSupported = newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        config.depthMode =
            if (depthSupported) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
        config.focusMode = Config.FocusMode.AUTO
        // We only need the latest frame; never let the pipeline fall behind.
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        newSession.configure(config)
        session = newSession

        frameListener?.onStatus(
            if (depthSupported) "ARCore ready — depth sensing on"
            else "ARCore ready — depth unavailable, distance estimates disabled"
        )
        return true
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            reportErrorOnce("Camera unavailable: ${e.message}")
        }
    }

    fun pause() {
        session?.pause()
    }

    fun close() {
        session?.close()
        session = null
        textureName = -1
    }

    /** Must be called on the GL thread, once per rendered frame. */
    fun doFrame(renderer: BackgroundRenderer) {
        val session = session ?: return
        try {
            if (textureName == -1) {
                textureName = renderer.cameraTextureId
                session.setCameraTextureName(textureName)
            }
            val frame: Frame = session.update()
            renderer.draw(frame)

            val camera: Camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                frameListener?.onStatus("Searching for visual features — move the phone slowly")
                return
            }

            var cameraImage: Image? = null
            var depthImage: Image? = null
            try {
                cameraImage = frame.acquireCameraImage()
                if (depthSupported) {
                    depthImage = try {
                        frame.acquireDepthImage16Bits()
                    } catch (_: NotYetAvailableException) {
                        null // depth not ready on this frame; try the next one
                    }
                }
                // Transfer ownership to the listener; null the locals so the
                // finally block below does not double-close.
                val ownedCamera = cameraImage
                val ownedDepth = depthImage
                cameraImage = null
                depthImage = null
                frameListener?.onTrackingFrame(ownedCamera, ownedDepth)
            } finally {
                cameraImage?.close()
                depthImage?.close()
            }
        } catch (_: NotYetAvailableException) {
            // Transient — a frame simply wasn't ready. Skip silently.
        } catch (e: CameraNotAvailableException) {
            reportErrorOnce("Camera unavailable: ${e.message}")
        } catch (e: Exception) {
            reportErrorOnce("ARCore error: ${e.message}")
        }
    }

    private fun reportErrorOnce(message: String) {
        if (!errorReported) {
            errorReported = true
            Log.e(TAG, message)
            frameListener?.onStatus("Error: $message")
        }
    }

    companion object {
        private const val TAG = "DrishtiNav-ArCore"
    }
}
