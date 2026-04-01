package com.aikeyboard.app.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
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
    private var speechRecognizer: SpeechRecognizer? = null

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
        // Number keys
        val numberKeys = mapOf(
            binding.key1 to "1", binding.key2 to "2", binding.key3 to "3",
            binding.key4 to "4", binding.key5 to "5", binding.key6 to "6",
            binding.key7 to "7", binding.key8 to "8", binding.key9 to "9",
            binding.key0 to "0"
        )
        numberKeys.forEach { (view, char) ->
            view.setOnClickListener {
                vibrate()
                currentInputConnection?.commitText(char, 1)
            }
        }

        // Letter keys with long-press symbols
        val letterKeys = mapOf(
            binding.keyQ to Pair("q", "@"), binding.keyW to Pair("w", "#"),
            binding.keyE to Pair("e", "$"), binding.keyR to Pair("r", "%"),
            binding.keyT to Pair("t", "&"), binding.keyY to Pair("y", "*"),
            binding.keyU to Pair("u", "-"), binding.keyI to Pair("i", "+"),
            binding.keyO to Pair("o", "("), binding.keyP to Pair("p", ")"),
            binding.keyA to Pair("a", "!"), binding.keyS to Pair("s", "\""),
            binding.keyD to Pair("d", "'"), binding.keyF to Pair("f", ":"),
            binding.keyG to Pair("g", ";"), binding.keyH to Pair("h", "/"),
            binding.keyJ to Pair("j", "?"), binding.keyK to Pair("k", "["),
            binding.keyL to Pair("l", "]"), binding.keyZ to Pair("z", "_"),
            binding.keyX to Pair("x", ","), binding.keyC to Pair("c", "."),
            binding.keyV to Pair("v", "<"), binding.keyB to Pair("b", ">"),
            binding.keyN to Pair("n", "{"), binding.keyM to Pair("m", "}")
        )

        letterKeys.forEach { (view, pair) ->
            val (letter, symbol) = pair
            view.setOnClickListener {
                vibrate()
                val toInsert = if (isShifted) letter.uppercase() else letter
                currentInputConnection?.commitText(toInsert, 1)
                if (isShifted) {
                    isShifted = false
                    updateShiftState()
                }
            }
            view.setOnLongClickListener {
                vibrate()
                currentInputConnection?.commitText(symbol, 1)
                true // Consume the event
            }
        }

        binding.keySpace.setOnClickListener {
            vibrate()
            currentInputConnection?.commitText(" ", 1)
        }

        // Spacebar cursor control
        binding.keySpace.setOnTouchListener { view, event ->
            val ic = currentInputConnection ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = event.x
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val initialX = view.tag as? Float ?: return@setOnTouchListener false
                    val deltaX = event.x - initialX
                    val threshold = 30f // pixels per move

                    if (Math.abs(deltaX) > threshold) {
                        if (deltaX < 0) {
                            // Move cursor left
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
                        } else {
                            // Move cursor right
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                        }
                        view.tag = event.x // Reset for next move
                    }
                    true // Consume to prevent click
                }
                else -> false
            }
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

        binding.keyMic.setOnClickListener {
            vibrate()
            startVoiceInput()
        }

        // Theme button
        binding.btnTheme.setOnClickListener {
            vibrate()
            // Cycle themes (dummy)
            // For now, just change background color
            val root = binding.root
            val currentBg = (root.background as? android.graphics.drawable.ColorDrawable)?.color ?: 0xFF1a1a2e.toInt()
            val newBg = when (currentBg) {
                0xFF1a1a2e.toInt() -> 0xFF2d1b69.toInt() // Dark purple
                0xFF2d1b69.toInt() -> 0xFF0f0f23.toInt() // Darker
                else -> 0xFF1a1a2e.toInt() // Back to default
            }
            root.setBackgroundColor(newBg)
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
        binding.chipEmoji.setOnClickListener { vibrate(); triggerAI(AITask.EMOJI) }
        binding.chipShorten.setOnClickListener { vibrate(); triggerAI(AITask.SHORTEN) }
        binding.chipExpand.setOnClickListener { vibrate(); triggerAI(AITask.EXPAND) }

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
                binding.chipPolite,
                binding.chipEmoji,
                binding.chipShorten,
                binding.chipExpand
            ).forEach { it.alpha = 0.4f }
        } else {
            listOf(
                binding.chipGrammar,
                binding.chipProfessional,
                binding.chipCasual,
                binding.chipPolite,
                binding.chipEmoji,
                binding.chipShorten,
                binding.chipExpand
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

    private fun startVoiceInput() {
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        // Show listening indicator
                        binding.keyMic.text = "🎙️"
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        binding.keyMic.text = "🎤"
                    }

                    override fun onError(error: Int) {
                        binding.keyMic.text = "🎤"
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Voice recognition error"
                        }
                        currentInputConnection?.commitText("[$errorMsg]", 1)
                    }

                    override fun onResults(results: Bundle?) {
                        binding.keyMic.text = "🎤"
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { recognizedText ->
                            currentInputConnection?.commitText(recognizedText, 1)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            currentInputConnection?.commitText("[Voice input failed: ${e.message}]", 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}