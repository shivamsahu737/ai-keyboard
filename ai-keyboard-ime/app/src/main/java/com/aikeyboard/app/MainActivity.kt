package com.aikeyboard.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.aikeyboard.app.databinding.ActivityMainBinding
import com.tencent.mmkv.MMKV

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MMKV.initialize(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndUpdateStatus()

        binding.btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        binding.btnSwitchKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdateStatus()
    }

    private fun checkAndUpdateStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val isEnabled = imm.enabledInputMethodList.any {
            it.packageName == packageName
        }

        binding.statusText.text = if (isEnabled) {
            "✅ Fleksy AI Keyboard is enabled"
        } else {
            "⚠️ Fleksy AI Keyboard is not enabled yet"
        }
    }
}