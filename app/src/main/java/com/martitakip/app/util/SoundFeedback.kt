package com.martitakip.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/**
 * Short confirmation beep for the field.
 *
 * The operator works in traffic, often with gloves and with the phone in a
 * pocket; haptics alone are easy to miss. A short, quiet tone confirms that a
 * tap registered without forcing them to look at the screen.
 *
 * Design notes:
 *  - Uses [SoundPool] with a tiny generated tone, so there is no audio asset to
 *    ship and playback latency stays in the low milliseconds.
 *  - Routes to [AudioManager.STREAM_MUSIC]; it respects the user's volume and
 *    never overrides a silent/do-not-disturb setting.
 *  - The pool is released in [release]; callers must do that when the host
 *    goes away.
 */
class SoundFeedback(context: Context) {

    private val appContext = context.applicationContext

    private var soundPool: SoundPool? = null
    private var toneId = 0
    private var loaded = false

    /** Lazily builds the pool so an app that never plays a sound pays nothing. */
    private fun ensureLoaded() {
        if (soundPool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()

        pool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
        }
        toneId = pool.load(appContext, TONE_RES_ID, 1)
        soundPool = pool
    }

    /**
     * Plays the confirmation tone. [volume] is 0f..1f; the tone is deliberately
     * quiet by default so repeated taps are not annoying.
     */
    fun playConfirm(volume: Float = 0.45f) {
        ensureLoaded()
        val pool = soundPool ?: return
        if (!loaded) {
            // Not decoded yet; the next tap will succeed. Skipping is better
            // than blocking the UI thread waiting for the load callback.
            return
        }
        val stream = pool.play(toneId, volume, volume, 1, 0, 1f)
        if (stream == 0) {
            // Load was evicted (rare); rebuild once.
            release()
        }
    }

    /** Undo/negative feedback: same tone, quieter. */
    fun playUndo() = playConfirm(volume = 0.25f)

    fun release() {
        soundPool?.release()
        soundPool = null
        loaded = false
        toneId = 0
    }

    companion object {
        /**
         * A very short notification tone shipped as a raw resource. Keeping it
         * as a resource (rather than synthesising a PCM buffer at runtime) lets
         * the platform cache the decoded sample.
         */
        private val TONE_RES_ID: Int = com.martitakip.app.R.raw.swap_confirm
    }
}
