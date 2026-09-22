package com.seriousstudy.app

import android.content.Context

data class WsConfig(val ip: String, val port: Int, val token: String, val laptop: String)

object FocusStateManager {

    private const val PREF_NAME = "focus_state"
    private const val KEY_IS_FOCUSED = "is_focused"
    private const val KEY_SUBJECT = "subject"
    private const val KEY_BLOCKED_APPS = "blocked_apps" // synced from desktop
    private const val KEY_LOCAL_BLOCKED_APPS = "local_blocked_apps" // selective apps chosen on mobile
    private const val KEY_WS_IP = "ws_ip"
    private const val KEY_WS_PORT = "ws_port"
    private const val KEY_WS_TOKEN = "ws_token"
    private const val KEY_WS_LAPTOP = "ws_laptop"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isFocused(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_FOCUSED, false)

    fun getSubject(context: Context): String =
        prefs(context).getString(KEY_SUBJECT, "") ?: ""

    fun getBlockedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()

    fun getLocalBlockedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LOCAL_BLOCKED_APPS, emptySet()) ?: emptySet()

    fun setLocalBlockedApps(context: Context, apps: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_LOCAL_BLOCKED_APPS, apps)
            .apply()
    }

    /**
     * Effective list of apps to block: union of desktop-synced blocked apps and mobile-selected blocked apps.
     */
    fun getEffectiveBlockedApps(context: Context): Set<String> {
        val desktopApps = getBlockedApps(context)
        val localApps = getLocalBlockedApps(context)
        return desktopApps + localApps
    }

    fun setFocusOn(context: Context, subject: String, blockedApps: Set<String>) {
        prefs(context).edit()
            .putBoolean(KEY_IS_FOCUSED, true)
            .putString(KEY_SUBJECT, subject)
            .putStringSet(KEY_BLOCKED_APPS, blockedApps)
            .apply()
    }

    fun setFocusOff(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_IS_FOCUSED, false)
            .putString(KEY_SUBJECT, "")
            .apply()
    }

    fun setBlockedApps(context: Context, apps: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_BLOCKED_APPS, apps)
            .apply()
    }

    fun saveWsConfig(context: Context, ip: String, port: Int, token: String, laptop: String) {
        prefs(context).edit()
            .putString(KEY_WS_IP, ip)
            .putInt(KEY_WS_PORT, port)
            .putString(KEY_WS_TOKEN, token)
            .putString(KEY_WS_LAPTOP, laptop)
            .apply()
    }

    fun getWsConfig(context: Context): WsConfig? {
        val p = prefs(context)
        val ip = p.getString(KEY_WS_IP, null) ?: return null
        val port = p.getInt(KEY_WS_PORT, 0)
        val token = p.getString(KEY_WS_TOKEN, null) ?: return null
        val laptop = p.getString(KEY_WS_LAPTOP, "") ?: ""
        if (port == 0) return null
        return WsConfig(ip, port, token, laptop)
    }
}
