package com.yourcompany.navigoapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.sceneform.ux.ArFragment
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var arFragment: ArFragment
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var navigateButton: Button
    private lateinit var waypointButton: Button

    private var arSession: Session? = null
    private var userRequestedInstall = true
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val pathWaypoints = mutableListOf<Anchor>()
    private var lastRecordedPose: Pose? = null
    private var currentWaypointIndex = 0

    private var isRecording = false
    private var isNavigating = false

    // Settings
    private val WAYPOINT_AUTO_DISTANCE = 1.4f // automatic waypoint every 1.4 meters
    private val WAYPOINT_PROXIMITY_THRESHOLD = 0.75f
    private val DIRECTION_ANGLE_SLIGHT = 25.0
    private val DIRECTION_ANGLE_MODERATE = 45.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment
        statusText = findViewById(R.id.status_text)
        recordButton = findViewById(R.id.record_button)
        waypointButton = findViewById(R.id.waypoint_button)
        navigateButton = findViewById(R.id.navigate_button)

        // Hide manual waypoint button
        waypointButton.visibility = View.GONE

        tts = TextToSpeech(this, this)

        recordButton.setOnClickListener { toggleRecording() }
        navigateButton.setOnClickListener { toggleNavigation() }

        checkPermissionsAndInit()
    }

    // ------------------------------------------------------------
    // RECORDING LOGIC
    // ------------------------------------------------------------
    private fun toggleRecording() {
        if (!isRecording) {
            if (arFragment.arSceneView.session == null) {
                speak("AR session not ready yet. Please wait.")
                return
            }

            isRecording = true
            isNavigating = false
            pathWaypoints.clear()
            currentWaypointIndex = 0
            lastRecordedPose = null

            recordButton.text = "Stop Recording"
            navigateButton.isEnabled = false
            statusText.text = "Recording path... Waypoints every $WAYPOINT_AUTO_DISTANCE meters."
            speak("Recording started. I will add waypoints automatically every one point four meters.")

            addWaypoint(force = true)
        } else {
            isRecording = false
            recordButton.text = "Record Path"
            navigateButton.isEnabled = pathWaypoints.size > 1
            speak("Recording stopped. Path saved with ${pathWaypoints.size} waypoints.")
        }
    }

    // ------------------------------------------------------------
    // NAVIGATION LOGIC
    // ------------------------------------------------------------
    private fun toggleNavigation() {
        if (!isNavigating) {
            if (pathWaypoints.size < 2) {
                speak("No path recorded. Please record a path first.")
                return
            }

            isNavigating = true
            isRecording = false
            currentWaypointIndex = 0

            navigateButton.text = "Stop Navigation"
            recordButton.isEnabled = false
            speak("Navigation started. Please follow my voice guidance.")

            // Start by giving direction to the first waypoint
            announceNextWaypointInstruction()
        } else {
            isNavigating = false
            navigateButton.text = "Navigate Path"
            recordButton.isEnabled = true
            speak("Navigation stopped.")
        }
    }

    // ------------------------------------------------------------
    // ARCORE SESSION MANAGEMENT
    // ------------------------------------------------------------
    private fun checkPermissionsAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else {
            initializeArSession()
        }
    }

    private fun initializeArSession() {
        try {
            if (arSession == null) {
                when (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        userRequestedInstall = false
                        return
                    }
                    else -> {}
                }

                arSession = Session(this)
                val config = Config(arSession)
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                arSession!!.configure(config)
                arFragment.arSceneView.setupSession(arSession)
                arFragment.arSceneView.scene.addOnUpdateListener { onUpdateFrame() }

                speak("AR session ready. You can start recording.")
            }
        } catch (e: Exception) {
            Log.e("NaviGo", "AR session init failed", e)
            speak("AR session could not be initialized.")
        }
    }

    // ------------------------------------------------------------
    // FRAME UPDATES
    // ------------------------------------------------------------
    private fun onUpdateFrame() {
        val frame = arFragment.arSceneView.arFrame ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        if (isRecording) addWaypoint()
        if (isNavigating) checkDistanceAndProgress()
    }

    // ------------------------------------------------------------
    // WAYPOINT MANAGEMENT
    // ------------------------------------------------------------
    private fun addWaypoint(force: Boolean = false) {
        val frame = arFragment.arSceneView.arFrame ?: return
        val cameraPose = frame.camera.pose ?: return

        if (force || lastRecordedPose == null ||
            calculateDistance(cameraPose, lastRecordedPose!!) >= WAYPOINT_AUTO_DISTANCE
        ) {
            val anchor = arFragment.arSceneView.session?.createAnchor(cameraPose)
            if (anchor != null) {
                pathWaypoints.add(anchor)
                lastRecordedPose = cameraPose
                speak("Waypoint ${pathWaypoints.size} added.")
            }
        }
    }

    // ------------------------------------------------------------
    // NAVIGATION LOGIC
    // ------------------------------------------------------------
    private fun checkDistanceAndProgress() {
        if (currentWaypointIndex >= pathWaypoints.size) {
            speak("You have reached your destination.")
            isNavigating = false
            navigateButton.text = "Navigate Path"
            recordButton.isEnabled = true
            return
        }

        val frame = arFragment.arSceneView.arFrame ?: return
        val cameraPose = frame.camera.pose
        val targetPose = pathWaypoints[currentWaypointIndex].pose
        val distance = calculateDistance(cameraPose, targetPose)

        if (distance < WAYPOINT_PROXIMITY_THRESHOLD) {
            currentWaypointIndex++
            if (currentWaypointIndex < pathWaypoints.size) {
                announceNextWaypointInstruction()
            } else {
                speak("You have arrived at your destination.")
                isNavigating = false
                navigateButton.text = "Navigate Path"
                recordButton.isEnabled = true
            }
        }
    }

    private fun announceNextWaypointInstruction() {
        val frame = arFragment.arSceneView.arFrame ?: return
        val cameraPose = frame.camera.pose ?: return

        if (currentWaypointIndex < pathWaypoints.size) {
            val nextPose = pathWaypoints[currentWaypointIndex].pose
            val distance = calculateDistance(cameraPose, nextPose)
            val instruction = getDirectionInstruction(cameraPose, nextPose, distance)
            speak(instruction)
        }
    }

    private fun calculateDistance(pose1: Pose, pose2: Pose): Float {
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // ------------------------------------------------------------
    // DIRECTION CALCULATION
    // ------------------------------------------------------------
    private fun getDirectionInstruction(currentPose: Pose, targetPose: Pose, distance: Float): String {
        val vx = targetPose.tx() - currentPose.tx()
        val vz = targetPose.tz() - currentPose.tz()

        val zAxis = currentPose.zAxis
        val fx = -zAxis[0]
        val fz = -zAxis[2]

        val lenF = sqrt(fx * fx + fz * fz)
        val lenV = sqrt(vx * vx + vz * vz)
        if (lenF == 0f || lenV == 0f) return "Go straight"

        val fxn = (fx / lenF).toDouble()
        val fzn = (fz / lenF).toDouble()
        val vxn = (vx / lenV).toDouble()
        val vzn = (vz / lenV).toDouble()

        val cross = fxn * vzn - fzn * vxn
        val dot = fxn * vxn + fzn * vzn
        val angleDeg = Math.toDegrees(atan2(cross, dot))
        val absAngle = Math.abs(angleDeg)

        val direction = when {
            absAngle < 10 -> "Go straight"
            absAngle < 25 && angleDeg > 0 -> "Slight right"
            absAngle < 25 && angleDeg < 0 -> "Slight left"
            absAngle < 45 && angleDeg > 0 -> "Turn right"
            absAngle < 45 && angleDeg < 0 -> "Turn left"
            angleDeg >= 45 -> "Sharp right"
            else -> "Sharp left"
        }

        return "$direction ${"%.1f".format(distance)} meters"
    }

    // ------------------------------------------------------------
    // TTS
    // ------------------------------------------------------------
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                ttsReady = false
                Log.e("TTS", "Language not supported.")
            } else {
                ttsReady = true
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.0f)
                speak("Welcome to Navi Go.")
            }
        } else {
            ttsReady = false
            Log.e("TTS", "Initialization failed.")
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NAVIGO_TTS")
        runOnUiThread { statusText.text = text }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeArSession()
            } else {
                speak("Camera permission is required for AR functionality.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        arSession?.close()
        arSession = null
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 101
    }
}
