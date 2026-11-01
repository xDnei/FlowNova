package com.flownova.launcher.util

import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object IconResolver {
    private val cache = LruCache<String, ImageBitmap>(200)

    fun clearCache() {
        cache.evictAll()
    }

    fun loadIcon(
        pm: PackageManager,
        packageName: String,
        useDynamic: Boolean,
        tintColor: Int,
        containerColor: Int
    ): ImageBitmap? {
        val key = packageName + "_" + useDynamic + "_" + tintColor + "_" + containerColor
        cache.get(key)?.let { return it }

        return runCatching {
            val drawable = pm.getApplicationIcon(packageName)
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val rect = RectF(4f, 4f, (size - 4).toFloat(), (size - 4).toFloat())
            val corner = size * 0.28f

            val clipPath = Path().apply {
                addRoundRect(rect, corner, corner, Path.Direction.CW)
            }

            if (!useDynamic) {
                val save = canvas.save()
                canvas.clipPath(clipPath)
                drawable.setBounds(4, 4, size - 4, size - 4)
                drawable.draw(canvas)
                canvas.restoreToCount(save)
            } else {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, size.toFloat(), size.toFloat(),
                        containerColor, tintColor, Shader.TileMode.CLAMP
                    )
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(rect, corner, corner, bgPaint)

                var drewMono = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
                    val mono = drawable.monochrome
                    if (mono != null) {
                        val mutated = mono.mutate()
                        mutated.setBounds(-16, -16, size + 16, size + 16)
                        mutated.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                        val save = canvas.save()
                        canvas.clipPath(clipPath)
                        mutated.draw(canvas)
                        canvas.restoreToCount(save)
                        drewMono = true
                    }
                }

                if (!drewMono) {
                    val save = canvas.save()
                    canvas.clipPath(clipPath)
                    val pad = 26
                    drawable.setBounds(pad, pad, size - pad, size - pad)
                    drawable.draw(canvas)

                    val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = tintColor
                        alpha = 75
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                    }
                    canvas.drawRoundRect(rect, corner, corner, overlayPaint)
                    canvas.restoreToCount(save)
                }
            }

            val img = bitmap.asImageBitmap()
            cache.put(key, img)
            img
        }.getOrNull()
    }
}
