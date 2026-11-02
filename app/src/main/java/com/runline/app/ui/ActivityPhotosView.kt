package com.runline.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.R
import com.runline.app.data.RunStore
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Small private-photo previews, reusable in saved details and the editor. */
class ActivityPhotosView(context: Context, runId: String, photos: List<String>, onRemove: ((String) -> Unit)? = null) : HorizontalScrollView(context) {
    private var attached = true
    init {
        isHorizontalScrollBarEnabled = false
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        addView(row)
        photos.forEachIndexed { index, id ->
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val picture = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = context.getString(R.string.details_photo_description, index + 1)
                setBackgroundColor(context.getColor(R.color.surface_variant))
            }
            column.addView(picture, LinearLayout.LayoutParams(dp(144), dp(128)))
            val file = RunStore(context).photoFile(runId, id)
            photoWorker.execute {
                val bitmap = runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull()
                post {
                    if (!attached) { bitmap?.recycle(); return@post }
                    picture.setImageBitmap(bitmap)
                    if (bitmap == null) picture.contentDescription = context.getString(R.string.details_photo_missing)
                    else picture.setOnClickListener {
                        val enlarged = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(bitmap); adjustViewBounds = true }
                        val preview = MaterialAlertDialogBuilder(context).setView(enlarged).setPositiveButton(R.string.main_close, null).show()
                        photoWorker.execute {
                            val full = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
                            post {
                                if (preview.isShowing && full != null) enlarged.setImageBitmap(full)
                                else full?.recycle()
                            }
                        }
                    }
                }
            }
            if (onRemove != null) {
                column.addView(MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    setIconResource(R.drawable.ic_trash); iconSize = dp(22); iconPadding = 0; iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    text = ""; contentDescription = context.getString(R.string.details_remove_photo, index + 1)
                    minHeight = dp(48); minimumHeight = dp(48); minWidth = 0; minimumWidth = 0
                    setOnClickListener { onRemove(id) }
                }, LinearLayout.LayoutParams(dp(144), dp(48)))
            }
            row.addView(column, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(12) })
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); attached = true }
    override fun onDetachedFromWindow() { attached = false; super.onDetachedFromWindow() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    companion object { private val photoWorker = Executors.newSingleThreadExecutor() }
}
