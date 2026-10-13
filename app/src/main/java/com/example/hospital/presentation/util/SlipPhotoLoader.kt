package com.example.hospital.presentation.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.example.hospital.data.api.SlipPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val PHOTO_PX = 1600 // long side sent to the server (web: PHOTO_PX in assistant.js), ~300 kB as JPEG
private const val THUMB_PX = 160

/** A picked or taken slip photo, shrunk and JPEG-encoded like the web's shrinkPhoto. Null when it is not a readable image. */
suspend fun loadSlipPhoto(context: Context, uri: Uri): SlipPhoto? = withContext(Dispatchers.IO) {
    runCatching {
        // ImageDecoder turns the photo upright (EXIF). ponytail: below Android 9 a camera photo may arrive sideways; the model still reads it.
        val full = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val k = minOf(1f, PHOTO_PX.toFloat() / max(info.size.width, info.size.height))
                d.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_PX) sample *= 2
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@runCatching null
            scale(raw, PHOTO_PX)
        }
        val jpeg = ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        SlipPhoto("data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP), scale(full, THUMB_PX))
    }.getOrNull()
}

private fun scale(b: Bitmap, px: Int): Bitmap {
    val k = minOf(1f, px.toFloat() / max(b.width, b.height))
    return if (k >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * k).roundToInt(), (b.height * k).roundToInt(), true)
}
