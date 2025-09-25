package com.meenbeese.chronos.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

import com.meenbeese.chronos.data.PreferenceEntry
import com.meenbeese.chronos.db.AlarmSerdes

import kotlinx.coroutines.launch

@Composable
fun FileChooserScreen(
    type: String,
    preference: PreferenceEntry.StringPref?,
    onFileChosen: (name: String, uriString: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val hasLaunched = remember { mutableStateOf(false) }

    val permission = when (type) {
        FileChooserType.IMAGE -> FileChooserType.READ_MEDIA_IMAGES
        FileChooserType.AUDIO -> FileChooserType.READ_MEDIA_AUDIO
        else -> null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFileChooser(type)
        } else {
            Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = uri.toString()
            coroutineScope.launch { preference?.set(context, path) }
            onFileChosen("Image File", path)
        } ?: onDismiss()
    }

    val jsonExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val serdes = AlarmSerdes()
                    val result = serdes.exportAlarmDataAsJson(context)
                    result.fold(
                        ifLeft = { e ->
                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        },
                        ifRight = { (json, count) ->
                            outputStream.write(json.toByteArray(Charsets.UTF_8))
                            Toast.makeText(context, "Exported $count alarms", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                onDismiss()
            }
        } else {
            onDismiss()
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = getDisplayName(context, uri) ?: "Selected File"
            val path = uri.toString()

            coroutineScope.launch {
                if (type == FileChooserType.IMPORT_JSON) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val json = inputStream?.bufferedReader()?.use { it.readText() }
                    if (json != null) {
                        val serdes = AlarmSerdes()
                        val result = serdes.importAlarmDataFromJson(context, json)
                        result.fold(
                            ifLeft = { e ->
                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            },
                            ifRight = { count ->
                                Toast.makeText(context, "Imported $count alarms", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Regular file preference
                    preference?.set(context, path)
                    onFileChosen(name, path)
                }
            }
        } ?: onDismiss()
    }

    LaunchedEffect(Unit) {
        if (!hasLaunched.value) {
            hasLaunched.value = true

            when (type) {
                FileChooserType.EXPORT_JSON -> {
                    jsonExporter.launch("app_data_export.json")
                }
                FileChooserType.IMPORT_JSON -> {
                    Toast.makeText(context, "Warning: Existing alarms will be deleted!", Toast.LENGTH_LONG).show()
                    startFileChooser(type, openDocumentLauncher, context)
                }
                FileChooserType.IMAGE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else if (permission != null && ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(permission)
                    } else {
                        startFileChooser(type, openDocumentLauncher, context)
                    }
                }
                else -> {
                    startFileChooser(type, openDocumentLauncher, context)
                }
            }
        }
    }
}

private fun startFileChooser(
    type: String,
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>? = null,
    context: Context? = null,
    onDismiss: (() -> Unit)? = null
) {
    val mimeTypes = when (type) {
        FileChooserType.IMAGE -> arrayOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/bmp",
            "image/heif"
        )
        FileChooserType.AUDIO -> arrayOf(
            "audio/mpeg",
            "audio/mp4",
            "audio/ogg",
            "audio/aac",
            "audio/opus",
            "audio/flac"
        )
        FileChooserType.IMPORT_JSON -> arrayOf("application/json")
        else -> arrayOf("*/*")
    }

    try {
        val pm = context?.packageManager
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = mimeTypes.firstOrNull() ?: "*/*"
        }
        val resolveInfo = pm?.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo == null) {
            Toast.makeText(context, "No file manager found", Toast.LENGTH_SHORT).show()
            onDismiss?.invoke()
            return
        }

        launcher?.launch(mimeTypes)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No file manager available", Toast.LENGTH_SHORT).show()
        onDismiss?.invoke()
    }
}

private fun getDisplayName(
    context: Context,
    uri: Uri
): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (index != -1) it.getString(index) else null
        } else null
    }
}

object FileChooserType {
    const val IMAGE = "image/*"
    const val AUDIO = "audio/*"
    const val EXPORT_JSON = "export_json"
    const val IMPORT_JSON = "import_json"
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
}
