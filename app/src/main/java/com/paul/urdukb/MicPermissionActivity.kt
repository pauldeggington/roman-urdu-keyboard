package com.paul.urdukb

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** A see-through screen whose only job is to show Android's microphone permission question. */
class MicPermissionActivity : Activity() {
    companion object { var onResult: ((Boolean) -> Unit)? = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { finish(); onResult?.invoke(true); return }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        finish()
        onResult?.invoke(ok)
    }
}
