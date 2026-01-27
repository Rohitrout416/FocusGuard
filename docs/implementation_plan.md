# Implementation Plan - FocusGuard Metadata-Only Privacy Architecture

## Goal
Build a privacy-first notification filtering system using **metadata analytics** (sender frequency, timing) to rank importance WITHOUT reading message content. Auto-suppress during Focus Mode and learn from user feedback.

> [!IMPORTANT]
> **Privacy**: Only `Sender`, `App`, `Timestamp` stored. **NO message content**.

---

## Phase 0: Build Setup

### Dependencies (`libs.versions.toml`)
| Library | Purpose |
|---------|---------|
| `room` (2.6.1) | Local database |
| `ksp` | Room annotation processor |
| `coroutines-android` | Async DB operations |

### Plugin Configuration
- Root `build.gradle.kts`: Declare `kotlin-android`, `ksp` with `apply false`
- App `build.gradle.kts`: Apply `android.application`, `kotlin.android`, `kotlin.compose`, `ksp`

---

## Phase 1: Data Layer (Room)

### [NEW] `NotificationEntity.kt`
```kotlin
@Entity
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderName: String,
    val packageName: String,
    val timestamp: Long
    // NO content field
)
```

### [NEW] `SenderScoreEntity.kt`
```kotlin
@Entity
data class SenderScoreEntity(
    @PrimaryKey val senderId: String, // "pkg:sender"
    val baseScore: Int = 0,
    val userFeedback: Int = 0, // +1 important, -1 spam
    val msgCount: Int = 0,
    val lastBurstTime: Long = 0,
    val isSpam: Boolean = false
)
```

### [NEW] `AppDatabase.kt`
- Room database with DAOs for both entities
- Singleton pattern

---

## Phase 2: Repository Layer

### [NEW] `FocusRepository.kt`
- `isFocusModeActive(): Boolean` (SharedPrefs)
- `setFocusModeActive(Boolean)`
- `saveNotification(entity)`
- `getAllNotifications(): Flow<List>`
- `updateSenderScore(senderId, feedback)`

### Ranking Formula
```
priority = baseScore + (msgCount * 0.1) + (userFeedback * 5) + burstBonus
```
Where `burstBonus = 10` if 3+ messages in 60 seconds.

---

## Phase 3: Service Layer

### [MODIFY] `NotificationListener.kt`
1. Check `repository.isFocusModeActive()`
2. If OFF → return (let notification pass)
3. If ON:
   - `cancelNotification(sbn.key)`
   - Extract metadata (title, package) — **NOT content**
   - Save to `NotificationEntity`
   - Update `SenderScoreEntity.msgCount++`

---

## Phase 4: UI Layer

### [NEW] `MainViewModel.kt`
- Exposes `focusModeState: StateFlow<Boolean>`
- Exposes `notifications: StateFlow<List<NotificationEntity>>`
- Methods: `toggleFocusMode()`, `markImportant(id)`, `markSpam(id)`

### [MODIFY] `MainActivity.kt`
- Focus Mode toggle (Switch)
- Blocked notifications list (LazyColumn)
- Swipe actions: Mark Important / Mark Spam

---

## Verification Plan

### Privacy Check
1. Send WhatsApp with "SECRET123"
2. Open Database Inspector
3. **Verify**: No "SECRET123" in any table

### Focus Mode Check
1. Toggle Focus ON
2. Send notification
3. **Verify**: Phone silent, no status bar icon
4. Open app → notification in list

### Learning Check
1. Mark sender as Important
2. **Verify**: Their messages rank higher
