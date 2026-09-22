package com.seriousstudy.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketForegroundService : Service() {

    companion object {
        const val ACTION_FOCUS_STATE_CHANGED = "com.seriousstudy.FOCUS_STATE_CHANGED"
        const val ACTION_SEND_INTERRUPTION = "com.seriousstudy.SEND_INTERRUPTION"
        const val EXTRA_INTERRUPTED_APP = "app"

        private const val CHANNEL_ID = "serious_study_focus"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "WSForegroundService"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val RECONNECT_DELAY_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, WebSocketForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebSocketForegroundService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var currentBlockedApps: Set<String> = emptySet()
    private var isPairRejected = false
    private var isRunning = false
    private lateinit var client: OkHttpClient
    private var wsConfig: WsConfig? = null

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            try {
                webSocket?.send(JSONObject().apply { put("type", "HEARTBEAT") }.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat send failed: ${e.message}")
            }
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    private val interruptionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SEND_INTERRUPTION) {
                val pkg = intent.getStringExtra(EXTRA_INTERRUPTED_APP) ?: return
                try {
                    val msg = JSONObject().apply {
                        put("type", "INTERRUPTION")
                        put("app", pkg)
                    }
                    webSocket?.send(msg.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Interruption send failed: ${e.message}")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val filter = android.content.IntentFilter(ACTION_SEND_INTERRUPTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(interruptionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(interruptionReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))

        wsConfig = FocusStateManager.getWsConfig(this)
        if (wsConfig == null) {
            Log.w(TAG, "No WsConfig found, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        currentBlockedApps = FocusStateManager.getBlockedApps(this)
        isRunning = true
        isPairRejected = false
        connectWebSocket()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        webSocket?.cancel()
        try { unregisterReceiver(interruptionReceiver) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectWebSocket() {
        val config = wsConfig ?: return
        val url = "ws://${config.ip}:${config.port}"
        Log.d(TAG, "Connecting to $url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, wsListener)
    }

    private val wsListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened")
            val config = wsConfig ?: return
            val pairMsg = JSONObject().apply {
                put("type", "PAIR")
                put("token", config.token)
                put("deviceId", Build.MODEL)
            }
            webSocket.send(pairMsg.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received: $text")
            try {
                val json = JSONObject(text)
                when (json.getString("type")) {
                    "PAIR_OK" -> {
                        val laptopName = json.optString("laptopName", wsConfig?.laptop ?: "Laptop")
                        updateNotification("Connected to $laptopName")
                        handler.post { scheduleHeartbeat() }
                    }
                    "PAIR_REJECTED" -> {
                        isPairRejected = true
                        updateNotification("Pairing rejected")
                        handler.removeCallbacks(heartbeatRunnable)
                    }
                    "FOCUS_ON" -> {
                        val subject = json.optString("subject", "")
                        handler.post {
                            FocusStateManager.setFocusOn(
                                this@WebSocketForegroundService,
                                subject,
                                currentBlockedApps
                            )
                            updateNotification("🔒 Focus: $subject")
                            broadcastFocusStateChanged()
                        }
                    }
                    "FOCUS_OFF" -> {
                        handler.post {
                            FocusStateManager.setFocusOff(this@WebSocketForegroundService)
                            updateNotification("Focus session ended")
                            broadcastFocusStateChanged()
                        }
                    }
                    "BLOCKLIST" -> {
                        val appsArray = json.optJSONArray("apps") ?: JSONArray()
                        val apps = mutableSetOf<String>()
                        for (i in 0 until appsArray.length()) {
                            apps.add(appsArray.getString(i))
                        }
                        currentBlockedApps = apps
                        handler.post {
                            FocusStateManager.setBlockedApps(this@WebSocketForegroundService, apps)
                            broadcastFocusStateChanged()
                        }
                    }
                    "HEARTBEAT_ACK" -> {
                        Log.d(TAG, "Heartbeat acknowledged")
                    }
                    else -> Log.w(TAG, "Unknown message type: ${json.optString("type")}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: ${e.message}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed")
            handleDisconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}")
            handleDisconnect()
        }
    }

    private fun handleDisconnect() {
        handler.removeCallbacks(heartbeatRunnable)
        // Do NOT call setFocusOff — keep focus locked even while disconnected
        updateNotification("Disconnected — reconnecting...")
        if (isRunning && !isPairRejected) {
            handler.postDelayed({ connectWebSocket() }, RECONNECT_DELAY_MS)
        }
    }

    private fun scheduleHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    private fun broadcastFocusStateChanged() {
        val intent = Intent(ACTION_FOCUS_STATE_CHANGED)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(content))
    }
}
