package com.capswriter.offline.audio

import android.annotation.SuppressLint
import android.content.Context
import com.capswriter.offline.R
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Captures 16 kHz mono audio and emits 100 ms chunks of little-endian float32 PCM,
 * which is exactly what CapsWriter-Offline expects.
 */
class AudioCapture(
    private val context: Context,
    private val onChunk: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
) {

    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var encoding = AudioFormat.ENCODING_PCM_FLOAT

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true

        val attempts = buildList {
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to AudioFormat.ENCODING_PCM_FLOAT)
            add(MediaRecorder.AudioSource.MIC to AudioFormat.ENCODING_PCM_FLOAT)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to AudioFormat.ENCODING_PCM_16BIT)
            add(MediaRecorder.AudioSource.MIC to AudioFormat.ENCODING_PCM_16BIT)
        }

        var lastError = context.getString(R.string.audio_unsupported)
        for ((source, candidateEncoding) in attempts) {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                candidateEncoding,
            )
            if (minBuffer <= 0) continue

            val audioRecord = try {
                val format = AudioFormat.Builder()
                    .setEncoding(candidateEncoding)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(max(minBuffer * 2, 16_000))
                    .build()
            } catch (error: Exception) {
                lastError = error.message ?: error.javaClass.simpleName
                continue
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                lastError = context.getString(R.string.audio_init_failed, source)
                audioRecord.release()
                continue
            }

            try {
                audioRecord.startRecording()
            } catch (error: Exception) {
                lastError = context.getString(
                    R.string.audio_start_failed,
                    error.message ?: error.javaClass.simpleName,
                )
                audioRecord.release()
                continue
            }

            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                lastError = context.getString(R.string.audio_not_recording)
                runCatching { audioRecord.stop() }
                audioRecord.release()
                continue
            }

            record = audioRecord
            encoding = candidateEncoding
            running = true
            thread = Thread({ captureLoop(audioRecord, candidateEncoding) }, "caps-writer-capture")
                .also { it.start() }
            return true
        }

        onError(lastError)
        return false
    }

    fun stop() {
        if (!running && record == null) return
        running = false
        val current = record
        record = null
        runCatching { current?.stop() }
        thread?.let { worker ->
            if (worker !== Thread.currentThread()) {
                runCatching { worker.join(700) }
            }
        }
        thread = null
        runCatching { current?.release() }
    }

    private fun captureLoop(audioRecord: AudioRecord, encoding: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        try {
            if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                val buffer = FloatArray(CHUNK_SAMPLES)
                while (running) {
                    val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count < 0) {
                        if (running) onError(context.getString(R.string.audio_read_failed, count))
                        break
                    }
                    if (count > 0) onChunk(floatToLittleEndian(buffer, count))
                }
            } else {
                val buffer = ShortArray(CHUNK_SAMPLES)
                while (running) {
                    val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count < 0) {
                        if (running) onError(context.getString(R.string.audio_read_failed, count))
                        break
                    }
                    if (count > 0) onChunk(shortToLittleEndian(buffer, count))
                }
            }
        } catch (error: Exception) {
            if (running) onError(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun floatToLittleEndian(samples: FloatArray, count: Int): ByteArray {
        val buffer = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until count) buffer.putFloat(samples[index])
        return buffer.array()
    }

    private fun shortToLittleEndian(samples: ShortArray, count: Int): ByteArray {
        val buffer = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until count) {
            buffer.putFloat(samples[index] / 32768f)
        }
        return buffer.array()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 1_600 // 100 ms
    }
}
