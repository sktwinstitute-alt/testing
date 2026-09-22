package com.seriousstudy.app

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.seriousstudy.app.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isConnected = false
    private var isFocused = false

    private val focusStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WebSocketForegroundService.ACTION_FOCUS_STATE_CHANGED) {
                updateUi()
            }
        }
    }

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val raw = result.data?.getStringExtra(QrScannerActivity.EXTRA_RESULT)
                ?: return@registerForActivityResult
            handleQrResult(raw)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Notification permission needed for focus status",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnScanQr.setOnClickListener { scanQr() }
        binding.btnManageBlocklist.setOnClickListener {
            startActivity(Intent(this, AppBlocklistActivity::class.java))
        }
        binding.btnAccessibility.setOnClickListener { showAccessibilityDialog() }
        binding.btnEmergencyUnlock.setOnClickListener { emergencyUnlock() }
        binding.btnExamTracker.setOnClickListener {
            startActivity(Intent(this, ExamTrackerActivity::class.java))
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        val filter = IntentFilter(WebSocketForegroundService.ACTION_FOCUS_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(focusStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(focusStateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(focusStateReceiver)
        } catch (_: Exception) {
        }
    }

    private fun scanQr() {
        qrLauncher.launch(Intent(this, QrScannerActivity::class.java))
    }

    private fun handleQrResult(raw: String) {
        try {
            val json = JSONObject(raw)
            val ip = json.getString("ip")
            val port = json.getInt("port")
            val token = json.getString("token")
            val laptop = json.optString("laptop", "Laptop")
            FocusStateManager.saveWsConfig(this, ip, port, token, laptop)
            isConnected = false  // will flip to true once PAIR_OK is received
            updateUi()
            WebSocketForegroundService.start(this)
            Toast.makeText(this, "Connecting to $laptop\u2026", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR code: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("App Blocking Permission")
            .setMessage(
                "Serious Study needs the Accessibility Service permission to detect when you " +
                        "open a blocked app during a focus session and redirect you back to the " +
                        "home screen.\n\nTap OK to open Accessibility Settings, then find " +
                        "'Serious Study' and enable it."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUi() {
        isFocused = FocusStateManager.isFocused(this)
        val wsConfig = FocusStateManager.getWsConfig(this)

        if (isFocused) {
            val subject = FocusStateManager.getSubject(this)
            binding.tvConnectionStatus.text =
                wsConfig?.let { "Connected to ${it.laptop}" } ?: "Connected"
            binding.tvFocusState.text = "\uD83D\uDD12 Focus active: $subject"
            binding.viewStatusDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.success)
                )
            binding.tvFocusSubject.text = "\uD83D\uDD12  Focus: $subject"
            binding.cardFocusActive.visibility = android.view.View.VISIBLE
            binding.btnEmergencyUnlock.visibility = android.view.View.VISIBLE
        } else {
            if (wsConfig != null) {
                binding.tvConnectionStatus.text = "Connecting to ${wsConfig.laptop}\u2026"
                binding.tvFocusState.text = "No active focus session"
                binding.viewStatusDot.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.accent)
                    )
            } else {
                binding.tvConnectionStatus.text = "Not Connected"
                binding.tvFocusState.text = "Scan QR to pair with laptop"
                binding.viewStatusDot.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.muted)
                    )
            }
            binding.cardFocusActive.visibility = android.view.View.GONE
            binding.btnEmergencyUnlock.visibility = android.view.View.GONE
        }
    }

    private fun emergencyUnlock() {
        AlertDialog.Builder(this)
            .setTitle("Emergency Unlock")
            .setMessage(
                "Are you sure you want to end the focus session? This action will be logged " +
                        "and reported to your study laptop."
            )
            .setPositiveButton("Unlock") { _, _ ->
                FocusStateManager.setFocusOff(this)
                WebSocketForegroundService.stop(this)
                updateUi()
                Toast.makeText(this, "Focus session ended", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
