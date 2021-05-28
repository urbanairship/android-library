package com.urbanairship.chat.ui

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.chat.ChatMessage
import com.urbanairship.chat.R

/**
 * RecyclerView adapter for chat messages.
 */
internal class MessageAdapter : PagedListAdapter<ChatMessage, MessageAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        private val textView: TextView = root.findViewById(R.id.message_text)
        private val attachment: ImageView = root.findViewById(R.id.message_attachment)
        private val createdOn: TextView = root.findViewById(R.id.message_created_at)

        fun bind(message: ChatMessage) {
            message.text?.let {
                textView.text = HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT)
            }
            createdOn.text = message.createdOnDisplayString(itemView.context)

            textView.visibility = if (message.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            attachment.visibility = if (message.attachmentUrl.isNullOrEmpty()) View.GONE else View.VISIBLE

            attachment.loadAttachment(message.attachmentUrl)
        }

        private fun ChatMessage.createdOnDisplayString(context: Context) =
            if (this.pending) {
                context.getString(R.string.ua_chat_message_sending)
            } else {
                DateUtils.formatDateTime(context, this.createdOn,
                        DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME)
            }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let(holder::bind)
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position)?.direction?.ordinal ?: 0
    }

    fun getChatMessage(position: Int): ChatMessage? =
        if (position > -1 && position < itemCount) {
            super.getItem(position)
        } else {
            null
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = if (viewType == 0) {
            R.layout.ua_item_chat_sent
        } else {
            R.layout.ua_item_chat_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem.messageId == newItem.messageId

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem == newItem
        }
    }
}
