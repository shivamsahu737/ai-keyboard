package com.aikeyboard.app.ime

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.aikeyboard.app.cache.ResultCache
import com.aikeyboard.app.databinding.KeyboardViewBinding
import com.aikeyboard.app.inference.AITask
import com.aikeyboard.app.inference.SimulatedInference
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AIKeyboardService : InputMethodService() {

    private lateinit var binding: KeyboardViewBinding
    private val cache = ResultCache()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isShifted = false
    private var lastAcceptedText: String? = null
    private var currentInputBeforeAI: String = ""

    // Bar state machine
    private enum class BarState { DEFAULT, CHIPS, LOADING, RESULT }
    private var barState = BarState.DEFAULT

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
    }

    override fun onCreateInputView(): View {
        return try {
            binding = KeyboardViewBinding.inflate(layoutInflater)
            setupKeys()
            setupAIBar()
            binding.root
        } catch (e: Exception) {
            // Fallback: create a simple error view
            val errorView = TextView(this).apply {
                text = "Keyboard Error: ${e.message}"
                setBackgroundColor(Color.RED)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            errorView
        }
    }

    // ── KEY SETUP ────────────────────────────────────────────────────────────

    private fun setupKeys() {
        val letterKeys = mapOf(
            binding.keyQ to "q", binding.keyW to "w", binding.keyE to "e",
            binding.keyR to "r", binding.keyT to "t", binding.keyY to "y",
            binding.keyU to "u", binding.keyI to "i", binding.keyO to "o",
            binding.keyP to "p", binding.keyA to "a", binding.keyS to "s",
            binding.keyD to "d", binding.keyF to "f", binding.keyG to "g",
            binding.keyH to "h", binding.keyJ to "j", binding.keyK to "k",
            binding.keyL to "l", binding.keyZ to "z", binding.keyX to "x",
            binding.keyC to "c", binding.keyV to "v", binding.keyB to "b",
            binding.keyN to "n", binding.keyM to "m"
        )

        letterKeys.forEach { (view, char) ->
            view.setOnClickListener {
                vibrate()
                val toInsert = if (isShifted) char.uppercase() else char
                currentInputConnection?.commitText(toInsert, 1)
                if (isShifted) {
                    isShifted = false
                    updateShiftState()
                }
            }
        }

        binding.keySpace.setOnClickListener {
            vibrate()
            currentInputConnection?.commitText(" ", 1)
        }

        binding.keyBackspace.setOnClickListener {
            vibrate()
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        binding.keyBackspace.setOnLongClickListener {
            // Delete whole word on long press
            val ic = currentInputConnection ?: return@setOnLongClickListener true
            val text = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
            val toDelete = text.trimEnd().let {
                if (it.isEmpty()) 1
                else it.length - it.trimEnd { c -> c != ' ' }.length + 1
            }
            ic.deleteSurroundingText(toDelete.coerceAtLeast(1), 0)
            true
        }

        binding.keyEnter.setOnClickListener {
            vibrate()
            currentInputConnection?.performEditorAction(
                currentInputEditorInfo?.imeOptions
                    ?.and(android.view.inputmethod.EditorInfo.IME_MASK_ACTION)
                    ?: android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            )
        }

        binding.keyShift.setOnClickListener {
            vibrate()
            isShifted = !isShifted
            updateShiftState()
        }

        binding.keySymbols.setOnClickListener {
            // TODO Phase 2: switch to symbols layout
            vibrate()
        }
    }

    private fun updateShiftState() {
        binding.keyShift.setTextColor(
            if (isShifted)
                resources.getColor(android.R.color.white, null)
            else
                0xFFa1a1aa.toInt()
        )
    }

    // ── AI BAR SETUP ─────────────────────────────────────────────────────────

    private fun setupAIBar() {
        // ★ icon → enter chip mode
        binding.btnAiStar.setOnClickListener {
            vibrate()
            setBarState(BarState.CHIPS)
        }

        // Exit chips → back to default
        binding.btnExitChips.setOnClickListener {
            vibrate()
            setBarState(BarState.DEFAULT)
        }

        // Chips
        binding.chipGrammar.setOnClickListener { vibrate(); triggerAI(AITask.FIX_GRAMMAR) }
        binding.chipProfessional.setOnClickListener { vibrate(); triggerAI(AITask.PROFESSIONAL) }
        binding.chipCasual.setOnClickListener { vibrate(); triggerAI(AITask.CASUAL) }
        binding.chipPolite.setOnClickListener { vibrate(); triggerAI(AITask.POLITE) }

        // Result bar buttons
        binding.btnAccept.setOnClickListener {
            vibrate()
            acceptResult()
        }

        binding.btnReject.setOnClickListener {
            vibrate()
            setBarState(BarState.CHIPS)
        }

        binding.btnResultBack.setOnClickListener {
            vibrate()
            setBarState(BarState.CHIPS)
        }
    }

    private fun triggerAI(task: AITask) {
        val ic = currentInputConnection ?: return

        // Get full text in the field
        val textBefore = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
        val textAfter = ic.getTextAfterCursor(500, 0)?.toString() ?: ""
        val fullText = "$textBefore$textAfter".trim()

        if (fullText.isBlank()) {
            // Nothing to process
            setBarState(BarState.DEFAULT)
            return
        }

        currentInputBeforeAI = fullText
        setBarState(BarState.LOADING)

        // Check cache first
        val cached = cache.get(task.label, fullText)
        if (cached != null) {
            binding.resultText.text = cached
            setBarState(BarState.RESULT)
            return
        }

        // Run simulated inference with streaming
        SimulatedInference.processText(
            input = fullText,
            task = task,
            scope = scope,
            onToken = { partial ->
                binding.resultText.text = partial
                if (barState == BarState.LOADING) setBarState(BarState.RESULT)
            },
            onComplete = { result ->
                binding.resultText.text = result
                cache.put(task.label, fullText, result)
                setBarState(BarState.RESULT)
            },
            onError = {
                setBarState(BarState.CHIPS)
            }
        )
    }

    private fun acceptResult() {
        val result = binding.resultText.text?.toString() ?: return
        val ic = currentInputConnection ?: return

        // Better: delete current text and insert new
        val before = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(1000, 0)?.toString() ?: ""

        ic.deleteSurroundingText(before.length, after.length)
        ic.commitText(result, 1)

        lastAcceptedText = currentInputBeforeAI

        setBarState(BarState.DEFAULT)
    }

    // ── BAR STATE MACHINE ─────────────────────────────────────────────────────

    private fun setBarState(state: BarState) {
        barState = state
        binding.barDefault.isVisible = state == BarState.DEFAULT
        binding.barChips.isVisible = state == BarState.CHIPS || state == BarState.LOADING
        binding.barResult.isVisible = state == BarState.RESULT

        // Loading shimmer on chips
        if (state == BarState.LOADING) {
            listOf(
                binding.chipGrammar,
                binding.chipProfessional,
                binding.chipCasual,
                binding.chipPolite
            ).forEach { it.alpha = 0.4f }
        } else {
            listOf(
                binding.chipGrammar,
                binding.chipProfessional,
                binding.chipCasual,
                binding.chipPolite
            ).forEach { it.alpha = 1.0f }
        }
    }

    // ── HAPTIC ───────────────────────────────────────────────────────────────

    private fun vibrate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                val v = vibratorManager?.defaultVibrator
                v?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(30)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}