package com.example.photogrouper

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContentUris
import android.provider.MediaStore

object PhotoRepo {
    private const val ROOT = "Pictures/PhotoGroups"

    /** يقرأ كل الصور (ما عدا اللي اتعملت بالتطبيق نفسه). */
    fun load(ctx: Context): List<Photo> {
        val list = ArrayList<Photo>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE ?"
        val args = arrayOf("$ROOT%")
        ctx.contentResolver.query(
            uri, projection, selection, args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                list += Photo(
                    id,
                    ContentUris.withAppendedId(uri, id),
                    c.getString(iName) ?: "img_$id.jpg",
                    c.getString(iMime) ?: "image/jpeg"
                )
            }
        }
        return list
    }

    /** ينسخ الصورة جوه Pictures/PhotoGroups/<folder> من غير ما يمس الأصل. */
    fun copyTo(resolver: ContentResolver, photo: Photo, folder: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, photo.name)
            put(MediaStore.Images.Media.MIME_TYPE, photo.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "$ROOT/$folder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val out = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("insert failed")
        resolver.openOutputStream(out)!!.use { o ->
            resolver.openInputStream(photo.uri)!!.use { it.copyTo(o) }
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(out, values, null, null)
    }
}
