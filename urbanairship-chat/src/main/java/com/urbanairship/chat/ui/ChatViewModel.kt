package com.urbanairship.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.paging.toLiveData
import com.urbanairship.chat.AirshipChat

internal class ChatViewModel @JvmOverloads constructor(
    application: Application,
    val chat: AirshipChat = AirshipChat.shared()
) : AndroidViewModel(application) {

    val messages = chat.conversation.messageDataSourceFactory
            .toLiveData(pageSize = 50)

    val text = MutableLiveData<String>()
    val image = MutableLiveData<String>()

    fun send() {
        val text = this.text.value
        val image = this.image.value

        if (text != null || image != null) {
            chat.conversation.sendMessage(text, image)
            this.text.value = null
            this.image.value = null
        }
    }

    fun clearImage() {
        image.value = null
    }
}
