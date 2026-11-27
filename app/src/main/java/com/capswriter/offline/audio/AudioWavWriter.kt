package com.capswriter.offline.audio

import android.content.Context
import android.os.Environment
import com.capswriter.offline.R
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Streams the float32 PCM chunks produced by AudioCapture into a 16 kHz mono
 * 16-bit PCM WAV file. Files are written under the app-specific Music directory,
 * which needs no storage permission on modern Android.
 */
class AudioWavWriter(private val context: Context) {

    private var file: File? = null
    private var raf: RandomAccessFile? = null
    private var dataBytes: Long = 0
    private val startedAt = Date()

    fun open(): File {
        val root = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
            DIRECTORY_NAME,
        )
        if (!root.exists() && !root.mkdirs()) {
            throw IllegalStateException(context.getString(R.string.error_recording_dir))
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(startedAt)
        val target = File(root, "rec_${stamp}.wav")
        val output = RandomAccessFile(target, "rw")
        output.setLength(0)
        writeHeader(output, 0)
        file = target
        raf = output
        dataBytes = 0
        return target
    }

    fun writeFloat32Le(bytes: ByteArray) {
        val output = raf ?: return
        val samples = bytes.size / 4
        if (samples <= 0) return
        val converted = ByteArray(samples * 2)
        var outIndex = 0
        for (index in 0 until samples) {
            val offset = index * 4
            val bits = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            val value = Float.fromBits(bits).coerceIn(-1f, 1f)
            val sample = (value * 32767f).toInt()
            converted[outIndex++] = (sample and 0xFF).toByte()
            converted[outIndex++] = ((sample shr 8) and 0xFF).toByte()
        }
        output.write(converted)
        dataBytes += converted.size
    }

    /** Finalizes the WAV and gives it a readable name derived from the result. */
    fun finish(text: String): File? {
        val output = raf ?: return null
        raf = null
        return try {
            output.seek(0)
            writeHeader(output, dataBytes)
            output.close()
            val original = file ?: return null
            val base = buildString {
                append(SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(startedAt))
                append('_')
                append(sanitize(text.take(AUDIO_NAME_LENGTH)).ifBlank { "recording" })
            }
            val target = uniqueFile(original.parentFile ?: return original, base)
            if (original.renameTo(target)) target else original
        } catch (_: Exception) {
            runCatching { output.close() }
            file
        } finally {
            file = null
            dataBytes = 0
        }
    }

    fun discard() {
        raf?.let { runCatching { it.close() } }
        raf = null
        file?.let { runCatching { it.delete() } }
        file = null
        dataBytes = 0
    }

    private fun writeHeader(output: RandomAccessFile, pcmBytes: Long) {
        output.writeBytes("RIFF")
        writeIntLe(output, (36 + pcmBytes).toInt())
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        writeIntLe(output, 16)
        writeShortLe(output, 1) // PCM
        writeShortLe(output, 1) // mono
        writeIntLe(output, SAMPLE_RATE)
        writeIntLe(output, SAMPLE_RATE * 2)
        writeShortLe(output, 2) // block align
        writeShortLe(output, 16) // bits per sample
        output.writeBytes("data")
        writeIntLe(output, pcmBytes.toInt())
    }

    private fun writeIntLe(output: RandomAccessFile, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
        output.write((value shr 16) and 0xFF)
        output.write((value shr 24) and 0xFF)
    }

    private fun writeShortLe(output: RandomAccessFile, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    private fun uniqueFile(directory: File, base: String): File {
        var target = File(directory, "$base.wav")
        var suffix = 2
        while (target.exists()) {
            target = File(directory, "${base}_$suffix.wav")
            suffix++
        }
        return target
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')

    companion object {
        const val DIRECTORY_NAME = "CapsWriter"
        const val AUDIO_NAME_LENGTH = 20 // matches config_client.py audio_name_len
        private const val SAMPLE_RATE = 16_000

        fun directory(context: Context): File = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
            DIRECTORY_NAME,
        )
    }
}
