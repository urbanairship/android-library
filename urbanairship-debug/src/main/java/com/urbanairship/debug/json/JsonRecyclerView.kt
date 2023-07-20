package com.urbanairship.debug.json

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.databinding.UaItemJsonViewerBinding
import com.urbanairship.debug.json.JsonRecyclerAdapter.ViewHolder
import com.urbanairship.debug.json.JsonRecyclerView.Item
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Custom `RecyclerView` for displaying JSON as a list of top-level fields, with expandable sections
 * for showing pretty-printed values.
 */
class JsonRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    private val jsonRecyclerAdapter = JsonRecyclerAdapter()

    init {
        adapter = jsonRecyclerAdapter
    }

    fun show(json: JsonMap) {
        json.map { (key, value) -> Item(key, value) }
            .let { items -> jsonRecyclerAdapter.submitList(items) }
    }

    data class Item(
        val key: String,
        val value: JsonValue,
        val isExpanded: Boolean = false,
    ) {
        fun prettyPrinted(): String = when {
            value.isJsonMap -> JSONObject(value.toString()).toString(2)
            value.isJsonList -> JSONArray(value.toString()).toString(2)
            else -> value.toString()
        }

        fun jsonType(): String = when {
            value.isJsonMap -> "object"
            value.isJsonList -> "array"
            value.isString -> "string"
            value.isNumber -> "number"
            value.isBoolean -> "boolean"
            value.isNull -> "null"
            // Shouldn't get here...
            else -> "???"
        }
    }
}

class JsonRecyclerAdapter(
    private val showDividers: Boolean = true
) : ListAdapter<Item, ViewHolder>(DIFF_UTIL) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(
        val parent: ViewGroup,
        val binding: UaItemJsonViewerBinding = UaItemJsonViewerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item): Unit = with(binding) {
            setItem(item)
            showDividers = this@JsonRecyclerAdapter.showDividers

            // Hack to disable wrapping and allow horizontal scrolling when text overflows.
            jsonValue.movementMethod = ScrollingMovementMethod()
            jsonValue.setHorizontallyScrolling(true)

            // Show/hide pretty-printed value when the header is tapped.
            header.setOnClickListener {
                val newItem = item.copy(isExpanded = !item.isExpanded)
                submitList(currentList.mapIndexed { index, existingItem ->
                    if (index == bindingAdapterPosition) newItem else existingItem
                })
            }

            executePendingBindings()
        }
    }

    companion object {
        private val DIFF_UTIL = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(old: Item, new: Item) = old.key == new.key
            override fun areContentsTheSame(old: Item, new: Item) = old == new
        }
    }
}
