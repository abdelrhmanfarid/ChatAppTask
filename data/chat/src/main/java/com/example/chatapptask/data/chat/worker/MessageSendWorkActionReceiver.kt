package com.example.chatapptask.data.chat.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chatapptask.core.domain.repository.ChatRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessageSendWorkActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = MessageSendWorkActions.messageIdFrom(intent) ?: return
        val repository = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MessageSendWorkActionEntryPoint::class.java,
        ).chatRepository()
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    MessageSendWorkActions.ACTION_RETRY -> repository.retryMessage(messageId)
                    MessageSendWorkActions.ACTION_CANCEL -> repository.cancelOutgoingSend(messageId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MessageSendWorkActionEntryPoint {
    fun chatRepository(): ChatRepository
}
