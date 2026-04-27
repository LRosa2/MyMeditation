package com.mymeditation.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.mymeditation.app.R
import java.io.File

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
                        android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.singing_bell}"))
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
