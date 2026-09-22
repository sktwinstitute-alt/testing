package com.seriousstudy.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private val ALWAYS_ALLOWED = setOf(
            "com.android.phone",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.systemui",
            "com.seriousstudy.app"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName.isBlank()) return

        if (!FocusStateManager.isFocused(this)) return
        if (packageName in ALWAYS_ALLOWED) return
        if (packageName == this.packageName) return
        
        // Checks effective blocked apps: desktop defined + mobile selectively defined
        if (packageName !in FocusStateManager.getEffectiveBlockedApps(this)) return

        // Send interruption broadcast to WebSocketForegroundService
        val intent = Intent(WebSocketForegroundService.ACTION_SEND_INTERRUPTION).apply {
            putExtra(WebSocketForegroundService.EXTRA_INTERRUPTED_APP, packageName)
            setPackage(this@FocusAccessibilityService.packageName)
        }
        sendBroadcast(intent)

        // Push user back to home
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
        // no-op
    }
}
