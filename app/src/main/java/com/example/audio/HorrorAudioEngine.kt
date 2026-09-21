package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.random.Random

/**
 * High-performance offline procedural horror audio engine.
 * Synthesizes dynamic sound effects and eerie ambient drone without any external files or network requirements.
 */
class HorrorAudioEngine(private val context: Context) {

    private val sampleRate = 22050
    private var isMuted = false
    private var sfxEnabled = true
    private var hapticsEnabled = true

    private var ambienceJob: Job? = null
    private val audioScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun setSoundEnabled(enabled: Boolean) {
        sfxEnabled = enabled
    }

    fun setAmbienceEnabled(enabled: Boolean) {
        isMuted = !enabled
        if (isMuted) {
            stopAmbience()
        } else {
            startAmbience()
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        hapticsEnabled = enabled
    }

    fun vibrate(durationMs: Long, amplitude: Int = 180) {
        if (!hapticsEnabled || vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    /**
     * Start procedural ambient horror drone loop.
     */
    fun startAmbience() {
        if (ambienceJob?.isActive == true || isMuted) return

        ambienceJob = audioScope.launch {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBufSize, 4096)
            var track: AudioTrack? = null

            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                val pcmBuffer = ShortArray(bufferSize)
                var phase1 = 0.0
                var phase2 = 0.0
                var lfoPhase = 0.0

                while (isActive && !isMuted) {
                    for (i in 0 until bufferSize) {
                        // Sub-bass 55Hz + eerie dissonant tritone 77.8Hz
                        lfoPhase += 0.0003
                        val lfo = 0.7 + 0.3 * sin(lfoPhase)
                        val f1 = 55.0 + 2.0 * sin(lfoPhase * 0.5)
                        val f2 = 78.0

                        phase1 += (2.0 * Math.PI * f1) / sampleRate
                        phase2 += (2.0 * Math.PI * f2) / sampleRate

                        val s1 = sin(phase1) * 0.35
                        val s2 = sin(phase2) * 0.18
                        val noise = (Random.nextDouble() - 0.5) * 0.04
                        val combined = ((s1 + s2 + noise) * lfo).coerceIn(-1.0, 1.0)

                        pcmBuffer[i] = (combined * 12000).toInt().toShort()
                    }
                    track.write(pcmBuffer, 0, bufferSize)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stopAmbience() {
        ambienceJob?.cancel()
        ambienceJob = null
    }

    private fun playPcm(pcm: ShortArray) {
        if (!sfxEnabled) return
        audioScope.launch {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                delay((pcm.size.toDouble() / sampleRate * 1000).toLong() + 50)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }

    // Footstep
    fun playFootstep(isCrouching: Boolean = false, isRunning: Boolean = false) {
        val numSamples = (sampleRate * 0.12).toInt()
        val pcm = ShortArray(numSamples)
        val baseFreq = if (isRunning) 110.0 else 85.0
        val vol = if (isCrouching) 0.3 else if (isRunning) 1.0 else 0.6
        var phase = 0.0

        for (i in 0 until numSamples) {
            val env = 1.0 - (i.toDouble() / numSamples)
            val freq = baseFreq * (1.0 - (i.toDouble() / numSamples) * 0.5)
            phase += (2.0 * Math.PI * freq) / sampleRate
            val s = (sin(phase) + (Random.nextDouble() - 0.5) * 0.4) * env * vol
            pcm[i] = (s * 14000).toInt().toShort()
        }
        playPcm(pcm)
    }

    // Door creak
    fun playDoorCreak() {
        val numSamples = (sampleRate * 0.6).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 220.0 + sin(progress * 18.0) * 80.0 + progress * 140.0
            phase += (2.0 * Math.PI * freq) / sampleRate
            val saw = ((phase % (2.0 * Math.PI)) / Math.PI - 1.0)
            val env = sin(progress * Math.PI)
            pcm[i] = (saw * env * 11000).toInt().toShort()
        }
        playPcm(pcm)
    }

    // Drawer slide
    fun playDrawerOpen() {
        val numSamples = (sampleRate * 0.35).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 140.0 + progress * 50.0
            phase += (2.0 * Math.PI * freq) / sampleRate
            val s = (sin(phase) * 0.4 + (Random.nextDouble() - 0.5) * 0.6) * (1.0 - progress)
            pcm[i] = (s * 10000).toInt().toShort()
        }
        playPcm(pcm)
    }

    // Menu Button Click stinger
    fun playMenuClick() {
        val numSamples = (sampleRate * 0.14).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = 180.0 * (1.0 - progress * 0.5)
            phase += (2.0 * Math.PI * freq) / sampleRate
            val s = (sin(phase) * 0.7 + (Random.nextDouble() - 0.5) * 0.3) * (1.0 - progress)
            pcm[i] = (s * 14000).toInt().toShort()
        }
        playPcm(pcm)
        vibrate(35, 110)
    }

    // Menu Start Game Horror Stinger
    fun playMenuStartGame() {
        val numSamples = (sampleRate * 0.85).toInt()
        val pcm = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq1 = 110.0 + sin(progress * 12.0) * 30.0 - progress * 40.0
            val freq2 = 165.0 - progress * 50.0
            phase1 += (2.0 * Math.PI * freq1) / sampleRate
            phase2 += (2.0 * Math.PI * freq2) / sampleRate
            val s = (sin(phase1) * 0.6 + sin(phase2) * 0.4 + (Random.nextDouble() - 0.5) * 0.3) * (1.0 - progress * 0.7)
            pcm[i] = (s * 18000).toInt().toShort()
        }
        playPcm(pcm)
        vibrate(180, 200)
    }

    // Item Pickup Chime
    fun playItemPickup() {
        val numSamples = (sampleRate * 0.25).toInt()
        val pcm = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until numSamples) {
            val env = (1.0 - i.toDouble() / numSamples)
            phase1 += (2.0 * Math.PI * 587.33) / sampleRate // D5
            phase2 += (2.0 * Math.PI * 880.0) / sampleRate  // A5
            val s = (sin(phase1) * 0.6 + sin(phase2) * 0.4) * env
            pcm[i] = (s * 16000).toInt().toShort()
        }
        playPcm(pcm)
    }

    // Item Drop / Loud Clang (triggers Granny hearing!)
    fun playItemDrop() {
        val numSamples = (sampleRate * 0.45).toInt()
        val pcm = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until numSamples) {
            val env = exp(-i.toDouble() / (sampleRate * 0.08))
            phase1 += (2.0 * Math.PI * 180.0) / sampleRate
            phase2 += (2.0 * Math.PI * 340.0) / sampleRate
            val s = (sin(phase1) * 0.5 + sin(phase2) * 0.3 + (Random.nextDouble() - 0.5) * 0.5) * env
            pcm[i] = (s * 22000).toInt().toShort()
        }
        playPcm(pcm)
        vibrate(80, 160)
    }

