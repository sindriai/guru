package com.sindriai.guru.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

fun bitmapToUri(
    context: Context,
    bitmap: Bitmap,
    fileName: String = "attached_image.png"
): Uri {
    val cacheDir = File(context.cacheDir, "images")
    cacheDir.mkdirs()

    val file = File(cacheDir, fileName)
    val outputStream = FileOutputStream(file)

    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    outputStream.flush()
    outputStream.close()

    return Uri.fromFile(file)
}

fun uriToBitmap(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)

    val original = ImageDecoder.decodeBitmap(source)

    // ✅ FORCE convert to ARGB_8888 (Gemma requirement)
    return original.copy(Bitmap.Config.ARGB_8888, false)
}
