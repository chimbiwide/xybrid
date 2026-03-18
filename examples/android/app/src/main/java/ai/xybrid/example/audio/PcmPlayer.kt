package ai.xybrid.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Simple PCM audio player using AudioTrack.
 * Plays raw 16-bit PCM mono audio at a given sample rate.
 */
class PcmPlayer(private val sampleRate: Int = 24000) {

    private var audioTrack: AudioTrack? = null

    val isPlaying: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    /**
     * Play raw 16-bit PCM mono audio bytes.
     * Stops any currently playing audio first.
     */
    fun play(pcmBytes: ByteArray, onComplete: (() -> Unit)? = null) {
        stop()

        val bufferSize = pcmBytes.size.coerceAtLeast(
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcmBytes, 0, pcmBytes.size)

        track.setNotificationMarkerPosition(pcmBytes.size / 2) // 2 bytes per sample
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                onComplete?.invoke()
            }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })

        track.play()
        audioTrack = track
    }

    fun stop() {
        audioTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        }
        audioTrack = null
    }

    /** Estimated duration in seconds for the given PCM byte array. */
    fun estimateDurationSec(pcmBytes: ByteArray): Float {
        // 16-bit mono = 2 bytes per sample
        val numSamples = pcmBytes.size / 2
        return numSamples.toFloat() / sampleRate
    }

    fun release() {
        stop()
    }
}
