package com.mymeditation.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object AudioHelper {

    private const val BELL_FREQUENCY = 432.0 // Hz - "Verdi Tuning"
    private const val BELL_DURATION_MS = 5000L // 5 seconds
    private const val SAMPLE_RATE = 44100

    fun playBell(
        volume: Int, // 0-100
        useAlarmStream: Boolean,
        executions: Int = 1,
        gapMs: Int = 500,
        onComplete: (() -> Unit)? = null
    ) {
        Thread {
            var remaining = executions
            while (remaining > 0) {
                try {
                    play432HzBell(volume, useAlarmStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                remaining--
                if (remaining > 0) {
                    Thread.sleep(gapMs.toLong())
                }
            }
            onComplete?.invoke()
        }.start()
    }

    private fun play432HzBell(volume: Int, useAlarmStream: Boolean) {
        val numSamples = (SAMPLE_RATE * BELL_DURATION_MS / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val amplitude = (volume / 100.0) * Short.MAX_VALUE

        // Generate 432Hz sine wave with exponential fade-out
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Exponential decay: gain goes from 1.0 to ~0.001 over BELL_DURATION_MS
            val progress = i.toDouble() / numSamples
            val gain = exp(-6.9 * progress) // e^(-6.9) ≈ 0.001
            val sample = amplitude * gain * sin(2.0 * PI * BELL_FREQUENCY * t)
            samples[i] = (sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())).toShort()
        }

        val bufferSize = numSamples * 2 // 16-bit = 2 bytes per sample

        val audioAttributes = if (useAlarmStream) {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } else {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, numSamples)
        track.play()

        // Wait for playback to finish
        Thread.sleep(BELL_DURATION_MS + 100)
        track.stop()
        track.release()
    }

    fun playMp3(
        context: Context,
        path: String,
        volume: Int, // 0-100
        useAlarmStream: Boolean,
        executions: Int = 1,
        gapMs: Int = 500,
        onAllComplete: (() -> Unit)? = null
    ) {
        Thread {
            var remaining = executions
            while (remaining > 0) {
                val mp = MediaPlayer()
                try {
                    if (useAlarmStream) {
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                    } else {
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                    }

                    val file = File(path)
                    if (file.exists()) {
                        mp.setDataSource(path)
                    } else if (path.startsWith("content://")) {
                        // Content URI from SAF file picker
                        val uri = android.net.Uri.parse(path)
                        mp.setDataSource(context, uri)
                    } else {
                        // Try as asset or resource URI
                        mp.setDataSource(path)
                    }

                    mp.setVolume(volume / 100f, volume / 100f)
                    mp.prepare()
                    mp.start()

                    // Wait for playback to finish
                    while (mp.isPlaying) {
                        Thread.sleep(100)
                    }
                    mp.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                    try { mp.release() } catch (_: Exception) {}
                }

                remaining--
                if (remaining > 0) {
                    Thread.sleep(gapMs.toLong())
                }
            }
            onAllComplete?.invoke()
        }.start()
    }

    fun setStreamVolume(context: Context, streamType: Int, volume: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(streamType)
        val scaled = (volume / 100f * max).toInt()
        audioManager.setStreamVolume(streamType, scaled, 0)
    }

    fun getStreamVolume(context: Context, streamType: Int): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        return if (max > 0) (current.toFloat() / max * 100).toInt() else 80
    }

    fun saveCurrentVolume(context: Context, settings: SettingsManager) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        settings.savedMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        settings.savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
    }

    fun restoreVolume(context: Context, settings: SettingsManager) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (settings.savedMediaVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, settings.savedMediaVolume, 0)
        }
        if (settings.savedAlarmVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, settings.savedAlarmVolume, 0)
        }
    }
}
