package com.runline.app.ui

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.R
import kotlin.math.roundToInt

/** A short dialog list where every option carries its Tabler icon, aligned on one start edge. */
internal object IconChoices {
    data class Choice(val label: String, val icon: Int)

    fun show(activity: AppCompatActivity, title: Int, choices: List<Choice>, onPick: (Int) -> Unit) {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
        val adapter = object : ArrayAdapter<Choice>(activity, android.R.layout.simple_list_item_1, choices) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    val choice = choices[position]
                    text = choice.label; textSize = 16f; minHeight = dp(56)
                    setPaddingRelative(dp(24), 0, dp(24), 0)
                    compoundDrawablePadding = dp(20)
                    setCompoundDrawablesRelative(AppCompatResources.getDrawable(context, choice.icon)?.mutate()?.apply {
                        setTint(context.getColor(R.color.on_surface)); setBounds(0, 0, dp(24), dp(24))
                    }, null, null, null)
                }
        }
        MaterialAlertDialogBuilder(activity).setTitle(title).setAdapter(adapter) { _, which -> onPick(which) }.show()
    }
}
