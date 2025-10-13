package com.urbanairship.devapp.thomas

import android.content.Context

internal class ThomasLayoutLoader {
    private val cache = mutableMapOf<ThomasLayout.Type, List<ThomasLayout.LayoutFile>>()

    fun load(type: ThomasLayout.Type, context: Context): List<ThomasLayout.LayoutFile> {
        val cached = cache[type]
        if (cached != null) {
            return cached
        }

        val directory = "$LAYOUTS_ROOT_FOLDER/${type.directory}"
        val result = context.assets.list(directory)?.map { fileName ->
            ThomasLayout.LayoutFile(
                assetsPath = "$directory/$fileName",
                type = type,
                filename = fileName
            )
        }
            ?: emptyList()

        cache[type] = result
        return result
    }

    companion object {
        const val LAYOUTS_ROOT_FOLDER = "sample_layouts"

        val shared: ThomasLayoutLoader = ThomasLayoutLoader()
    }
}
