package com.mysimplemeditation.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.net.toUri
import com.mysimplemeditation.app.R
import java.io.File
import android.os.*

object AudioHelper {

    fun playBell(
        context: Context,
        volume: Int, // 0-100
        useAlarmStream: Boolean,
        executions: Int = 1,
        gapMs: Int = 500,
        onComplete: (() -> Unit)? = null
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
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    } else {
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    }

                    mp.setDataSource(context.applicationContext,
                        "android.resource://${context.packageName}/${R.raw.singing_bell}".toUri())
                    mp.setVolume(volume / 100f, volume / 100f)
                    mp.prepare()
                    mp.setOnCompletionListener { it.release() }
                    mp.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                    try { mp.release() } catch (_: Exception) {}
                }

                remaining--
                if (remaining > 0) {
                    Thread.sleep(gapMs.toLong())
                }
            }
            onComplete?.invoke()
        }.start()
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
                        val uri = path.toUri()
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

    fun playVibration(
        context: Context,
        durationMs: Int = 500,
        executions: Int = 1,
        gapMs: Int = 1000,
        onAllComplete: (() -> Unit)? = null
    ) {
        val vibrator =
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

        if (!vibrator.hasVibrator()) return

        Log.d("AudioHelper", "playVibration: duration=$durationMs, exec=$executions, gap=$gapMs")

        // 1. Define the Pattern
        // Waveform timings: [delayBeforeStart, duration1, gap1, duration2, gap2...]
        val timings = mutableListOf<Long>(0) // Start immediately
        val amplitudes = mutableListOf<Int>(0) // Initial amplitude (off)

        for (i in 1..executions) {
            timings.add(durationMs.toLong())
            amplitudes.add(255) // Max strength

            if (i < executions) {
                timings.add(gapMs.toLong())
                amplitudes.add(0) // Pause
            }
        }


        val effect = VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attributes)
        } else {
            vibrator.vibrate(effect)
        }

        // Optional: Use a simple postDelayed for your callback
        Handler(Looper.getMainLooper()).postDelayed({
            onAllComplete?.invoke()
        }, timings.sum())
    }


    fun setStreamVolume(context: Context, streamType: Int, volume: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(streamType)
        val scaled = (volume / 100f * max).toInt()
        audioManager.setStreamVolume(streamType, scaled, 0)
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
