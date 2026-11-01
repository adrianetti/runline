package com.runline.app.coaching

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.runline.app.R
import com.runline.app.core.CoachCue
import com.runline.app.core.PendingCoachGoal
import com.runline.app.core.SpeechLease
import com.runline.app.core.SpeechOutput
import com.runline.app.i18n.AppLanguage
import kotlin.math.roundToInt

enum class SpeechResult { READY, UNAVAILABLE, AUDIO_BUSY, ERROR }

/**
 * Short, optional speech using an installed offline system voice. All engine/focus
 * operations are serialized on main; recording persistence never waits for speech.
 */
class CoachSpeech(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var engine: TextToSpeech? = null
    private var initializing = false
    private var initialized = false
    private var initializationSerial = 0L
    private var initializationTimeout: Runnable? = null
    private var closed = false
    private var serial = 0L
    private var language: String? = null
    private var pendingPreview: ((SpeechResult) -> Unit)? = null
    private var utterancePreview: ((SpeechResult) -> Unit)? = null
    private var currentId: String? = null
    private var timeout: Runnable? = null
    private val pendingGoal = PendingCoachGoal()
    private val retryGoal = Runnable { drainPendingGoal() }
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                completePreview(SpeechResult.AUDIO_BUSY)
                stopInternal()
            }
        }, main).build()
    private val lease = SpeechLease(object : SpeechOutput {
        override fun requestFocus() = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        override fun abandonFocus() { audio.abandonAudioFocusRequest(focus) }
        override fun speak(text: String, id: String) =
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) == TextToSpeech.SUCCESS
        override fun stopSpeech() { engine?.stop() }
    })

    fun prepare() = onMain { prepareInternal() }
    fun speakPreview(onResult: (SpeechResult) -> Unit) = onMain {
        if (closed) { onResult(SpeechResult.ERROR); return@onMain }
        if (lease.isBusy || pendingPreview != null || pendingGoal.hasPending(SystemClock.elapsedRealtime())) {
            onResult(SpeechResult.AUDIO_BUSY); return@onMain
        }
        if (!initialized) {
            pendingPreview = onResult
            prepareInternal()
            if (!initializing && !initialized) { pendingPreview = null; onResult(SpeechResult.UNAVAILABLE) }
        } else speakInternal(tr(R.string.coach_voice_preview), onResult)
    }
    fun speak(cue: CoachCue) = onMain {
        if (closed) return@onMain
        if (cue is CoachCue.GoalReached) {
            pendingGoal.offer(cue, SystemClock.elapsedRealtime())
            drainPendingGoal()
            return@onMain
        }
        if (pendingGoal.hasPending(SystemClock.elapsedRealtime())) { drainPendingGoal(); return@onMain }
        if (!initialized || lease.isBusy) return@onMain
        speakInternal(cueText(cue), null)
    }
    fun stop() = onMain { stopInternal() }
    fun close() = onMain {
        if (closed) return@onMain
        closed = true
        stopInternal()
        initializationSerial++
        initializationTimeout?.let(main::removeCallbacks); initializationTimeout = null
        runCatching { engine?.shutdown() }
        engine = null; initialized = false; initializing = false
    }

    private fun prepareInternal() {
        if (closed || initialized || initializing) return
        runCatching { engine?.shutdown() }
        engine = null; language = null
        val generation = ++initializationSerial
        initializing = true
        try {
            engine = TextToSpeech(context) { status -> main.post {
                if (closed || generation != initializationSerial) return@post
                initializationTimeout?.let(main::removeCallbacks); initializationTimeout = null
                initializing = false
                initialized = status == TextToSpeech.SUCCESS
                if (initialized) {
                    runCatching {
                        engine?.setAudioAttributes(attributes)
                        engine?.setSpeechRate(1.0f)
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String?) { main.post { if (id == currentId) completePreview(SpeechResult.READY) } }
                            override fun onDone(id: String?) { main.post { finish(id, null) } }
                            @Deprecated("Required legacy callback")
                            override fun onError(id: String?) { main.post { finish(id, SpeechResult.ERROR) } }
                            override fun onError(id: String?, errorCode: Int) { main.post { finish(id, SpeechResult.ERROR) } }
                            override fun onStop(id: String?, interrupted: Boolean) { main.post { finish(id, SpeechResult.ERROR) } }
                        })
                    }.onFailure { initialized = false }
                }
                val preview = pendingPreview; pendingPreview = null
                if (preview != null) {
                    if (initialized) speakInternal(tr(R.string.coach_voice_preview), preview)
                    else preview(SpeechResult.UNAVAILABLE)
                }
                if (initialized) drainPendingGoal() else clearPendingGoal()
            } }
        } catch (_: Exception) {
            initializing = false; initialized = false
            clearPendingGoal()
        }
        // A broken TTS engine must never keep a preview request hanging indefinitely.
        val initGuard = Runnable {
            if (initializing && !closed && generation == initializationSerial) {
                initializing = false
                initializationSerial++
                runCatching { engine?.shutdown() }; engine = null
                val preview = pendingPreview; pendingPreview = null
                preview?.invoke(SpeechResult.UNAVAILABLE)
                clearPendingGoal()
            }
        }
        initializationTimeout = initGuard
        main.postDelayed(initGuard, 15_000)
    }

    private fun ensureVoice(): Boolean {
        val selected = AppLanguage.localizedContext(context).resources.configuration.locales[0]
        val desired = if (selected.language == "es") "es" else "en"
        if (language == desired) return true
        val tts = engine ?: return false
        val voices = tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired && it.locale.language == desired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
        val voice = voices.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == selected.country }
            .thenByDescending { it.quality }).firstOrNull() ?: return false
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS) return false
        language = desired
        return true
    }

    private fun speakInternal(text: String, preview: ((SpeechResult) -> Unit)?) {
        if (closed || !initialized) { preview?.invoke(SpeechResult.UNAVAILABLE); return }
        if (lease.isBusy || (speakingInstance != null && speakingInstance !== this)) {
            preview?.invoke(SpeechResult.AUDIO_BUSY); return
        }
        if (!runCatching { ensureVoice() }.getOrDefault(false)) { preview?.invoke(SpeechResult.UNAVAILABLE); return }
        val id = "runline-coach-${++serial}"
        currentId = id; utterancePreview = preview
        if (!lease.begin(text, id)) {
            currentId = null
            completePreview(SpeechResult.AUDIO_BUSY)
            return
        }
        speakingInstance = this
        val guard = Runnable {
            if (currentId == id) {
                completePreview(SpeechResult.ERROR)
                lease.timeout(id); currentId = null; timeout = null
                if (speakingInstance === this) speakingInstance = null
                clearPendingGoal()
            }
        }
        timeout = guard
        // Short prompts should take a few seconds. Never keep another app ducked.
        main.postDelayed(guard, 15_000)
    }

    private fun finish(id: String?, error: SpeechResult?) {
        if (id == null || id != currentId) return
        timeout?.let(main::removeCallbacks); timeout = null
        lease.finish(id); currentId = null
        if (speakingInstance === this) speakingInstance = null
        completePreview(error ?: SpeechResult.READY)
        if (error == null) drainPendingGoal() else clearPendingGoal()
    }
    private fun completePreview(result: SpeechResult) { val callback = utterancePreview; utterancePreview = null; callback?.invoke(result) }
    private fun stopInternal() {
        clearPendingGoal()
        pendingPreview = null; utterancePreview = null; currentId = null
        timeout?.let(main::removeCallbacks); timeout = null
        lease.stop()
        if (speakingInstance === this) speakingInstance = null
    }
    private fun drainPendingGoal() {
        main.removeCallbacks(retryGoal)
        val now = SystemClock.elapsedRealtime()
        if (closed || (!initialized && !initializing)) { clearPendingGoal(); return }
        if (!pendingGoal.hasPending(now)) return
        val available = initialized && !lease.isBusy && speakingInstance == null
        val goal = pendingGoal.takeWhenReady(now, available)
        if (goal != null) {
            // Focus/voice failures deliberately drop this one attempt; do not fight calls
            // or replay a goal later when the run has already moved on.
            speakInternal(cueText(goal), null)
        } else main.postDelayed(retryGoal, 250)
    }
    private fun clearPendingGoal() { pendingGoal.clear(); main.removeCallbacks(retryGoal) }
    private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }
    private fun tr(id: Int, vararg args: Any): String = AppLanguage.localizedContext(context).getString(id, *args)
    private fun cueText(cue: CoachCue): String = when (cue) {
        is CoachCue.Kilometre -> {
            val local = AppLanguage.localizedContext(context)
            val km = local.resources.getQuantityString(R.plurals.coach_voice_kilometres, cue.completed, cue.completed)
            val pace = cue.averagePaceSeconds?.roundToInt()
            if (pace == null) km else km + " " + tr(R.string.coach_voice_average_pace, pace / 60, pace % 60)
        }
        CoachCue.OneKilometreRemaining -> tr(R.string.coach_voice_one_remaining)
        is CoachCue.GoalReached -> tr(when (cue.withinTime) {
            true -> R.string.coach_voice_goal_in_time
            false -> R.string.coach_voice_goal_over_time
            null -> R.string.coach_voice_goal_reached
        })
        CoachCue.SlowerThanTarget -> tr(R.string.coach_voice_slower)
        CoachCue.FasterThanTarget -> tr(R.string.coach_voice_faster)
        CoachCue.BackOnPace -> tr(R.string.coach_voice_on_pace)
    }

    companion object {
        // Main-thread ownership prevents a settings preview and background cue overlapping.
        private var speakingInstance: CoachSpeech? = null
    }
}
