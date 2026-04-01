package com.aikeyboard.app.inference

import kotlinx.coroutines.*

enum class AITask(val label: String) {
    FIX_GRAMMAR("fix_grammar"),
    PROFESSIONAL("professional"),
    CASUAL("casual"),
    POLITE("polite"),
    EMOJI("emoji"),
    SHORTEN("shorten"),
    EXPAND("expand")
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
            AITask.EMOJI -> addEmojis(input, lower)
            AITask.SHORTEN -> shortenText(input, lower)
            AITask.EXPAND -> expandText(input, lower)
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
            lower.contains("help") ->
                "I would be grateful if you could assist me with this matter."
            lower.contains("please") ->
                "I kindly request your assistance in this regard."
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
            lower.contains("hey") && lower.contains("chat") ->
                "Yo, wanna chat later?"
            lower.contains("tmrw") || lower.contains("tomorrow") ->
                "Catch you tomorrow, cool?"
            lower.contains("asap") ->
                "Need this ASAP, bro!"
            lower.contains("send") && lower.contains("file") ->
                "Shoot me that file when you can!"
            lower.contains("help") ->
                "Can you help me out with this?"
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
            lower.contains("hey") && lower.contains("chat") ->
                "Hello, might we have a brief conversation at your convenience?"
            lower.contains("tmrw") || lower.contains("tomorrow") ->
                "Would tomorrow work for you to discuss this?"
            lower.contains("asap") ->
                "I would be most grateful if you could attend to this as soon as possible."
            lower.contains("thanks") || lower.contains("thank you") ->
                "Thank you very much for your kindness."
            lower.contains("sorry") ->
                "I do apologize for any inconvenience caused."
            lower.contains("meeting") ->
                "Might we arrange a meeting to discuss this matter?"
            lower.contains("please") ->
                "I kindly ask for your assistance with this."
            else ->
                "Could you please ${input.trimStart().replaceFirstChar { it.lowercase() }.trimEnd('.')}? Thank you!"
        }
    }

    private fun addEmojis(input: String, lower: String): String {
        return when {
            lower.contains("happy") || lower.contains("good") ->
                "$input 😊"
            lower.contains("sad") || lower.contains("bad") ->
                "$input 😢"
            lower.contains("love") ->
                "$input ❤️"
            lower.contains("food") || lower.contains("eat") ->
                "$input 🍽️"
            lower.contains("work") ->
                "$input 💼"
            lower.contains("party") || lower.contains("fun") ->
                "$input 🎉"
            lower.contains("thanks") || lower.contains("thank you") ->
                "$input 🙏"
            lower.contains("sorry") ->
                "$input 😔"
            lower.contains("meeting") ->
                "$input 📅"
            lower.contains("call") ->
                "$input 📞"
            else ->
                "$input ✨"
        }
    }

    private fun shortenText(input: String, lower: String): String {
        return when {
            lower.contains("would like to") ->
                input.replace(Regex("would like to", RegexOption.IGNORE_CASE), "want to")
            lower.contains("i am going to") ->
                input.replace(Regex("i am going to", RegexOption.IGNORE_CASE), "i'll")
            lower.contains("because") ->
                input.replace(Regex("because", RegexOption.IGNORE_CASE), "'cause")
            lower.contains("cannot") ->
                input.replace(Regex("cannot", RegexOption.IGNORE_CASE), "can't")
            lower.contains("do not") ->
                input.replace(Regex("do not", RegexOption.IGNORE_CASE), "don't")
            lower.length > 50 ->
                input.take(40) + "..."
            else ->
                input.replace(Regex("\\s+", RegexOption.IGNORE_CASE), " ").trim()
        }
    }

    private fun expandText(input: String, lower: String): String {
        return when {
            lower.contains("brb") ->
                "Be right back"
            lower.contains("lol") ->
                "Laughing out loud"
            lower.contains("omg") ->
                "Oh my goodness"
            lower.contains("idk") ->
                "I don't know"
            lower.contains("btw") ->
                "By the way"
            lower.contains("thx") ->
                "Thank you"
            lower.contains("np") ->
                "No problem"
            lower.contains("i'll") ->
                input.replace(Regex("i'll", RegexOption.IGNORE_CASE), "I will")
            lower.contains("can't") ->
                input.replace(Regex("can't", RegexOption.IGNORE_CASE), "cannot")
            lower.contains("don't") ->
                input.replace(Regex("don't", RegexOption.IGNORE_CASE), "do not")
            else ->
                input.replace(Regex("\\s+", RegexOption.IGNORE_CASE), " ").trim()
        }
    }
}