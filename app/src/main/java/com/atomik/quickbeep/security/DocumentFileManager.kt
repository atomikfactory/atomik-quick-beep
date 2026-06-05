package com.atomik.quickbeep.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

object DocumentFileManager {

    private const val PROVIDER_AUTHORITY = "com.atomik.quickbeep.fileprovider"

    /**
     * Creates a temporary file in the cache directory and returns a File Provider URI.
     * Retains the File reference so we can easily copy or delete it.
     */
    fun createTempImageFile(context: Context): Pair<File, Uri> {
        val tempFile = File.createTempFile("temp_doc_", ".jpg", context.cacheDir).apply {
            deleteOnExit()
        }
        val uri = FileProvider.getUriForFile(context, PROVIDER_AUTHORITY, tempFile)
        return Pair(tempFile, uri)
    }

    /**
     * Copies a temporary file from cache into the secure internal files directory,
     * encrypts its content using CryptographyManager, and cleans up the temp file.
     * Returns the absolute path to the saved encrypted file.
     */
    fun saveImageToSecureStorage(context: Context, tempFile: File, isFront: Boolean): String? {
        return try {
            if (!tempFile.exists()) return null
            
            val docDir = File(context.filesDir, "documents").apply {
                if (!exists()) mkdirs()
            }
            
            val suffix = if (isFront) "front" else "back"
            val targetFile = File(docDir, "doc_${System.currentTimeMillis()}_$suffix.jpg")
            
            // Read temp bytes and encrypt
            val bytes = tempFile.readBytes()
            val encryptedBytes = CryptographyManager.encryptData(bytes)
            targetFile.writeBytes(encryptedBytes)
            
            // Cleanup temp file
            if (tempFile.exists()) {
                tempFile.delete()
            }
            
            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reads contents from a dynamic content provider Uri, encrypts them using CryptographyManager,
     * and saves them securely into internal storage. Necessary for ML Kit scanner Uri results.
     */
    fun saveUriToSecureStorage(context: Context, uri: Uri, isFront: Boolean): String? {
        return try {
            val docDir = File(context.filesDir, "documents").apply {
                if (!exists()) mkdirs()
            }
            
            val suffix = if (isFront) "front" else "back"
            val targetFile = File(docDir, "doc_${System.currentTimeMillis()}_$suffix.jpg")
            
            // Read stream and encrypt
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val encryptedBytes = CryptographyManager.encryptData(bytes)
            targetFile.writeBytes(encryptedBytes)
            
            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves the entire PDF file securely (encrypted) and generates an encrypted JPEG thumbnail of the first page.
     * Returns a Pair of (pdfFilePath, thumbnailFilePath) or null on failure.
     */
    fun savePdfFileSecurely(context: Context, pdfUri: Uri): Pair<String, String>? {
        return try {
            val docDir = File(context.filesDir, "documents").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val pdfFile = File(docDir, "doc_$timestamp.pdf")
            val thumbFile = File(docDir, "doc_${timestamp}_thumb.jpg")

            // 1. Copy and encrypt the PDF file
            val pdfBytes = context.contentResolver.openInputStream(pdfUri)?.use { it.readBytes() } ?: return null
            val encryptedPdfBytes = CryptographyManager.encryptData(pdfBytes)
            pdfFile.writeBytes(encryptedPdfBytes)

            // 2. Render first page for thumbnail
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { fileDescriptor ->
                val renderer = PdfRenderer(fileDescriptor)
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val scale = 1.5f
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // Composite on top of an opaque white bitmap to preserve white background
                        val whiteBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(whiteBitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        canvas.drawBitmap(bitmap, 0f, 0f, null)

                        val stream = ByteArrayOutputStream()
                        whiteBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        val thumbBytes = stream.toByteArray()
                        val encryptedThumbBytes = CryptographyManager.encryptData(thumbBytes)
                        thumbFile.writeBytes(encryptedThumbBytes)
                        bitmap.recycle()
                        whiteBitmap.recycle()
                    }
                }
                renderer.close()
            }

            Pair(pdfFile.absolutePath, thumbFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Renders a selected PDF document's first two pages, encrypts them using CryptographyManager,
     * and saves them to high-resolution JPEGs inside the private secure documents directory.
     */
    fun renderPdfToImages(context: Context, pdfUri: Uri): List<String> {
        val savedPaths = mutableListOf<String>()
        try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { fileDescriptor ->
                val renderer = PdfRenderer(fileDescriptor)
                val pageCount = renderer.pageCount.coerceAtMost(2) // limit to 2 pages (recto/verso)
                
                val docDir = File(context.filesDir, "documents").apply {
                    if (!exists()) mkdirs()
                }
                
                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        // Render page at double scale for clean readability
                        val scale = 2.0f
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()
                        
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // Composite on top of an opaque white bitmap to preserve white background
                        val whiteBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(whiteBitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                        
                        val suffix = if (i == 0) "front" else "back"
                        val targetFile = File(docDir, "doc_${System.currentTimeMillis()}_$suffix.jpg")
                        
                        // Compress to stream, encrypt, and write
                        val stream = ByteArrayOutputStream()
                        whiteBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        val bytes = stream.toByteArray()
                        val encryptedBytes = CryptographyManager.encryptData(bytes)
                        targetFile.writeBytes(encryptedBytes)
                        
                        savedPaths.add(targetFile.absolutePath)
                        bitmap.recycle()
                        whiteBitmap.recycle()
                    }
                }
                renderer.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return savedPaths
    }

    /**
     * Deletes a secure file at the specified absolute path.
     */
    fun deleteFile(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
