package com.meenbeese.chronos.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.get
import androidx.core.net.toUri

import arrow.core.Either
import arrow.core.getOrElse

import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

import com.meenbeese.chronos.R
import com.meenbeese.chronos.data.Preferences
import com.meenbeese.chronos.ext.getFlow

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
    fun getBackgroundImageAsync(url: String, context: Context): Painter {
        return when {
            url.startsWith("drawable/") -> {
                val resName = url.removePrefix("drawable/")
                val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                if (resId != 0) painterResource(id = resId)
                else painterResource(id = R.drawable.snowytrees)
            }
            url.startsWith("http") || url.startsWith("content://") -> {
                rememberAsyncImagePainter(model = url)
            }
            url.isNotEmpty() -> {
                val file = File(url)
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
                val color = Color(colorInt)
                ColorPainter(color)
            } else {
                getBackgroundImageAsync()
            }
        } else {
            null
        }
    }

    @Composable
    fun rememberBackgroundPainterState(isAlarm: Boolean): Painter? {
        val context = LocalContext.current

        val colorfulBg by Preferences.COLORFUL_BACKGROUND.getFlow(context).collectAsState(initial = false)
        val bgColor by Preferences.BACKGROUND_COLOR.getFlow(context).collectAsState(initial = 0)
        val bgImage by Preferences.BACKGROUND_IMAGE.getFlow(context).collectAsState(initial = "")

        if (!isAlarm || Preferences.RINGING_BACKGROUND_IMAGE.get(context)) {
            return if (colorfulBg) {
                ColorPainter(Color(bgColor))
            } else {
                getBackgroundImageAsync(bgImage, context)
            }
        }
        return null
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

    fun isColorDark(colorInt: Int): Boolean {
        val color = Color(colorInt)
        val darkness = 1 - (
            0.299 * color.red +
            0.587 * color.green +
            0.114 * color.blue
        )
        return darkness >= 0.5
    }

    suspend fun getContrastingTextColorFromBg(context: Context): Color {
        val backgroundImage = Preferences.BACKGROUND_IMAGE.get(context)

        val result: Either<Throwable, Color> = Either.catch {
            val imageRequest = ImageRequest.Builder(context)
                .data(backgroundImage.toUri())
                .size(200, 200)
                .allowHardware(false)
                .build()

            val drawable = context.imageLoader.execute(imageRequest).image
            val bitmap = drawable?.toBitmap()
            val isDark = bitmap?.let { isBitmapDark(it) } ?: false

            if (isDark) Color.LightGray else Color.DarkGray
        }

        return result.getOrElse { Color.DarkGray }
    }
}
