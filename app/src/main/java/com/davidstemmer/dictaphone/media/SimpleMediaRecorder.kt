package com.davidstemmer.dictaphone.media

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.CheckResult
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class SimpleMediaRecorder(context: Context, private val mediaRepository: SimpleMediaRepository) {

    private val contentResolver by lazy { context.contentResolver }
    private val recorder: MediaRecorder by lazy {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> MediaRecorder(context)
            else -> @Suppress("DEPRECATION") MediaRecorder()
        }
    }

    sealed interface StartResult {
        data class Success(
            val uri: Uri,
            val fileHandle: Closeable): StartResult
        data class Failure(val e: Throwable? = null): StartResult
    }

    sealed interface StopResult {
        data object Success: StopResult
        data class Failure(val e: Throwable? = null): StopResult
    }

    @CheckResult
    fun startRecording(): StartResult {

        recorder.setOnErrorListener { _, code, extra ->
            Log.e("MediaRecorder", "MediaRecorder.OnErrorListener: $code, $extra")
        }
        recorder.setOnInfoListener { _, code, extra ->
            Log.i("MediaRecorder", "MediaRecorder.OnErrorListener: $code, $extra")
        }

        val maxDuration = TimeUnit.MINUTES.toMillis(1).toInt()
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        recorder.setAudioChannels(1)
        recorder.setMaxDuration(maxDuration)

        @OptIn(ExperimentalStdlibApi::class)
        val randomSuffix = Random.nextInt().toHexString()

        val fileName = "recording-$randomSuffix"
        val uri = mediaRepository.initializeAudioRecording(fileName) ?: return StartResult.Failure()

        return try {
            contentResolver.openFileDescriptor(uri, "w")?.let { parcelFileDescriptor ->
                recorder.setOutputFile(parcelFileDescriptor.fileDescriptor)
                recorder.prepare()
                recorder.start()
                StartResult.Success(uri, parcelFileDescriptor)
            } ?: StartResult.Failure()
        } catch (e: Throwable) {
            StartResult.Failure(e)
        }
    }

    /**
     * The MediaRecorder will throw "RuntimeException: stop failed" if stop() is called immediately
     * after start(). See link for details.
     *
     * https://stackoverflow.com/questions/16221866/mediarecorder-failed-when-i-stop-the-recording
     */
    @CheckResult
    fun stopRecording(): StopResult {
        return try {
            recorder.stop()
            StopResult.Success
        } catch (e: Throwable) {
            StopResult.Failure(e)
        } finally {
            recorder.reset()
        }
    }
}