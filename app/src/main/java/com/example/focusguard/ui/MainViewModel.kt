package com.example.focusguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusguard.data.FocusRepository
import com.example.focusguard.data.NotificationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen.
 * 
 * PRIORITY LOGIC (from Repository):
 * - Priority tab: Senders with userFeedback >= 0 AND not marked spam
 * - Spam tab: Senders explicitly marked as spam
 * - VIP: Senders with userFeedback >= 3 → notifications pass through Focus Mode
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FocusRepository(application)

    // Focus mode state
    private val _focusModeActive = MutableStateFlow(repository.isFocusModeActive())
    val focusModeActive: StateFlow<Boolean> = _focusModeActive

    // Priority notifications (from repository, DB-level filtering)
    val priorityNotifications: StateFlow<List<NotificationEntity>> = repository
        .getPriorityNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Spam notifications (from repository, DB-level filtering)
    val spamNotifications: StateFlow<List<NotificationEntity>> = repository
        .getSpamNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // No init needed - duplicates are prevented at insert time

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

    fun markImportant(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.markAsImportant(senderId)
        }
    }

    fun markSpam(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.markAsSpam(senderId)
        }
    }
}
