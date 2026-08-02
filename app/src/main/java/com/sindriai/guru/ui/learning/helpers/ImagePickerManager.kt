package com.sindriai.guru.ui.learning.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class ImagePickerManager(
    val pickFromGallery: () -> Unit,
    val takeFromCamera: () -> Unit
)

@Composable
fun rememberImagePickerManager(
    context: Context,
    onImagePicked: (Uri) -> Unit
): ImagePickerManager {

    // ---------------- Android Photo Picker ----------------
    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let(onImagePicked)
        }

    // ---------------- Camera ----------------

    var currentCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                currentCameraUri?.let(onImagePicked)
            }
        }

    fun createNewCameraUri(): Uri {
        val file = File.createTempFile(
            "camera_${System.currentTimeMillis()}_",
            ".jpg",
            context.cacheDir
        )

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    // ---------------- Camera permission ----------------

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val uri = createNewCameraUri()
                currentCameraUri = uri
                cameraLauncher.launch(uri)
            }
        }

    // ---------------- Gallery picker (NO PERMISSION REQUIRED) ----------------

    fun pickGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun pickCamera() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val uri = createNewCameraUri()
            currentCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    return remember {
        ImagePickerManager(
            pickFromGallery = { pickGallery() },
            takeFromCamera = { pickCamera() }
        )
    }
}