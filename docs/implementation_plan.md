
# FocusGuard Implementation Plan (As Built)

This document reflects the authoritative state of the FocusGuard application. It documents the implemented behavior, architecture, and design decisions.

## 1. Core Focus Mode Behavior
- **Lifecycle**: Focus Mode is toggled via a Switch on the main screen.
    - **ON**: Blocks notifications from "Unknown" and "Spam" senders. Tracks session duration. Starts `FocusStatusService` (Foreground Service).
    - **OFF**: Allows standard notification flow. Displays a "Quiet Session Summary". Stops the service.
- **Persistent Status Notification**:
    - A persistent "Focus Mode Active" notification while enabled.
    - Channel: `IMPORTANCE_LOW` (silent, no sound/vibration).
    - Uses `FOREGROUND_SERVICE_IMMEDIATE` to ensure immediate visibility.
    - Service: `FocusStatusService` with `foregroundServiceType="specialUse"`.
- **Blocking Logic**:
    - **Primary**: Always allowed (non-disturbing).
    - **VIP**: Always allowed (interrupts Focus Mode).
    - **Unknown/Spam**: Blocked / Silenced during Focus Mode.
- **Immediate Updates**: Toggling Focus Mode or changing sender categories updates the UI logic instantly.
- **Permission Handling**: 
    - Checks `NotificationListenerService` permission on `ON_RESUME`.
    - Requests `POST_NOTIFICATIONS` permission on Android 13+ for foreground service notification.
    - Restarts service via `onRequestPermissionsResult` when permission is granted.

## 2. Message Classification System
Senders are classified into one of four states (`SenderCategory`):

1.  **UNKNOWN** (Default):
    - New senders land here.
    - Shown in the "Unknown" tab.
    - **Behavior**: Silenced during Focus Mode.
    - **Auto-Promotion**: If a sender sends highly frequent messages (>= 3), a "Classification Banner" appears suggesting action.

2.  **PRIMARY**:
    - For important but non-urgent contacts.
    - **Behavior**: Allowed through Focus Mode filters.

3.  **VIP**:
    - For urgent/critical contacts.
    - **Behavior**: Always allowed. Bypasses filters.
    - **UI**: Marked with a Star icon.

4.  **SPAM**:
    - For unwanted automated messages.
    - **Behavior**: Hidden/Silenced.

**Actions**:
- **Inline Actions**: Each notification card has "Primary", "VIP", and "Spam" AssistChips.
- **Immediate Feedback**: Clicking an action immediately moves the item and shows a Snackbar with an **UNDO** action.

## 3. UI Structure
- **Architecture**: Single Activity (`MainActivity`) with Composable UI.
- **Navigation**: 4-Tab Interface (`Unknown`, `Primary`, `VIP`, `Spam`).
- **Main Components**:
    - **TopAppBar**: Branded "FocusGuard" title.
    - **Focus Switch**: Large toggle card with haptic feedback.
    - **Focus Timer**: Visible only when Focus Mode is ON.
    - **Notification List**: `LazyColumn` of `NotificationItem` cards.
    - **Clear All**: Functionality to clear message list (does not delete sender rules).

## 4. Focus Timer System
- **Determinate Progress**: A premium Circular Progress Indicator that fills clockwise.
- **Cycle**: Represents progress towards the next **2-hour milestone** (0% -> 100% over 2 hours).
- **Animation**: Smooth `animateFloatAsState` transitions; calm and non-distracting (no infinite spinning).
- **Display**: Shows "IN FOCUS" badge and current duration (e.g., "1:15").

## 5. Focus Milestone System
- **Purpose**: Gentle encouragement for long sessions.
- **Mechanism**:
    - **WorkManager**: Schedules a background check (`FocusMilestoneWorker`) every **2 hours**.
    - **Notification**: "🎉 Great Focus Session. You've stayed focused for X hours."
- **User Control**:
    - **Inline Opt-Out**: Notification includes a "Disable Focus Milestones" action button.
    - **Logic**: Uses `MilestoneActionReceiver` to update preferences and cancel the worker without opening the app.
- **Default**: Enabled by default.

## 6. Session Summary (Quiet Closure)
- **Trigger**: When Focus Mode is toggled **OFF**.
- **UI**: A subtle, dismissible card appears below the toggle.
- **Content**: "Focus session complete. Focused for [X]h [Y]m today."
- **Constraint**: No system notification, sound, or vibration. Purely visual closure.

## 7. Haptic Feedback
- **Triggers**:
    - **Start Focus**: `LongPress` haptic feedback.
    - **End Focus**: `LongPress` haptic feedback.
- **Constraint**: No haptics for notifications or other actions to maintain calmness.
- **Implementation**: Uses `LocalHapticFeedback.current.performHapticFeedback()`.

## 8. Visual & Branding
- **Theme**: Unified Indigo/Violet palette (`Color.kt`). Dynamic Colors disabled for consistency.
- **Typography**:
    - **Font**: `FontFamily.SansSerif` (Clean, System).
    - **Hierarchy**: Defined in `Type.kt` (Display, Headline, Body, Label).
    - **Styling**: Optimized letter spacing and line height for readability.
- **App Title**:
    - Styled as a logotype: **Bold**, **Primary Color**, **Letter-spacing 1.sp**.

## 9. Architecture & Technical Decisions
- **Data Layer**:
    - `AppDatabase`: Room Database (v5).
    - `SenderScoreEntity`: Tracks `senderId`, `category`, and `msgCount`.
    - `FocusRepository`: Central source of truth for Focus State, Metrics, and Classification.
- **Services**:
    - `FocusStatusService`: Foreground Service for persistent notification. Uses `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on Android 14+.
    - `NotificationListener`: System `NotificationListenerService` for intercepting notifications.
- **Background Work**: `WorkManager` for reliable milestone scheduling.
- **State Management**: `MainViewModel` uses `StateFlow` for reactive UI updates.
- **Event Handling**: `BroadcastReceiver` for notification action handling.
- **Permissions**:
    - `BIND_NOTIFICATION_LISTENER_SERVICE`: For notification interception.
    - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`: For persistent notification.
    - `POST_NOTIFICATIONS`: Runtime permission for Android 13+ notifications.

## 10. Verification & Audit Notes
- **Verified**:
    - Focus Timer Animation (Smooth, determinate).
    - Haptic Feedback on Physical Device (Toggle).
    - Background Milestone Scheduling (via WorkManager Inspector).
    - Notification Action (Disable Milestones).
    - Permission Auto-Refresh Flow.
    - **Persistent Focus Notification** (Foreground Service, immediate visibility on first toggle, Android 13/14+ compatible).
- **Assumptions**:
    - Standard Android "Touch Vibration" settings are enabled on the user device.
    - Manufacturer battery savers do not aggressively kill the WorkManager job.
