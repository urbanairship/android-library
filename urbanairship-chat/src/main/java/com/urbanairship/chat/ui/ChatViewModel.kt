package com.urbanairship.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.urbanairship.chat.Chat
import com.urbanairship.chat.ChatMessage

internal class ChatViewModel @JvmOverloads constructor(
    application: Application,
    messageDraft: String? = null,
    private val chat: Chat = Chat.shared()
) : AndroidViewModel(application) {

    class ChatViewModelFactory(
        private val application: Application,
        private val messageDraft: String?
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, messageDraft) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.canonicalName}")
        }
    }

    val messages = LivePagedListBuilder(chat.conversation.messageDataSourceFactory, 50)
            .setBoundaryCallback(object : PagedList.BoundaryCallback<ChatMessage>() {
                override fun onZeroItemsLoaded() = listViewVisibility.postValue(false)

                override fun onItemAtFrontLoaded(itemAtFront: ChatMessage) = listViewVisibility.postValue(true)
            }).build()

    val listViewVisibility = MutableLiveData(true)
    val emptyViewVisibility = Transformations.map(listViewVisibility) { it.not() }

    val text = MutableLiveData<String>(messageDraft)
    val hasText = Transformations.map(text) { !it.isNullOrEmpty() }

    private val image = MutableLiveData<String>()
    val hasImage = Transformations.map(image) { it != null }

    val sendButtonEnabled: LiveData<Boolean> =
        MediatorLiveData<Boolean>().apply {
            addSource(hasText) { this.value = it || hasImage.value == true }
            addSource(hasImage) { this.value = it || hasText.value == true }
        }

    private val attachmentLoaded = MutableLiveData<Boolean>()
    val isAttachmentLoaded: LiveData<Boolean> = attachmentLoaded

    fun onAttachmentThumbnailLoaded() {
        attachmentLoaded.value = true
    }

    fun send() {
        val text = this.text.value
        val image = this.image.value

        if (!text.isNullOrBlank() || image != null) {
            chat.conversation.sendMessage(text, image)
            this.text.value = null
            clearImage()
        }
    }

    fun setImage(imageUri: String) {
        clearImage()
        image.value = imageUri
    }

    fun clearImage() {
        attachmentLoaded.value = false
        image.value = null
    }
}
