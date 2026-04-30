package com.example.doc2chatgpt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

class PdfPageRenderer(context: Context) {

    private val appContext = context.applicationContext

    fun getPageCount(pdfUri: Uri): Int {
        return openPdfRenderer(pdfUri) { renderer ->
            renderer.pageCount
        }
    }

    fun renderPageToPng(
        pdfUri: Uri,
        pageIndex: Int,
        scale: Float = DEFAULT_SCALE,
        cachePrefix: String = "pdf"
    ): File {
        return openPdfRenderer(pdfUri) { renderer ->
            require(pageIndex in (0 until renderer.pageCount)) {
                "Trang ${pageIndex + 1} không tồn tại"
            }

            val outFile = File(sharedImageDir(), "${cachePrefix}_${cacheKey(pdfUri)}_page_${pageIndex + 1}.png")
            if (outFile.exists() && outFile.length() > 0L) {
                outFile.setLastModified(System.currentTimeMillis())
                return@openPdfRenderer outFile
            }

            val page = renderer.openPage(pageIndex)
            var bitmap: Bitmap? = null
            try {
                val safeScale = calculateSafeScale(page.width, page.height, scale)
                val width = (page.width * safeScale).toInt().coerceAtLeast(1)
                val height = (page.height * safeScale).toInt().coerceAtLeast(1)

                bitmap = try {
                    createBitmap(width, height, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    throw IllegalStateException(
                        "Trang PDF quá lớn để render an toàn. Hãy thử chia nhỏ tài liệu hoặc dùng trang có kích thước nhỏ hơn.",
                        oom
                    )
                }

                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                FileOutputStream(outFile).use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        "Không ghi được ảnh trang ${pageIndex + 1}"
                    }
                }
                trimSharedImageCache()
                outFile
            } finally {
                bitmap?.recycle()
                page.close()
            }
        }
    }

    private fun <T> openPdfRenderer(pdfUri: Uri, block: (PdfRenderer) -> T): T {
        val pfd: ParcelFileDescriptor = appContext.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: error("Không mở được PDF")

        pfd.use {
            PdfRenderer(it).use { renderer ->
                return block(renderer)
            }
        }
    }

    private fun calculateSafeScale(pageWidth: Int, pageHeight: Int, requestedScale: Float): Float {
        val requested = requestedScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val dimensionScale = minOf(
            requested,
            MAX_BITMAP_DIMENSION.toFloat() / pageWidth.coerceAtLeast(1),
            MAX_BITMAP_DIMENSION.toFloat() / pageHeight.coerceAtLeast(1)
        )
        val requestedPixels = pageWidth.toDouble() * pageHeight.toDouble() * dimensionScale * dimensionScale
        if (requestedPixels <= MAX_BITMAP_PIXELS) return dimensionScale.coerceAtLeast(MIN_SCALE)

        val pixelScale = sqrt(MAX_BITMAP_PIXELS.toDouble() / (pageWidth.toDouble() * pageHeight.toDouble()))
        return minOf(dimensionScale, pixelScale.toFloat()).coerceAtLeast(MIN_SCALE)
    }

    private fun sharedImageDir(): File {
        return File(appContext.cacheDir, ShareHelper.SHARED_IMAGE_DIR).apply {
            if (!exists()) {
                check(mkdirs()) { "Không tạo được thư mục cache ảnh" }
            }
        }
    }

    private fun trimSharedImageCache() {
        val files = sharedImageDir()
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.toList()
            .orEmpty()

        var totalBytes = 0L
        files.forEachIndexed { index, file ->
            totalBytes += file.length()
            if (index >= MAX_CACHE_FILES || totalBytes > MAX_CACHE_BYTES) {
                file.delete()
            }
        }
    }

    private fun cacheKey(uri: Uri): String {
        return uri.toString().hashCode().toUInt().toString(16)
    }

    private companion object {
        private const val DEFAULT_SCALE = 2.0f
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 2.0f
        private const val MAX_BITMAP_DIMENSION = 2_500
        private const val MAX_BITMAP_PIXELS = 8_000_000L
        private const val MAX_CACHE_FILES = 60
        private const val MAX_CACHE_BYTES = 200L * 1024L * 1024L
    }
}
