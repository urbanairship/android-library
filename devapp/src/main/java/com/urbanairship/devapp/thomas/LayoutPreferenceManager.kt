package com.urbanairship.devapp.thomas

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class LayoutPreferenceManager(context: Context) {
    private val _recent = MutableStateFlow<List<ThomasLayout.LayoutFile>>(emptyList())
    val recentFiles: StateFlow<List<ThomasLayout.LayoutFile>> = _recent.asStateFlow()
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        _recent.update { getRecentLayouts() }
    }

    fun addToRecent(layout: ThomasLayout.LayoutFile) {
        if (_recent.value.contains(layout)) {
            return
        }

        _recent.update {
            val result = it + layout
            if (result.size > STORE_RECENT_FILES_COUNT) {
                result
                    .toMutableList()
                    .apply { removeAt(0) }
                    .toList()
            } else {
                result
            }
        }

        val toStore = _recent.value.map { it.toJsonValue().toString() }.toSet()
        sharedPreferences.edit {
            putStringSet(KEY_RECENT_LAYOUTS, toStore)
        }
    }

    fun getRecentLayouts(): List<ThomasLayout.LayoutFile> {
        val saved = sharedPreferences.getStringSet(KEY_RECENT_LAYOUTS, emptySet())
            ?: return emptyList()

        return saved
            .sorted()
            .mapNotNull { ThomasLayout.LayoutFile.from(JsonValue.parseString(it)) }
            .toList()
    }

    companion object {
        private const val KEY_RECENT_LAYOUTS = "recent_layouts"
        private const val PREFERENCES_NAME = "LayoutPreferences"
        private const val STORE_RECENT_FILES_COUNT = 5
        private var instance: LayoutPreferenceManager? = null

        fun init(context: Context) {
            if (instance == null) {
                instance = LayoutPreferenceManager(context)
            }
        }

        fun shared(): LayoutPreferenceManager {
            return instance!!
        }
    }
}
