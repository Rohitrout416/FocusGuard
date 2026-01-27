package com.example.focusguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusguard.data.FocusRepository
import com.example.focusguard.data.NotificationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen.
 * Manages focus mode state and notification list with priority/spam separation.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FocusRepository(application)

    // Focus mode state
    private val _focusModeActive = MutableStateFlow(repository.isFocusModeActive())
    val focusModeActive: StateFlow<Boolean> = _focusModeActive

    // Track which senders are marked as spam (refresh trigger)
    private val _spamSenders = MutableStateFlow<Set<String>>(emptySet())

    // All notifications from DB
    private val allNotifications = repository
        .getAllNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Priority notifications (not marked as spam)
    val priorityNotifications: StateFlow<List<NotificationEntity>> = 
        combine(allNotifications, _spamSenders) { notifs, spamSet ->
            notifs.filter { notif ->
                val senderId = "${notif.packageName}:${notif.senderName}"
                !spamSet.contains(senderId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Spam notifications
    val spamNotifications: StateFlow<List<NotificationEntity>> = 
        combine(allNotifications, _spamSenders) { notifs, spamSet ->
            notifs.filter { notif ->
                val senderId = "${notif.packageName}:${notif.senderName}"
                spamSet.contains(senderId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load initial spam senders
        viewModelScope.launch {
            loadSpamSenders()
        }
    }

    private suspend fun loadSpamSenders() {
        val notifs = allNotifications.value
        val spamSet = mutableSetOf<String>()
        for (notif in notifs) {
            val senderId = "${notif.packageName}:${notif.senderName}"
            if (repository.isSpam(senderId)) {
                spamSet.add(senderId)
            }
        }
        _spamSenders.value = spamSet
    }

    fun toggleFocusMode() {
        val newState = !_focusModeActive.value
        repository.setFocusModeActive(newState)
        _focusModeActive.value = newState
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearNotifications()
            _spamSenders.value = emptySet()
        }
    }

    fun markImportant(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.markAsImportant(senderId)
            // Remove from spam set if present
            _spamSenders.value = _spamSenders.value - senderId
        }
    }

    fun markSpam(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.markAsSpam(senderId)
            // Add to spam set
            _spamSenders.value = _spamSenders.value + senderId
        }
    }
}
