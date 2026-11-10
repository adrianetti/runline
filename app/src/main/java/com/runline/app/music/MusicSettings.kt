package com.runline.app.music

import android.content.Context

/** Optional and off by default. A music problem never reaches the recorder. */
object MusicSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("music_settings", Context.MODE_PRIVATE)

    fun load(context: Context): MusicConfig {
        val p = prefs(context)
        fun playlist(key: String) = p.getString("$key.id", null)?.let { PlaylistRef(it, p.getString("$key.name", null).orEmpty()) }?.takeIf { it.valid() }
        return MusicConfig(p.getBoolean("enabled", false), p.getBoolean("start", true), p.getBoolean("pause", true),
            p.getBoolean("adaptive", true), playlist("calm"), playlist("steady"), playlist("push"))
    }

    fun save(context: Context, config: MusicConfig) {
        require(config.valid())
        val editor = prefs(context).edit().putBoolean("enabled", config.enabled).putBoolean("start", config.startWithRun)
            .putBoolean("pause", config.pauseWithRun).putBoolean("adaptive", config.adaptive)
        mapOf("calm" to config.calm, "steady" to config.steady, "push" to config.push).forEach { (key, playlist) ->
            if (playlist == null) editor.remove("$key.id").remove("$key.name")
            else editor.putString("$key.id", playlist.id).putString("$key.name", playlist.name)
        }
        check(editor.commit())
        RunMusic.reload()
    }

    /** Disconnecting keeps the on/off choices but forgets everything that came from the account. */
    fun forgetAccount(context: Context) = save(context, load(context).copy(calm = null, steady = null, push = null))
}
