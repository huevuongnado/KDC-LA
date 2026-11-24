package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalMediaPaths(
    val logoPath: String = "",
    val coverPath: String = "",
)

class LocalMediaPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("portal_local_media", Context.MODE_PRIVATE)
    private val _paths = MutableStateFlow(readPaths())
    val pathsFlow: Flow<LocalMediaPaths> = _paths.asStateFlow()

    private fun readPaths(): LocalMediaPaths = LocalMediaPaths(
        logoPath = prefs.getString(KEY_LOGO, "").orEmpty(),
        coverPath = prefs.getString(KEY_COVER, "").orEmpty(),
    )

    fun savePaths(logoPath: String, coverPath: String) {
        prefs.edit()
            .putString(KEY_LOGO, logoPath)
            .putString(KEY_COVER, coverPath)
            .apply()
        _paths.value = LocalMediaPaths(logoPath, coverPath)
    }

    private companion object {
        const val KEY_LOGO = "logo_path"
        const val KEY_COVER = "cover_path"
    }
}
