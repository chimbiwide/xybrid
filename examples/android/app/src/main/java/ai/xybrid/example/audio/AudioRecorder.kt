package ai.xybrid.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Simple PCM audio recorder for ASR input.
 * Records 16-bit mono PCM at 16 kHz (what Whisper / Wav2Vec2 expect).
 */
class AudioRecorder(private val sampleRate: Int = 16000) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    private var outputStream: ByteArrayOutputStream? = null

    val recording: Boolean get() = isRecording

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Start recording. Returns false if permission is missing or AudioRecord fails.
     */
    fun start(context: Context): Boolean {
        if (!hasPermission(context)) return false
        if (isRecording) return true

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return false
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
        } catch (_: SecurityException) {
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        val baos = ByteArrayOutputStream()
        outputStream = baos
        audioRecord = record
        isRecording = true

        record.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    baos.write(buffer, 0, read)
                }
            }
        }.apply { start() }

        return true
    }

    /**
     * Stop recording and return WAV-formatted audio bytes.
     */
    fun stop(): ByteArray {
        isRecording = false
        recordingThread?.join(1000)
        recordingThread = null

        audioRecord?.let {
            if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                it.stop()
            }
            it.release()
        }
        audioRecord = null

        val pcm = outputStream?.toByteArray() ?: ByteArray(0)
        outputStream = null
        return wrapWav(pcm)
    }

    /** Wraps raw 16-bit mono PCM into a WAV container. */
    private fun wrapWav(pcm: ByteArray): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val fileSize = 36 + dataSize // total - 8 bytes for RIFF header

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)
        buffer.put("WAVE".toByteArray())
        // fmt sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)              // sub-chunk size
        buffer.putShort(1)             // PCM format
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        // data sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcm)

        return buffer.array()
    }

    fun release() {
        if (isRecording) stop()
    }
}
