package com.example.camrecorder

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _recordTime = MutableStateFlow("00:00")
    val recordTime: StateFlow<String> = _recordTime.asStateFlow()
    
    private var recordingStartTime = 0L
    private var recordingJob: kotlinx.coroutines.Job? = null
    
    fun startRecording() {
        if (_isRecording.value) return
        _isRecording.value = true
        recordingStartTime = System.currentTimeMillis()
        
        recordingJob = viewModelScope.launch {
            while (_isRecording.value) {
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                _recordTime.value = String.format("%02d:%02d", minutes, seconds)
                delay(1000)
            }
        }
    }
    
    fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        _recordTime.value = "00:00"
    }
    
    fun saveVideo(context: Context, videoFile: File): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CamRecorder")
                } else {
                    put(MediaStore.Video.Media.DATA, videoFile.absolutePath)
                }
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    videoFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            // حذف الملف المؤقت
            videoFile.delete()
            
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun generateVideoFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "VIDEO_$timestamp.mp4"
    }
    
    fun getVideoDirectory(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // بالنسبة للإصدارات الجديدة، نستخدم مجلد مؤقت ثم ننقل إلى MediaStore
            context.cacheDir
        } else {
            File(Environment.getExternalStorageDirectory(), "Movies/CamRecorder").apply {
                if (!exists()) mkdirs()
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopRecording()
    }
}
