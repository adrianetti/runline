package com.runline.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.runline.app.R
import com.runline.app.core.RunPoint
import com.runline.app.terrain.TerrainRepository

internal object TerrainUi {
    fun status(context: Context, raw: List<RunPoint>, mapped: List<RunPoint>, repository: TerrainRepository): String {
        if (!repository.enabled) return context.getString(R.string.terrain_maps_off)
        val count = mapped.count { it.altitudeMeters != null }
        if (count > 0) return if (count == raw.size) context.getString(R.string.terrain_map_source)
            else context.getString(R.string.terrain_map_partial, (count * 100.0 / raw.size).toInt())
        return context.getString(if (repository.isLoading(raw)) R.string.terrain_maps_loading else R.string.terrain_maps_unavailable)
    }
    fun show(activity: AppCompatActivity): AiSheet {
        val repository = TerrainRepository.get(activity)
        val sheet = AiSheet(activity, R.string.terrain_maps_title)
        sheet.column.addView(MaterialSwitch(activity).apply {
            text = activity.getString(R.string.terrain_maps_download); minHeight = sheet.dp(56)
            isChecked = repository.enabled
            setOnCheckedChangeListener { _, checked -> repository.setEnabled(checked) }
        }, sheet.row(0, 12))
        sheet.column.addView(sheet.text(activity.getString(R.string.terrain_maps_description)), sheet.row(0, 16))
        sheet.column.addView(sheet.button(activity.getString(R.string.terrain_maps_clear), R.drawable.ic_trash).apply {
            setOnClickListener { repository.clearCache(); text = activity.getString(R.string.terrain_maps_cleared) }
        }, sheet.row(0, 12))
        sheet.column.addView(sheet.text(activity.getString(R.string.terrain_maps_credit)), sheet.row(0, 8))
        sheet.column.addView(sheet.button(activity.getString(R.string.terrain_maps_source_link), R.drawable.ic_info).apply {
            setOnClickListener { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ign.es/resources/licencia/Condiciones_licenciaUso_IGN.pdf"))) }
        }, sheet.row())
        sheet.show()
        return sheet
    }
}
