package com.seriousstudy.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.seriousstudy.app.databinding.ActivityQrScannerBinding

class QrScannerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESULT = "qr_result"
    }

    private lateinit var binding: ActivityQrScannerBinding
    private var scanning = true

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScanning()
        } else {
            Toast.makeText(this, "Camera permission is required to scan the QR code.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val barcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            val text = result?.text ?: return
            if (!scanning) return
            scanning = false
            val intent = Intent().apply { putExtra(EXTRA_RESULT, text) }
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startScanning()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanning() {
        binding.barcodeScannerView.decodeContinuous(barcodeCallback)
    }

    override fun onResume() {
        super.onResume()
        scanning = true
        binding.barcodeScannerView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeScannerView.pause()
    }
}
