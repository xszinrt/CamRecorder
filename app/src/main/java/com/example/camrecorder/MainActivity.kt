package com.example.camrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.camrecorder.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: CameraViewModel
    
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    
    private lateinit var cameraExecutor: ExecutorService
    
    // طلب الأذونات
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.CAMERA, false) &&
            permissions.getOrDefault(Manifest.permission.RECORD_AUDIO, false) -> {
                startCamera()
            }
            else -> {
                Toast.makeText(this, "Camera and Audio permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = CameraViewModel(application)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        checkPermissions()
        setupUI()
        observeViewModel()
    }
    
    private fun checkPermissions() {
        val permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            startCamera()
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ))
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun setupUI() {
        binding.btnRecord.setOnClickListener {
            if (viewModel.isRecording.value) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        
        binding.btnSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }
        
        binding.btnGallery.setOnClickListener {
            startActivity(Intent(this, VideoListActivity::class.java))
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.isRecording.collect { isRecording ->
                binding.btnRecord.text = if (isRecording) "⏹ Stop" else "🔴 Record"
                binding.btnSwitchCamera.isEnabled = !isRecording
            }
        }
        
        lifecycleScope.launch {
            viewModel.recordTime.collect { time ->
                binding.tvTimer.text = time
            }
        }
    }
    
    private fun startRecording() {
        val videoCapture = videoCapture ?: return
        
        val videoFile = File(
            viewModel.getVideoDirectory(this),
            viewModel.generateVideoFileName()
        )
        
        val outputOptions = FileOutputOptions.Builder(videoFile).build()
        
        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewModel.startRecording()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (recordEvent.hasError()) {
                            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
                        } else {
                            val savedUri = viewModel.saveVideo(this, videoFile)
                            if (savedUri != null) {
                                Toast.makeText(this, "Video saved to Gallery", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to save video", Toast.LENGTH_SHORT).show()
                            }
                        }
                        viewModel.stopRecording()
                        recording = null
                    }
                }
            }
    }
    
    private fun stopRecording() {
        recording?.stop()
        recording = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        recording?.stop()
    }
}
