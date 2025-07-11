package com.meenbeese.chronos.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.get

import coil3.compose.rememberAsyncImagePainter

import com.meenbeese.chronos.R
import com.meenbeese.chronos.data.Preferences

import java.io.File

object ImageUtils {
    @Composable
    private fun getBackgroundImageAsync(): Painter {
        val context = LocalContext.current
        val backgroundUrl = Preferences.BACKGROUND_IMAGE.get(context)

        return when {
            backgroundUrl.startsWith("drawable/") -> {
                val resName = backgroundUrl.removePrefix("drawable/")
                val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                if (resId != 0) painterResource(id = resId)
                else painterResource(id = R.drawable.snowytrees)
            }
            backgroundUrl.startsWith("http") ||
            backgroundUrl.startsWith("content://") -> {
                rememberAsyncImagePainter(model = backgroundUrl)
            }
            backgroundUrl.isNotEmpty() -> {
                val file = File(backgroundUrl)
                rememberAsyncImagePainter(model = Uri.fromFile(file))
            }
            else -> painterResource(id = R.drawable.snowytrees)
        }
    }

    @Composable
    fun getBackgroundPainter(isAlarm: Boolean): Painter? {
        val context = LocalContext.current

        return if (!isAlarm || Preferences.RINGING_BACKGROUND_IMAGE.get(context)) {
            if (Preferences.COLORFUL_BACKGROUND.get(context)) {
                val colorInt = Preferences.BACKGROUND_COLOR.get(context)
                val color = androidx.compose.ui.graphics.Color(colorInt)
                ColorPainter(color)
            } else {
                getBackgroundImageAsync()
            }
        } else {
            null
        }
    }

    fun isBitmapDark(bitmap: Bitmap, sampleSize: Int = 10): Boolean {
        var darkPixels = 0
        var totalPixels = 0

        val stepX = bitmap.width / sampleSize
        val stepY = bitmap.height / sampleSize

        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val color = bitmap[x, y]
                if (isColorDark(color)) {
                    darkPixels++
                }
                totalPixels++
            }
        }
        return darkPixels >= totalPixels / 2
    }

    fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (
            0.299 * Color.red(color) +
            0.587 * Color.green(color) +
            0.114 * Color.blue(color)
        ) / 255
        return darkness >= 0.5
    }
}
