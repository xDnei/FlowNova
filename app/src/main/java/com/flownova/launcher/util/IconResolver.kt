package com.flownova.launcher.util

import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object IconResolver {
    private val cache = LruCache<String, ImageBitmap>(180)

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

            if (!useDynamic) {
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
            } else {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = containerColor
                    style = Paint.Style.FILL
                }
                canvas.drawOval(RectF(4f, 4f, (size - 4).toFloat(), (size - 4).toFloat()), bgPaint)

                var drewMonochrome = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
                    val mono = drawable.monochrome
                    if (mono != null) {
                        val mutated = mono.mutate()
                        mutated.setBounds(-18, -18, size + 18, size + 18)
                        mutated.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                        val save = canvas.save()
                        val path = Path().apply {
                            addOval(RectF(8f, 8f, (size - 8).toFloat(), (size - 8).toFloat()), Path.Direction.CW)
                        }
                        canvas.clipPath(path)
                        mutated.draw(canvas)
                        canvas.restoreToCount(save)
                        drewMonochrome = true
                    }
                }

                if (!drewMonochrome) {
                    val pad = 38
                    val rawBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val rawCanvas = Canvas(rawBmp)
                    drawable.setBounds(pad, pad, size - pad, size - pad)
                    drawable.draw(rawCanvas)

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                    }
                    canvas.drawBitmap(rawBmp, 0f, 0f, paint)

                    val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = tintColor
                        alpha = 110
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                    }
                    canvas.drawOval(RectF(4f, 4f, (size - 4).toFloat(), (size - 4).toFloat()), tintPaint)
                }
            }

            val img = bitmap.asImageBitmap()
            cache.put(key, img)
            img
        }.getOrNull()
    }
}
