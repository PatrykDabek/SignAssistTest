package net.patryk.signassist

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private val requestCameraPermission = 1001
    private lateinit var sharedPreferences: SharedPreferences

    private val minHeightCamera = 200 // Minimum height for cameraContainer in pixels
    private val minHeightInfo = 200 // Minimum height for infoContainer in pixels

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val cameraContainer = findViewById<CardView>(R.id.cameraContainer)
        val infoContainer = findViewById<LinearLayout>(R.id.infoContainer)
        val divider = findViewById<View>(R.id.divider)

        // Load saved heights and camera state
        val savedHeightCamera = sharedPreferences.getInt("heightCamera", 0)
        val savedHeightInfo = sharedPreferences.getInt("heightInfo", 0)
        val isCameraEnabled = sharedPreferences.getBoolean("cameraEnabled", false)
        if (savedHeightCamera > 0 && savedHeightInfo > 0) {
            val cameraParams = cameraContainer.layoutParams as LinearLayout.LayoutParams
            cameraParams.height = savedHeightCamera
            cameraContainer.layoutParams = cameraParams

            val infoParams = infoContainer.layoutParams as LinearLayout.LayoutParams
            infoParams.height = savedHeightInfo
            infoContainer.layoutParams = infoParams
        }

        divider.setOnTouchListener(object : View.OnTouchListener {
            private var initialY = 0f
            private var initialHeightCamera = 0
            private var initialHeightInfo = 0

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = event.rawY
                        initialHeightCamera = cameraContainer.height
                        initialHeightInfo = infoContainer.height
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.rawY - initialY
                        val newHeightCamera = initialHeightCamera + deltaY.toInt()
                        val newHeightInfo = initialHeightInfo - deltaY.toInt()

                        if (newHeightCamera >= minHeightCamera && newHeightInfo >= minHeightInfo) {
                            val cameraParams = cameraContainer.layoutParams as LinearLayout.LayoutParams
                            cameraParams.height = newHeightCamera
                            cameraContainer.layoutParams = cameraParams

                            val infoParams = infoContainer.layoutParams as LinearLayout.LayoutParams
                            infoParams.height = newHeightInfo
                            infoContainer.layoutParams = infoParams
                            saveHeights(newHeightCamera, newHeightInfo)

                            startCamera()
                        } else if (newHeightCamera < minHeightCamera) {
                            stopPreview()
                        }

                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v?.performClick()
                        return true
                    }
                }
                return false
            }
        })

        cameraContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                cameraContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), requestCameraPermission)
                } else {
                    if (isCameraEnabled) {
                        startCamera()
                    } else {
                        stopPreview()
                    }
                }
            }
        })
    }

    private fun startCamera() {
        // Starts the camera preview
        val previewView = findViewById<PreviewView>(R.id.previewView)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        findViewById<TextView>(R.id.cameraDisabledText).visibility = View.GONE
        previewView.visibility = View.VISIBLE
        saveCameraState(true)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (exc: Exception) {
                // Handle exceptions
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopPreview() {
        // Stops the camera preview but keeps the camera bound
        findViewById<TextView>(R.id.cameraDisabledText).visibility = View.VISIBLE
        findViewById<PreviewView>(R.id.previewView).visibility = View.GONE
        saveCameraState(false)
    }

    private fun saveCameraState(cameraEnabled: Boolean) {
        // Saves the state of the camera to SharedPreferences
        val editor = sharedPreferences.edit()
        editor.putBoolean("cameraEnabled", cameraEnabled)
        editor.apply()
    }

    private fun saveHeights(heightCamera: Int, heightInfo: Int) {
        // Saves the heights of the cameraContainer and infoContainer to SharedPreferences
        val editor = sharedPreferences.edit()
        editor.putInt("heightCamera", heightCamera)
        editor.putInt("heightInfo", heightInfo)
        editor.apply()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCameraPermission) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startCamera()
            } else {
                // Handle permission denial
            }
        }
    }
}