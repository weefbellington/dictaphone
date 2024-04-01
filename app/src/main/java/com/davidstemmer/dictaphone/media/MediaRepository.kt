package com.davidstemmer.dictaphone.media

import android.annotation.TargetApi
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import com.davidstemmer.dictaphone.data.AudioMetadata
import java.util.concurrent.TimeUnit

class MediaRepository(private val context: Context) {

    private val contentResolver by lazy { context.contentResolver }

    private val useLegacyMediaStore = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    private val supportsIsRecordingColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val externalStorageCollection =
        if (useLegacyMediaStore) Media.EXTERNAL_CONTENT_URI
        else Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    // Projection (FROM)
    private val mediaProjectionBase = arrayOf(
        Media._ID,
        Media.OWNER_PACKAGE_NAME,
        Media.DISPLAY_NAME,
        Media.DATE_ADDED,
        Media.DURATION,
    )

    private val mediaProjection =
        if (supportsIsRecordingColumn) mediaProjectionBase + Media.IS_RECORDING
        else mediaProjectionBase

    // Selection (WHERE)
    @TargetApi(Build.VERSION_CODES.S)
    private val mediaSelectionByIsRecording = "${Media.IS_RECORDING} = 1"


    // Sort order (ORDER BY)
    private val mediaSortOrder = "${Media.DATE_ADDED} DESC"

    private fun mediaSelectionByPackageName(context: Context): String =
        "${Media.OWNER_PACKAGE_NAME} = '${context.packageName}'"

    private fun mediaSelection(context: Context) =
        if (supportsIsRecordingColumn) mediaSelectionByIsRecording
        else mediaSelectionByPackageName(context)

    /**
     * Initialize an audio recording and place it in the MediaStore.
     * It will be placed in the Music/Recordings directory with a the provided file name.
     *
     * Only Android S (v.31) and above supports the IS_RECORDING column. On earlier versions, we
     * set the 'artist' field to the application package name and use that as an identifier.
     *
     * @param fileName the display name for the file
     * @return Uri of the newly created file
     */
    fun initializeAudioRecording(fileName: String): Uri? {
        val audioCollection = Media.EXTERNAL_CONTENT_URI
        val recordingDetails = getRecordingDetails(displayName = fileName)
        return with(contentResolver) {
            insert(audioCollection, recordingDetails)
        }
    }

    /**
     * Query the MediaStore and return a list of recorded audio metadata, sorted by creation date.
     *
     * On Android S (v.31) and above, this will consist of all media in external storage with the
     * IS_RECORDING column set to 1. On earlier versions, it will consist of files whose 'artist'
     * field corresponds to the application package name.
     *
     * @return the metadata list
     */
    fun fetchRecordingMetadata(): List<AudioMetadata> {

        val query = contentResolver.query(
            externalStorageCollection,
            mediaProjection,
            mediaSelection(context),
            null,
            mediaSortOrder
        )

        return query?.use { cursor -> cursor.getMetadataList() } ?: emptyList()
    }

    fun updateName(uri: Uri, newName: String) {
        contentResolver.run {
            val newValues = getRecordingDetails(newName)
            update(uri, newValues, null, null)
        }
    }

    private data class Columns(val idIndex: Int,
                               val nameIndex: Int,
                               val artistIndex: Int,
                               val createdIndex: Int,
                               val durationIndex: Int)

    private fun Cursor.extractColumns() = run {
        Columns(
            idIndex = getColumnIndexOrThrow(Media._ID),
            nameIndex = getColumnIndexOrThrow(Media.DISPLAY_NAME),
            artistIndex = getColumnIndexOrThrow(Media.OWNER_PACKAGE_NAME),
            createdIndex = getColumnIndexOrThrow(Media.DATE_ADDED),
            durationIndex = getColumnIndexOrThrow(Media.DURATION)
        )
    }

    private fun Cursor.getMetadataList(): List<AudioMetadata> = run {
        val output = mutableListOf<AudioMetadata>()
        val columns = extractColumns()
        while (moveToNext()) {

            val id = getLong(columns.idIndex)
            val name = getString(columns.nameIndex)
            val created = getLong(columns.createdIndex)
            val durationMs = getLong(columns.durationIndex)

            val contentUri: Uri = ContentUris.withAppendedId(
                Media.EXTERNAL_CONTENT_URI,
                id
            )

            output += AudioMetadata(id.toString(), contentUri, name, created, durationMs)
        }
        return output.toList() // make immutable
    }

    private fun getRecordingDetails(displayName: String): ContentValues {
        val dateAdded = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        return ContentValues().apply {
            put(Media.DISPLAY_NAME, displayName)
            put(Media.MIME_TYPE, "audio/3gpp")
            put(Media.RELATIVE_PATH, "Music/Recordings/")
            put(Media.DATE_ADDED, dateAdded)
            if (supportsIsRecordingColumn) {
                put(Media.IS_RECORDING, true)
            }
        }
    }
}