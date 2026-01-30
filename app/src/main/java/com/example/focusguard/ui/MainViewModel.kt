package com.example.focusguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusguard.data.FocusRepository
import com.example.focusguard.data.NotificationEntity
import com.example.focusguard.data.SenderCategory
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
    val unknownNotifications: StateFlow<List<NotificationEntity>> = repository
        .getUnknownNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val primaryNotifications: StateFlow<List<NotificationEntity>> = repository
        .getPrimaryNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vipNotifications: StateFlow<List<NotificationEntity>> = repository
        .getVipNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val spamNotifications: StateFlow<List<NotificationEntity>> = repository
        .getSpamNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Senders (For Settings)
    val allSenders: StateFlow<List<SenderScoreEntity>> = repository
        .getAllSenders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Uncategorized Senders (For Banners)
    val uncategorizedSenders: StateFlow<List<SenderScoreEntity>> = repository
        .getUnknownSendersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Locally dismissed sender IDs (to hide banner temporarily)
    private val _dismissedSenders = MutableStateFlow<Set<String>>(emptySet())
    val dismissedSenders: StateFlow<Set<String>> = _dismissedSenders

    fun toggleFocusMode() {
        val newState = !_focusModeActive.value
        repository.setFocusModeActive(newState)
        _focusModeActive.value = newState
        updateMetrics() // Update immediately on toggle
    }

    // Focus Metrics (Session, Daily Total)
    private val _focusMetrics = MutableStateFlow(Pair(0L, 0L))
    val focusMetrics: StateFlow<Pair<Long, Long>> = _focusMetrics

    init {
        // Ticker to update time every minute
        viewModelScope.launch {
            while (true) {
                if (_focusModeActive.value) {
                    updateMetrics()
                }
                kotlinx.coroutines.delay(60000) // 1 minute delay
            }
        }
    }

    private fun updateMetrics() {
        _focusMetrics.value = repository.getFocusMetrics()
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearNotifications()
        }
    }

    // Quick Actions
    fun markAsPrimary(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.setSenderCategory(senderId, SenderCategory.PRIMARY)
        }
    }

    fun markAsSpam(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.setSenderCategory(senderId, SenderCategory.SPAM)
        }
    }
    
    fun markAsVip(notification: NotificationEntity) {
        viewModelScope.launch {
            val senderId = "${notification.packageName}:${notification.senderName}"
            repository.setSenderCategory(senderId, SenderCategory.VIP)
        }
    }

    // Categorization Logic
    fun categorizeSender(senderId: String, category: SenderCategory) {
        viewModelScope.launch {
            repository.setSenderCategory(senderId, category)
        }
    }

    fun dismissBanner(senderId: String) {
        _dismissedSenders.value = _dismissedSenders.value + senderId
    }
}
