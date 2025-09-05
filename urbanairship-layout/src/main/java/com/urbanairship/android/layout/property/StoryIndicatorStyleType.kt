package com.urbanairship.android.layout.property

internal enum class StoryIndicatorStyleType(private val value: String) {
    LINEAR_PROGRESS("linear_progress");

    override fun toString(): String = value

    companion object {
        @Throws(IllegalArgumentException::class)
        fun from(value: String): StoryIndicatorStyleType {
            for (type in entries) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw IllegalArgumentException("Unknown StoryIndicatorStyleType value: $value")
        }
    }
}
