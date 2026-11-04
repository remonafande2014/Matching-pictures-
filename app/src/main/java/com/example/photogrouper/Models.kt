package com.example.photogrouper

import android.net.Uri

data class Photo(val id: Long, val uri: Uri, val name: String, val mime: String)

data class Group(val folder: String, val photos: List<Photo>)

data class UiState(
    val busy: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val threshold: Float = 0.6f,
    val groups: List<Group> = emptyList(),
    val message: String = ""
)
