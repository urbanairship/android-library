package com.urbanairship.android.layout.property

internal enum class StoryIndicatorSource(private val value: String) {
    PAGER("pager"),
    CURRENT_PAGE("current_page");

    override fun toString(): String = value

    companion object {
        @Throws(IllegalArgumentException::class)
        fun from(value: String): StoryIndicatorSource {
            for (type in values()) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw IllegalArgumentException("Unknown StoryIndicatorSource value: $value")
        }
    }
}
