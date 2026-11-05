package com.runline.app.updates

object ReleaseCheckPolicy {
    fun shouldCheck(now: Long, checked: Long, retryAt: Long, hasLatest: Boolean, force: Boolean, checking: Boolean): Boolean =
        !checking && now >= retryAt && (force || !hasLatest || now - checked !in 0 until 6 * 60 * 60_000L)
    fun retryAt(now: Long, code: Int, retryAfter: String?, rateReset: String?): Long {
        if (code != 403 && code != 429) return now + 60_000L
        val seconds = retryAfter?.toLongOrNull()?.coerceIn(60, 86400)
        val reset = rateReset?.toLongOrNull()?.takeIf { it in (now / 1000)..(now / 1000 + 86400) }
        return maxOf(now + 15 * 60_000, seconds?.let { now + it * 1000 } ?: 0, reset?.times(1000) ?: 0)
    }
}
