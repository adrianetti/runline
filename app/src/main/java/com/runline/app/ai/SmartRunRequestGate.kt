package com.runline.app.ai

import java.util.concurrent.atomic.AtomicBoolean

/** Single explicit request, with no queue; failed setup must not disable future attempts. */
class SmartRunRequestGate {
    private val active = AtomicBoolean()
    fun start(submit: () -> Unit): Boolean {
        if (!active.compareAndSet(false, true)) return false
        try { submit() } catch (failure: Throwable) { active.set(false); throw failure }
        return true
    }
    fun finished() { active.set(false) }
}
