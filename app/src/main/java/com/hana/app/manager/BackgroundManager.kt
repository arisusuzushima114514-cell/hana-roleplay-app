package com.hana.app.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

sealed class BackgroundTarget {
    data object Global : BackgroundTarget()
    data object MainChat : BackgroundTarget()
    data class Character(val characterId: String) : BackgroundTarget()
}

data class SavedBackgroundInfo(
    val name: String,
    val filePath: String,
    val previewUri: String,
    val updatedAt: Long
)

class BackgroundManager(
    private val context: Context
) {
    private val backgroundDir = File(context.filesDir, "backgrounds").apply { mkdirs() }
    private val libraryDir = File(backgroundDir, "library").apply { mkdirs() }

    suspend fun saveBackground(uri: Uri, target: BackgroundTarget): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeBitmapScaled(uri) ?: return@runCatching false
            val resized = resizeBitmap(bitmap)
            FileOutputStream(fileFor(target)).use { output ->
                resized.compress(Bitmap.CompressFormat.JPEG, 80, output)
            }
            FileOutputStream(newLibraryFile()).use { output ->
                resized.compress(Bitmap.CompressFormat.JPEG, 80, output)
            }
            true
        }.getOrDefault(false)
    }

    suspend fun loadBackground(target: BackgroundTarget): Bitmap? = withContext(Dispatchers.IO) {
        when (target) {
            is BackgroundTarget.Character -> {
                decodeFromFile(fileFor(target)) ?: decodeFromFile(fileFor(BackgroundTarget.Global))
            }
            BackgroundTarget.MainChat -> {
                decodeFromFile(fileFor(target)) ?: decodeFromFile(fileFor(BackgroundTarget.Global))
            }
            BackgroundTarget.Global -> decodeFromFile(fileFor(target))
        }
    }

    fun isCustomBackgroundEnabled(target: BackgroundTarget): Boolean = fileFor(target).exists()

    fun backgroundFileSizeBytes(): Long = backgroundDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun appFilesSizeBytes(): Long = context.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearBackground(target: BackgroundTarget) {
        fileFor(target).takeIf { it.exists() }?.delete()
    }

    fun clearAllBackgrounds() {
        backgroundDir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
        libraryDir.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
    }

    fun listSavedBackgrounds(): List<SavedBackgroundInfo> {
        return libraryDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                SavedBackgroundInfo(
                    name = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    previewUri = Uri.fromFile(file).toString(),
                    updatedAt = file.lastModified()
                )
            }
            .orEmpty()
    }

    fun applySavedBackground(filePath: String, target: BackgroundTarget): Boolean {
        return runCatching {
            val source = File(filePath)
            if (!source.exists()) return false
            source.inputStream().use { input ->
                FileOutputStream(fileFor(target)).use { output -> input.copyTo(output) }
            }
            true
        }.getOrDefault(false)
    }

    fun deleteSavedBackground(filePath: String): Boolean {
        return runCatching {
            val source = File(filePath)
            source.exists() && source.delete()
        }.getOrDefault(false)
    }

    fun renameSavedBackground(filePath: String, newName: String): Boolean {
        return runCatching {
            val source = File(filePath)
            if (!source.exists()) return false
            val safe = newName.trim().ifBlank { return false }
                .replace(Regex("""[\\/:*?\"<>|]"""), "_")
            val target = File(source.parentFile, "$safe.jpg")
            if (target.exists()) return false
            source.renameTo(target)
        }.getOrDefault(false)
    }

    fun currentBackgroundPath(target: BackgroundTarget): String? {
        val currentFile = fileFor(target).takeIf { it.exists() } ?: return null
        val matchedLibraryFile = libraryDir.listFiles()
            ?.firstOrNull { it.isFile && hasSameContent(it, currentFile) }
        return (matchedLibraryFile ?: currentFile).absolutePath
    }

    private fun decodeBitmapScaled(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val maxEdge = max(info.size.width, info.size.height)
                if (maxEdge > 1920) {
                    val scale = 1920f / maxEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                val maxEdge = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                val sampleSize = generateSequence(1) { it * 2 }
                    .first { maxEdge / it <= 1920 }
                context.contentResolver.openInputStream(uri)?.use { decodeInput ->
                    BitmapFactory.decodeStream(
                        decodeInput,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    )
                }
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val maxEdge = max(bitmap.width, bitmap.height)
        if (maxEdge <= 1920) return bitmap

        val scale = 1920f / maxEdge
        val targetWidth = (bitmap.width * scale).toInt()
        val targetHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun fileFor(target: BackgroundTarget): File {
        val name = when (target) {
            BackgroundTarget.Global -> "global_background.jpg"
            BackgroundTarget.MainChat -> "main_chat_background.jpg"
            is BackgroundTarget.Character -> "character_${target.characterId}_background.jpg"
        }
        return File(backgroundDir, name)
    }

    private fun newLibraryFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(libraryDir, "bg_$stamp.jpg")
    }

    private fun hasSameContent(first: File, second: File): Boolean {
        if (!first.exists() || !second.exists() || first.length() != second.length()) return false
        return try {
            first.readBytes().contentEquals(second.readBytes())
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeFromFile(file: File): Bitmap? {
        return try {
            if (!file.exists()) return null
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
