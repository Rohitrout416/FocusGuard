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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

    // Session Summary (Quiet Feedback)
    private val _sessionSummary = MutableStateFlow<String?>(null)
    val sessionSummary: StateFlow<String?> = _sessionSummary

    fun toggleFocusMode() {
        val newState = !_focusModeActive.value
        repository.setFocusModeActive(newState)
        _focusModeActive.value = newState
        
        updateMetrics() // Ensure repository state is current
        
        if (!newState) {
            // Just finished a session
            val metrics = repository.getFocusMetrics()
            val dailyMs = metrics.second
            
            val hours = dailyMs / 3600000
            val minutes = (dailyMs % 3600000) / 60000
            
            val timeString = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            _sessionSummary.value = "Focused for $timeString today"
        } else {
             _sessionSummary.value = null // Clear when starting new
        }
    }
    
    fun dismissSessionSummary() {
        _sessionSummary.value = null
    }

    // Focus Metrics (Session, Daily Total)
    private val _focusMetrics = MutableStateFlow(Pair(0L, 0L))
    val focusMetrics: StateFlow<Pair<Long, Long>> = _focusMetrics

    init {
        // Reactive ticker: only runs when Focus Mode is active
        viewModelScope.launch {
            _focusModeActive
                .flatMapLatest { isActive ->
                    if (isActive) {
                        // Emit immediately, then every 60 seconds
                        flow {
                            while (true) {
                                emit(Unit)
                                kotlinx.coroutines.delay(60000)
                            }
                        }
                    } else {
                        emptyFlow()
                    }
                }
                .collect { updateMetrics() }
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
