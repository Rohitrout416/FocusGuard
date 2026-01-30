package com.example.focusguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusguard.data.FocusRepository
import com.example.focusguard.data.NotificationEntity
import com.example.focusguard.data.SenderScoreEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FocusRepository(application)

    // Focus mode state
    private val _focusModeActive = MutableStateFlow(repository.isFocusModeActive())
    val focusModeActive: StateFlow<Boolean> = _focusModeActive

    // Notifications (Filtered)
    val priorityNotifications: StateFlow<List<NotificationEntity>> = repository
        .getPrimaryNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val spamNotifications: StateFlow<List<NotificationEntity>> = repository
        .getSpamNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Senders (For Settings)
    val allSenders: StateFlow<List<SenderScoreEntity>> = repository
        .getAllSenders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFocusMode() {
        val newState = !_focusModeActive.value
        repository.setFocusModeActive(newState)
        _focusModeActive.value = newState
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearNotifications()
        }
    }

    // Quick Actions from Main Screen
    fun markAsPrimary(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.setSenderPrimary(senderId, true)
        }
    }

    fun markAsSpam(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.setSenderPrimary(senderId, false)
        }
    }

    // Settings Toggle Actions
    fun updateSenderConfig(senderId: String, isPrimary: Boolean, isVip: Boolean) {
        viewModelScope.launch {
            repository.setSenderPrimary(senderId, isPrimary)
            repository.setSenderVip(senderId, isVip)
        }
    }
}