    // Granny Club Attack Swing & Impact
    fun playClubAttack() {
        val numSamples = (sampleRate * 0.5).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            // Whoosh swoosh then heavy bone crunch impact
            val s = if (progress < 0.25) {
                val whoosh = (Random.nextDouble() - 0.5) * (progress / 0.25)
                whoosh * 0.6
            } else {
                val impactProgress = (progress - 0.25) / 0.75
                val env = exp(-impactProgress * 6.0)
                phase += (2.0 * Math.PI * 65.0) / sampleRate
                (sin(phase) * 0.8 + (Random.nextDouble() - 0.5) * 0.6) * env
            }
            pcm[i] = (s * 25000).toInt().toShort()
        }
        playPcm(pcm)
        vibrate(300, 240)
    }

    // Grandpa Shotgun Blast
    fun playShotgunBlast() {
        val numSamples = (sampleRate * 0.8).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 5.0)
            phase += (2.0 * Math.PI * (120.0 * (1.0 - progress * 0.6))) / sampleRate
            val s = ((Random.nextDouble() - 0.5) * 0.9 + sin(phase) * 0.5) * env
            pcm[i] = (s * 28000).toInt().toShort()
        }
        playPcm(pcm)
        vibrate(400, 255)
    }

    // Sledrina Screech Jump Scare
    fun playSledrinaScare() {
        val numSamples = (sampleRate * 2.2).toInt()
        val pcm = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val vibrato = sin(progress * 45.0) * 120.0
            val f1 = 850.0 + vibrato + (progress * 300.0)
            val f2 = 1205.0 + vibrato * 1.5 // Dissonant minor second / tritone clash
            phase1 += (2.0 * Math.PI * f1) / sampleRate
            phase2 += (2.0 * Math.PI * f2) / sampleRate

            val noise = (Random.nextDouble() - 0.5) * 0.35
            val env = if (progress < 0.08) progress / 0.08 else exp(-(progress - 0.08) * 1.8)
            val s = (sin(phase1) * 0.5 + sin(phase2) * 0.5 + noise) * env
            pcm[i] = (s * 26000).toInt().toShort()
        }
        playPcm(pcm)
        vibrate(600, 255)
    }

    // Angelene freed roar / scream
    fun playAngeleneFreed() {
        val numSamples = (sampleRate * 1.5).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 2.5)
            val freq = 420.0 + sin(progress * 25.0) * 80.0
            phase += (2.0 * Math.PI * freq) / sampleRate
            val s = (sin(phase) * 0.6 + (Random.nextDouble() - 0.5) * 0.5) * env
            pcm[i] = (s * 22000).toInt().toShort()
        }
        playPcm(pcm)
        vibrate(350, 220)
    }

    // Car Engine Start & Escape
    fun playVehicleEscape() {
        val numSamples = (sampleRate * 2.5).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val s = if (progress < 0.4) {
                // Starter crank chug chug chug
                val crank = sin(progress * 40.0 * Math.PI)
                crank * 0.5
            } else {
                // Engine roar vroom!
                val vroomProgress = (progress - 0.4) / 0.6
                val rpmFreq = 80.0 + vroomProgress * 140.0
                phase += (2.0 * Math.PI * rpmFreq) / sampleRate
                (sin(phase) * 0.7 + (Random.nextDouble() - 0.5) * 0.4)
            }
            pcm[i] = (s * 24000).toInt().toShort()
        }
        playPcm(pcm)
        vibrate(500, 200)
    }

    fun release() {
        stopAmbience()
        audioScope.cancel()
    }
}
