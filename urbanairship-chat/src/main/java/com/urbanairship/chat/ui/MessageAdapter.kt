package com.urbanairship.chat.ui

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.chat.ChatMessage
import com.urbanairship.chat.R
import com.urbanairship.chat.databinding.UaItemChatReceivedBinding
import com.urbanairship.chat.databinding.UaItemChatSentBinding

/**
 * RecyclerView adapter for chat messages.
 */
internal class MessageAdapter : PagedListAdapter<ChatMessage, MessageAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(
        root: View,
        val binder: (message: ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(root)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { message ->
            holder.binder(message)
        }
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
        val context = parent.context
        return if (viewType == 0) {
            val binding = UaItemChatSentBinding.inflate(LayoutInflater.from(context), parent, false)
            ViewHolder(binding.root) {
                binding.message = it
                binding.createdOn = it.createdOnDisplayString(context)
                binding.messageAttachment.loadAttachment(it.attachmentUrl)
                binding.executePendingBindings()
            }
        } else {
            val binding = UaItemChatReceivedBinding.inflate(LayoutInflater.from(context), parent, false)
            ViewHolder(binding.root) {
                binding.message = it
                binding.createdOn = it.createdOnDisplayString(context)
                binding.messageAttachment.loadAttachment(it.attachmentUrl)
                binding.executePendingBindings()
            }
        }
    }

    private fun ChatMessage.createdOnDisplayString(context: Context) =
            if (this.pending) {
                context.getString(R.string.ua_chat_message_sending)
            } else {
                DateUtils.formatDateTime(context, this.createdOn,
                        DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME)
            }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem.messageId == newItem.messageId

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                // IDs and pending field can change, so we only consider what gets displayed here
                // in order to avoid unnecessary list updates.
                oldItem.direction == newItem.direction && oldItem.text == newItem.text
        }
    }
}
