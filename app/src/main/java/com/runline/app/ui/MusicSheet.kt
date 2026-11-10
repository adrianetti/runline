package com.runline.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.R
import com.runline.app.music.MusicConfig
import com.runline.app.music.MusicMood
import com.runline.app.music.MusicSettings
import com.runline.app.music.PlaylistRef
import com.runline.app.music.SpotifyAccount
import com.runline.app.music.SpotifyFailure
import com.runline.app.music.SpotifyPkce
import com.runline.app.music.SpotifyPlaylist
import java.util.concurrent.Executors

/**
 * Music that follows the run. The top three switches work with any player; the Spotify part needs
 * the user's own Client ID, so the app works the same for everyone who builds or installs it.
 */
class MusicSheet(private val activity: AppCompatActivity, callback: String? = null) {
    private val sheet = AiSheet(activity, R.string.music_title)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val app = activity.applicationContext
    private val status = sheet.text("").apply { visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private var playlists: List<SpotifyPlaylist>? = null
    private var guideOpen = false
    private var draftClientId: String? = null
    val isShowing get() = sheet.isShowing

    init {
        render()
        if (callback != null) complete(callback) else if (SpotifyAccount.isConnected(activity)) loadPlaylists()
    }
    fun show() = sheet.show()
    fun close() { worker.shutdownNow(); sheet.close() }
    /** Spotify sent the browser back while this sheet was already open. */
    fun onCallback(uri: String) = complete(uri)

    private fun render() {
        val config = MusicSettings.load(activity)
        val connected = SpotifyAccount.isConnected(activity)
        sheet.column.removeAllViews()
        sheet.column.addView(toggle(R.string.music_enabled, config.enabled) { on -> change { it.copy(enabled = on) }; rerender() }, sheet.row(0, 4))
        sheet.column.addView(toggle(R.string.music_start, config.startWithRun, config.enabled) { on -> change { it.copy(startWithRun = on) } }, sheet.row())
        sheet.column.addView(toggle(R.string.music_pause, config.pauseWithRun, config.enabled) { on -> change { it.copy(pauseWithRun = on) } }, sheet.row())
        sheet.column.addView(sheet.text(activity.getString(R.string.music_any_player)), sheet.row(4, 4))
        sheet.column.addView(sheet.text("Spotify", 18, true), sheet.row(20, 8))
        // Directly under the heading, so a sign-in result is seen without scrolling.
        (status.parent as? android.view.ViewGroup)?.removeView(status)
        sheet.column.addView(status, sheet.row(0, 8))
        if (connected) renderConnected(config) else renderSetup()
    }

    private fun renderSetup() {
        val clientId = sheet.field(R.string.music_client_id, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        clientId.first.helperText = activity.getString(R.string.music_client_id_hint)
        clientId.second.apply {
            isSaveEnabled = false; filters = arrayOf(InputFilter.LengthFilter(32))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setText(draftClientId ?: SpotifyAccount.clientId(activity))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(text: android.text.Editable?) { draftClientId = text.toString(); clientId.first.error = null }
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            })
        }
        sheet.column.addView(sheet.button(activity.getString(R.string.music_connect), R.drawable.ic_music, filled = true).apply {
            setOnClickListener {
                val id = clientId.second.text.toString().trim().lowercase()
                if (!SpotifyPkce.validClientId(id)) { clientId.first.error = activity.getString(R.string.music_client_id_error); return@setOnClickListener }
                val url = runCatching { SpotifyAccount.saveClientId(activity, id); SpotifyAccount.begin(activity) }
                    .getOrElse { say(R.string.music_error_storage); return@setOnClickListener }
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { say(R.string.music_error_browser) }
            }
        }, sheet.row(0, 8))
        val guide = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = if (guideOpen) View.VISIBLE else View.GONE }
        guide.addView(sheet.text(activity.getString(R.string.music_setup_steps, SpotifyPkce.REDIRECT_URI)), sheet.row(0, 8))
        guide.addView(sheet.button(activity.getString(R.string.music_copy_redirect), R.drawable.ic_check).apply {
            setOnClickListener {
                activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Redirect URI", SpotifyPkce.REDIRECT_URI))
                say(R.string.music_copied)
            }
        }, sheet.row())
        guide.addView(sheet.button(activity.getString(R.string.music_open_dashboard), R.drawable.ic_external_link).apply {
            setOnClickListener { runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyPkce.DASHBOARD_URL))) }.onFailure { say(R.string.music_error_browser) } }
        }, sheet.row())
        sheet.column.addView(sheet.button(activity.getString(R.string.music_setup), R.drawable.ic_info).apply {
            setOnClickListener { guideOpen = !guideOpen; guide.visibility = if (guideOpen) View.VISIBLE else View.GONE }
        }, sheet.row())
        sheet.column.addView(guide, sheet.row())
    }

    private fun renderConnected(config: MusicConfig) {
        val name = SpotifyAccount.accountName(activity)
        sheet.column.addView(sheet.text(if (name.isNullOrBlank()) activity.getString(R.string.music_connected_plain) else activity.getString(R.string.music_connected, name)), sheet.row(0, 8))
        sheet.column.addView(toggle(R.string.music_adaptive, config.adaptive, config.enabled) { on -> change { it.copy(adaptive = on) } }, sheet.row(0, 8))
        playlistPicker(R.string.music_calm, MusicMood.CALM, config.calm)
        playlistPicker(R.string.music_steady, MusicMood.STEADY, config.steady)
        playlistPicker(R.string.music_push, MusicMood.PUSH, config.push)
        sheet.column.addView(sheet.button(activity.getString(R.string.music_disconnect), R.drawable.ic_trash).apply {
            setOnClickListener {
                runCatching { SpotifyAccount.disconnect(activity); MusicSettings.forgetAccount(activity) }
                playlists = null; status.visibility = View.GONE; render()
            }
        }, sheet.row(8, 0))
    }

    private fun playlistPicker(label: Int, mood: MusicMood, saved: PlaylistRef?) {
        val known = playlists.orEmpty().map { PlaylistRef(it.id, it.name) }
        val choices = (known + listOfNotNull(saved?.takeIf { ref -> known.none { it.id == ref.id } }))
        val wrapper = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = activity.getString(label); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
            if (playlists == null) helperText = activity.getString(R.string.music_loading)
        }
        val names = listOf(activity.getString(R.string.music_none)) + choices.map { it.name }
        val choice = MaterialAutoCompleteTextView(wrapper.context).apply {
            inputType = InputType.TYPE_NULL; minHeight = sheet.dp(56)
            setPadding(sheet.dp(16), sheet.dp(12), sheet.dp(16), sheet.dp(12))
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, names))
            setText(names[choices.indexOfFirst { it.id == saved?.id } + 1], false)
            setOnItemClickListener { _, _, index, _ ->
                val picked = choices.getOrNull(index - 1)
                change { when (mood) { MusicMood.CALM -> it.copy(calm = picked); MusicMood.STEADY -> it.copy(steady = picked); MusicMood.PUSH -> it.copy(push = picked) } }
            }
        }
        wrapper.addView(choice, LinearLayout.LayoutParams(-1, -2))
        sheet.column.addView(wrapper, sheet.row(4, 12))
    }

    private fun complete(callback: String) {
        say(R.string.music_connecting)
        worker.execute {
            val result = runCatching { SpotifyAccount.complete(app, callback) }
            main.post {
                if (sheet.closed) return@post
                result.fold({ outcome ->
                    when (outcome) {
                        SpotifyAccount.Outcome.CONNECTED -> {
                            // Connecting is the wish to use it; the switch need not be found again.
                            change { it.copy(enabled = true) }
                            status.visibility = View.GONE; render(); loadPlaylists()
                        }
                        SpotifyAccount.Outcome.DENIED -> say(R.string.music_error_denied)
                        SpotifyAccount.Outcome.STALE -> say(R.string.music_error_stale)
                    }
                }, { say(message(it)) })
            }
        }
    }

    private fun loadPlaylists() {
        worker.execute {
            val result = runCatching { SpotifyAccount.api(app).playlists() }
            main.post {
                if (sheet.closed) return@post
                result.fold({ playlists = it; render() }, { playlists = emptyList(); render(); say(message(it)) })
            }
        }
    }

    private fun message(error: Throwable): String = when (error) {
        is SpotifyFailure -> activity.getString(when (error.reason) {
            SpotifyFailure.Reason.AUTH -> R.string.music_error_auth
            SpotifyFailure.Reason.PREMIUM -> R.string.music_error_premium
            SpotifyFailure.Reason.NOT_ALLOWED -> R.string.music_error_not_allowed
            SpotifyFailure.Reason.RATE_LIMIT -> R.string.music_error_rate
            SpotifyFailure.Reason.NETWORK -> R.string.music_error_network
            SpotifyFailure.Reason.NO_DEVICE, SpotifyFailure.Reason.RESPONSE -> R.string.music_error_response
        })
        else -> activity.getString(R.string.music_error_storage)
    }

    private fun change(update: (MusicConfig) -> MusicConfig) { runCatching { MusicSettings.save(activity, update(MusicSettings.load(activity))) }.onFailure { say(R.string.music_error_storage) } }
    /** A switch must not rebuild its own row while it is still reporting the change. */
    private fun rerender() { sheet.column.post { if (!sheet.closed) render() } }
    private fun say(message: Int) = say(activity.getString(message))
    private fun say(message: String) { status.text = message; status.visibility = View.VISIBLE }
    private fun toggle(label: Int, checked: Boolean, enabled: Boolean = true, changed: (Boolean) -> Unit) = MaterialSwitch(activity).apply {
        setText(label); isChecked = checked; isEnabled = enabled; minHeight = sheet.dp(56); setPadding(0, sheet.dp(8), 0, sheet.dp(8))
        setOnCheckedChangeListener { _, value -> changed(value) }
    }
}
