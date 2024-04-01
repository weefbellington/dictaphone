package com.davidstemmer.dictaphone.data

import android.net.Uri

data class AudioMetadata(val mediaId: String,
                         val uri: Uri,
                         val name: String,
                         val createdDate: Long,
                         val durationMs: Long)