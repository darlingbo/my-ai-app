package com.myai.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Works with NO internet — simple built-in replies so the AI is never fully dead. */
object OfflineBrain {
    fun reply(input: String): String {
        val t = input.lowercase().trim()
        if (t.contains("time")) return "It's ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())} (offline)."
        if (t.contains("date") || t.contains("today")) return "Today is ${SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())}."
        if (Regex(".*\\b(hi|hello|hey|yo)\\b.*").matches(t)) return "Hey! 👋 I'm in offline mode right now — connect to the internet for full answers."
        if (t.contains("how are you")) return "I'm good! But I'm offline right now, so I can only do basic things until you reconnect. 😊"
        if (t.contains("thank")) return "Anytime! 🤗"
        // tiny calculator
        val m = Regex("(\\d+)\\s*(plus|\\+|minus|-|times|x|\\*|divided by|/)\\s*(\\d+)").find(t)
        if (m != null) {
            val a = m.groupValues[1].toDouble(); val b = m.groupValues[3].toDouble(); val op = m.groupValues[2]
            val r = when {
                op.contains("plus") || op == "+" -> a + b
                op.contains("minus") || op == "-" -> a - b
                op.contains("times") || op == "x" || op == "*" -> a * b
                else -> if (b != 0.0) a / b else return "Can't divide by zero!"
            }
            return "That's ${if (r == r.toLong().toDouble()) r.toLong().toString() else r}. 🔢"
        }
        return "📴 I'm offline right now, so I can't give a full answer. Reconnect to the internet and I'll be back to my smart self!"
    }
}
