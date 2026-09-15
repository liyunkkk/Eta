package io.github.mangi.eta.ui.components

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 透明的 Trampoline Activity，用于在悬浮窗 Service 等无法直接提供 LocalActivityResultRegistryOwner 的场景中
 * 调起系统相册、文件选择器或目录选择器，并通过静态回调将结果安全回传。
 */
class AgentAttachmentPickerTrampolineActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_PICK_IMAGES = "pick_images"
        const val ACTION_PICK_FILES = "pick_files"
        const val ACTION_PICK_FOLDER = "pick_folder"

        private var onImagesCallback: ((List<String>) -> Unit)? = null
        private var onFilesCallback: ((List<String>) -> Unit)? = null
        private var onFolderCallback: ((String) -> Unit)? = null

        fun pickImages(context: Context, onResult: (List<String>) -> Unit) {
            onImagesCallback = onResult
            val intent = Intent(context, AgentAttachmentPickerTrampolineActivity::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_PICK_IMAGES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun pickFiles(context: Context, onResult: (List<String>) -> Unit) {
            onFilesCallback = onResult
            val intent = Intent(context, AgentAttachmentPickerTrampolineActivity::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_PICK_FILES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun pickFolder(context: Context, onResult: (String) -> Unit) {
            onFolderCallback = onResult
            val intent = Intent(context, AgentAttachmentPickerTrampolineActivity::class.java).apply {
                putExtra(EXTRA_ACTION, ACTION_PICK_FOLDER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        try {
            uris.forEach { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            if (uris.isNotEmpty()) {
                onImagesCallback?.invoke(uris.map { it.toString() })
            }
        } finally {
            onImagesCallback = null
            finish()
        }
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        try {
            uris.forEach { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            if (uris.isNotEmpty()) {
                onFilesCallback?.invoke(uris.map { it.toString() })
            }
        } finally {
            onFilesCallback = null
            finish()
        }
    }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        try {
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                onFolderCallback?.invoke(uri.toString())
            }
        } finally {
            onFolderCallback = null
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_PICK_IMAGES -> {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ACTION_PICK_FILES -> {
                filePicker.launch(arrayOf("*/*"))
            }
            ACTION_PICK_FOLDER -> {
                folderPicker.launch(null)
            }
            else -> finish()
        }
    }
}
