package me.magnum.melonds.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import me.magnum.melonds.domain.model.Background
import java.io.File
import java.io.FileOutputStream
import java.util.*

class BackgroundThumbnailProvider(private val context: Context) {
    companion object {
        private const val THUMBNAIL_SIZE = 256
        private const val THUMBNAIL_CACHE_DIR = "background_thumbnails"
    }

    private val memoryThumbnailCache = mutableMapOf<UUID, Bitmap>()

    fun getBackgroundThumbnail(background: Background): Bitmap? {
        return loadThumbnailFromMemory(background)
    }

    private fun loadThumbnailFromMemory(background: Background): Bitmap? {
        val id = background.id ?: return null
        return memoryThumbnailCache.getOrElse(id) {
            val thumbnail = loadThumbnailFromDisk(background)
            if (thumbnail != null) {
                memoryThumbnailCache[id] = thumbnail
            }

            thumbnail
        }
    }

    private fun loadThumbnailFromDisk(background: Background): Bitmap? {
        val thumbnailFile = getThumbnailFile(background)
        if (thumbnailFile?.isFile == true) {
            return BitmapFactory.decodeFile(thumbnailFile.absolutePath)
        }

        val thumbnail = generateBackgroundThumbnail(background)
        if (thumbnail != null) {
            saveThumbnailToDisk(background, thumbnail)
        }

        return thumbnail
    }

    private fun saveThumbnailToDisk(background: Background, thumbnail: Bitmap) {
        try {
            val thumbnailFile = getThumbnailFile(background) ?: return
            FileOutputStream(thumbnailFile).use {
                thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (_: Exception) {
            // Ignore errors
        }
    }

    private fun getThumbnailFile(background: Background): File? {
        val cacheDir = context.externalCacheDir?.let { File(it, THUMBNAIL_CACHE_DIR) } ?: return null
        return if (cacheDir.isDirectory || cacheDir.mkdirs()) {
            File(cacheDir, background.id.toString())
        } else {
            null
        }
    }

    private fun generateBackgroundThumbnail(background: Background): Bitmap? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        try {
            context.contentResolver.openInputStream(background.uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        if (options.outWidth == -1 || options.outHeight == -1) {
            return null
        }

        // Find thumbnail dimensions limiting the largest side to THUMBNAIL_SIZE
        val (thumbnailWidth: Int, thumbnailHeight: Int) = if (options.outWidth > options.outHeight) {
            (THUMBNAIL_SIZE to (THUMBNAIL_SIZE * (options.outHeight / options.outWidth.toFloat())).toInt())
        } else {
            ((THUMBNAIL_SIZE * (options.outWidth / options.outHeight.toFloat())).toInt() to THUMBNAIL_SIZE)
        }

        val sampleSize = calculateThumbnailSampleSize(options, thumbnailWidth, thumbnailHeight)
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize

        return try {
            context.contentResolver.openInputStream(background.uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateThumbnailSampleSize(options: BitmapFactory.Options, targetWidth: Int, targetHeight: Int): Int {
        val width = options.outWidth
        val height = options.outHeight
        var inSampleSize = 1

        if (height > targetHeight || width > targetWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= targetHeight && halfWidth / inSampleSize >= targetWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}