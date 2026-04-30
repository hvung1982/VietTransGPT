package com.example.doc2chatgpt

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareHelper {

    const val SHARED_IMAGE_DIR = "shared_images"

    data class CachedImage(
        val file: File,
        val mimeType: String,
    )

    fun copyUriToCache(context: Context, uri: Uri): CachedImage {
        val appContext = context.applicationContext
        val mimeType = appContext.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: "image/*"
        val extension = extensionForMimeType(mimeType)
        val dir = sharedImageDir(appContext)
        val file = File(dir, "picked_${System.currentTimeMillis()}.$extension")

        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Không đọc được ảnh" }
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }

        require(file.length() > 0L) { "Ảnh rỗng hoặc không đọc được" }
        trimSharedImageCache(appContext)
        return CachedImage(file = file, mimeType = mimeType)
    }

    fun shareImageWithPrompt(
        context: Context,
        imageFile: File,
        prompt: String,
        mimeType: String = "image/png",
    ) {
        require(imageFile.exists() && (imageFile.length() > 0L)) { "Ảnh chia sẻ không tồn tại" }

        val imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, prompt)
            clipData = ClipData.newUri(context.contentResolver, "Doc2ChatGPT image", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, "Chia sẻ sang ChatGPT")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            throw IllegalStateException("Không tìm thấy ứng dụng để chia sẻ ảnh", e)
        }
    }

    fun clearSharedCache(context: Context) {
        sharedImageDir(context.applicationContext)
            .listFiles()
            ?.forEach { file -> file.delete() }
    }

    private fun sharedImageDir(context: Context): File {
        return File(context.cacheDir, SHARED_IMAGE_DIR).apply {
            if (!exists()) {
                check(mkdirs()) { "Không tạo được thư mục cache ảnh" }
            }
        }
    }

    private fun extensionForMimeType(mimeType: String): String {
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: "img"
    }

    private fun trimSharedImageCache(context: Context) {
        val files = sharedImageDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.toList()
            .orEmpty()

        var totalBytes = 0L
        files.forEachIndexed { index, file ->
            totalBytes += file.length()
            if ((index >= MAX_CACHE_FILES) || (totalBytes > MAX_CACHE_BYTES)) {
                file.delete()
            }
        }
    }

    private const val MAX_CACHE_FILES = 60
    private const val MAX_CACHE_BYTES = 200L * 1024L * 1024L
}
