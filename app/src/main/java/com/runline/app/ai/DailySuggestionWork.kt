package com.runline.app.ai

import com.runline.app.core.SmartRunSuggestion
import java.time.LocalDate
import java.util.UUID

data class SmartRunAiState(val running: Boolean, val suggestion: SmartRunSuggestion? = null,
    val failure: AiFailure.Reason? = null, val id: String? = null, val changed: Boolean = false, val preparedAtMs: Long? = null)

/** Serialized by the controller. A reservation survives provider failure or process death. */
object DailySuggestionWork {
    fun execute(today: LocalDate, now: Long, zone: String, inputKey: String, explicit: Boolean,
                previous: DailySuggestion?, persist: (DailySuggestion) -> Unit,
                beforeRequest: () -> Unit, request: () -> SmartRunSuggestion,
                isCurrent: () -> Boolean): SmartRunAiState {
        fun requireCurrent() { if (!isCurrent()) throw AiFailure(AiFailure.Reason.DISABLED) }
        requireCurrent()
        if (!explicit && previous?.day == today.toString() && now >= previous.attemptedAt &&
            previous.inputKey == inputKey && previous.suggestion != null)
            return SmartRunAiState(false, previous.suggestion, id = previous.token, preparedAtMs = previous.attemptedAt)
        if (!explicit && previous != null && !previous.canAutomaticallyRequest(today, now, zone))
            return SmartRunAiState(false, changed = previous.suggestion != null,
                failure = if (previous.suggestion == null) AiFailure.Reason.RESPONSE else null)
        if (explicit && previous != null && now - previous.attemptedAt < 60_000)
            throw AiFailure(AiFailure.Reason.RATE_LIMIT)
        beforeRequest() // Offline/key failures do not consume a provider attempt.
        requireCurrent()
        val reservation = DailySuggestion(today.toString(), zone, now, inputKey, UUID.randomUUID().toString(),
            attemptedDays = (previous?.attemptedDays.orEmpty() + today.toString()).distinct().takeLast(14))
        persist(reservation)
        requireCurrent()
        val result = request()
        requireCurrent()
        persist(reservation.copy(suggestion = result))
        return SmartRunAiState(false, result, id = reservation.token, preparedAtMs = reservation.attemptedAt)
    }
}
