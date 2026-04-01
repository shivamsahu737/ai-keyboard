package com.aikeyboard.app.inference

import kotlinx.coroutines.*

enum class AITask(val label: String) {
    FIX_GRAMMAR("fix_grammar"),
    PROFESSIONAL("professional"),
    CASUAL("casual"),
    POLITE("polite")
}

object SimulatedInference {

    // Simulate streaming: calls onToken every ~80ms, then onComplete
    fun processText(
        input: String,
        task: AITask,
        scope: CoroutineScope,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (input.isBlank()) {
            onError("No text to process")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // Simulate model thinking delay (first token latency)
                delay(400)

                val result = generateResponse(input.trim(), task)

                // Stream word by word like a real LLM
                val words = result.split(" ")
                val streamed = StringBuilder()

                for (word in words) {
                    streamed.append(if (streamed.isEmpty()) word else " $word")
                    withContext(Dispatchers.Main) {
                        onToken(streamed.toString())
                    }
                    delay(80) // ~80ms per token — feels real
                }

                withContext(Dispatchers.Main) {
                    onComplete(result)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Processing failed: ${e.message}")
                }
            }
        }
    }

    private fun generateResponse(input: String, task: AITask): String {
        val lower = input.lowercase()

        return when (task) {
            AITask.FIX_GRAMMAR -> fixGrammar(input)
            AITask.PROFESSIONAL -> makeProfessional(input, lower)
            AITask.CASUAL -> makeCasual(input, lower)
            AITask.POLITE -> makePolite(input, lower)
        }
    }

    private fun fixGrammar(input: String): String {
        // Smart rule-based grammar fixes for demo
        var result = input
            .replace(Regex("\\bi am go\\b", RegexOption.IGNORE_CASE), "I went")
            .replace(Regex("\\bi are\\b", RegexOption.IGNORE_CASE), "I am")
            .replace(Regex("\\bhe are\\b", RegexOption.IGNORE_CASE), "he is")
            .replace(Regex("\\bshe are\\b", RegexOption.IGNORE_CASE), "she is")
            .replace(Regex("\\bthey is\\b", RegexOption.IGNORE_CASE), "they are")
            .replace(Regex("\\bdont\\b", RegexOption.IGNORE_CASE), "don't")
            .replace(Regex("\\bcant\\b", RegexOption.IGNORE_CASE), "can't")
            .replace(Regex("\\bwont\\b", RegexOption.IGNORE_CASE), "won't")
            .replace(Regex("\\bi\\b"), "I")

        // Capitalize first letter
        result = result.trimStart().replaceFirstChar { it.uppercase() }

        // Add period if missing punctuation at end
        if (result.isNotEmpty() && !result.last().let { it == '.' || it == '!' || it == '?' }) {
            result += "."
        }

        return result
    }

    private fun makeProfessional(input: String, lower: String): String {
        return when {
            lower.contains("hey") && lower.contains("chat") ->
                "Could we schedule a brief discussion at your earliest convenience?"
            lower.contains("tmrw") || lower.contains("tomorrow") ->
                "I would like to request a meeting tomorrow to discuss this matter further."
            lower.contains("asap") ->
                "I would appreciate your prompt attention to this matter."
            lower.contains("thanks") || lower.contains("thank you") ->
                "Thank you for your time and consideration."
            lower.contains("sorry") ->
                "I sincerely apologize for any inconvenience this may have caused."
            lower.contains("send") && lower.contains("file") ->
                "Could you please share the relevant documentation at your earliest convenience?"
            lower.contains("meeting") ->
                "I would like to propose scheduling a meeting to discuss this at your convenience."
            lower.length < 20 ->
                "I would like to bring this matter to your attention: ${input.trimEnd('.')}."
            else ->
                "I am writing to formally address the following: ${input.trimStart().replaceFirstChar { it.uppercase() }}"
        }
    }

    private fun makeCasual(input: String, lower: String): String {
        return when {
            lower.contains("meeting") || lower.contains("schedule") ->
                "Hey, can we catch up sometime soon?"
            lower.contains("would like to") ->
                input.replace(Regex("would like to", RegexOption.IGNORE_CASE), "wanna")
                    .replace(Regex("I am", RegexOption.IGNORE_CASE), "I'm")
            lower.contains("please") || lower.contains("kindly") ->
                "Hey! ${input.replace(Regex("please|kindly", RegexOption.IGNORE_CASE), "").trim()}"
            lower.contains("apologize") || lower.contains("sorry") ->
                "My bad! Won't happen again 😅"
            lower.contains("thank") ->
                "Thanks so much! Really appreciate it 🙌"
            lower.length < 20 ->
                "Hey, just wanted to say — $input"
            else ->
                input.replace(Regex("I am ", RegexOption.IGNORE_CASE), "I'm ")
                    .replace(Regex("do not", RegexOption.IGNORE_CASE), "don't")
                    .replace(Regex("cannot", RegexOption.IGNORE_CASE), "can't")
                    .trimStart().replaceFirstChar { it.uppercase() }
        }
    }

    private fun makePolite(input: String, lower: String): String {
        return when {
            lower.contains("send") ->
                "Could you please send this when you get a chance? Thank you!"
            lower.contains("do this") || lower.contains("do it") ->
                "Would you mind doing this when you have a moment? I really appreciate it."
            lower.contains("call") ->
                "Would it be possible to give me a call at your convenience? Thank you."
            lower.contains("help") ->
                "I would really appreciate your help with this, if you don't mind."
            lower.contains("now") ->
                input.replace(Regex("\\bnow\\b", RegexOption.IGNORE_CASE), "when you get a chance") + ", please."
            lower.startsWith("give me") ->
                "Could you please give me ${input.substring(7).trimStart()}? Thank you so much."
            else ->
                "Could you please ${input.trimStart().replaceFirstChar { it.lowercase() }.trimEnd('.')}? Thank you!"
        }
    }
}