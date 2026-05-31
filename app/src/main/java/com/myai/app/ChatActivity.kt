package com.myai.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URL
import java.util.Locale

class ChatActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var muted = false
    private var callMode = false
    private var recognizer: android.speech.SpeechRecognizer? = null
    private var bizLocked = false
    private var mode = "general"
    private var platform = "daily"
    private var isCreator = false
    private val userId by lazy { prefs.getString("user_id", "guest") ?: "guest" }

    // image picker for photo analysis
    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyzePhoto(uri)
    }
    private var pendingPhotoQ = "What's in this photo? Describe it and read any text."

    // document picker for "Teach My AI"
    private val pickDoc = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) teachFromDoc(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        prefs = getSharedPreferences("myai", Context.MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        try { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext) } catch (_: Exception) {}
        Ads.init(this)

        messages = findViewById(R.id.messages)
        scroll = findViewById(R.id.scroll)
        input = findViewById(R.id.input)
        platform = prefs.getString("platform", "daily") ?: "daily"
        isCreator = prefs.getBoolean("is_creator", false)

        findViewById<Button>(R.id.studentBtn).setOnClickListener { switchPlatform("student") }
        findViewById<Button>(R.id.businessBtn).setOnClickListener { switchPlatform("business") }
        findViewById<Button>(R.id.dailyBtn).setOnClickListener { switchPlatform("daily") }

        // Creator sees the platform switcher; normal users are locked to their choice
        findViewById<LinearLayout>(R.id.switcher).visibility = if (isCreator) View.VISIBLE else View.GONE

        findViewById<Button>(R.id.sendBtn).setOnClickListener { doSend() }
        input.setOnEditorActionListener { _, _, _ -> doSend(); true }
        findViewById<Button>(R.id.micBtn).setOnClickListener { listen() }
        findViewById<Button>(R.id.muteBtn).setOnClickListener {
            muted = !muted
            (it as Button).text = if (muted) "🔇" else "🔊"
            if (muted) tts?.stop()
        }
        findViewById<Button>(R.id.bubbleBtn).setOnClickListener { toggleBubble() }
        findViewById<Button>(R.id.bubbleBtn).text = if (prefs.getBoolean("bubble_on", false)) "💬 Off" else "💬 Float"
        findViewById<Button>(R.id.callBtn).setOnClickListener { toggleCall() }
        findViewById<Button>(R.id.photoBtn).setOnClickListener { pickImage.launch("image/*") }
        findViewById<TextView>(R.id.title).setOnLongClickListener { logoutDialog(); true }

        applyPlatform(greet = false)   // set up toolkit/title, no greeting yet
        loadHistoryThenGreet()
        Thread { Api.wake() }.start()
        ensureMicPermission()
        scheduleDailyCheckin()
    }

    // F: the AI reaches out to you every day
    private fun scheduleDailyCheckin() {
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val msg = when (platform) {
                "student" -> "🎓 Time to learn! Open me to study, quiz yourself, or keep your 🔥 streak alive!"
                "business" -> "💼 Good day! Open me to plan, message customers, or grow your business."
                else -> "👋 Your AI is here! Tap me for a tip, to plan your day, or just to chat."
            }
            val i = Intent(this, ReminderReceiver::class.java).putExtra("task", msg)
            val pi = PendingIntent.getBroadcast(this, 999, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 9); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            am.setInexactRepeating(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                android.app.AlarmManager.INTERVAL_DAY, pi)
        } catch (_: Exception) {}
    }

    // #1 Show previous conversation when the app opens
    private fun loadHistoryThenGreet() {
        val loading = addAi("⏳ Loading your conversation…")
        Thread {
            val past = if (isOnline()) Api.history(userId) else emptyList()
            runOnUiThread {
                messages.removeView(loading)
                if (past.isNotEmpty()) {
                    past.takeLast(40).forEach { (role, content) -> bubble(content, role == "user") }
                    addAi("— end of earlier chat — I remember everything above. 👋")
                } else {
                    greetForPlatform()
                }
                checkBusinessTrial()
            }
        }.start()
    }

    // Business 7-day trial gate
    private fun checkBusinessTrial() {
        bizLocked = false
        if (platform != "business" || isCreator || !isOnline()) return
        Thread {
            val (allowed, daysLeft, locked) = Api.accessStatus(userId)
            runOnUiThread {
                bizLocked = locked
                if (locked) {
                    addAi("🔒 Your 7-day free Business trial has ended. Subscribe to keep using Business AI.")
                    subscribeDialog()
                } else if (daysLeft in 1..3) {
                    addAi("⏳ $daysLeft day${if (daysLeft==1) "" else "s"} left in your free Business trial.")
                }
            }
        }.start()
    }

    private fun subscribeDialog() {
        AlertDialog.Builder(this)
            .setTitle("⭐ Subscribe to Business AI — GH₵20/month")
            .setMessage("Unlock unlimited Business AI: invoices, marketing, customer replies, pricing & growth.\n\n1) Tap 'Pay now' (MoMo/card)\n2) After paying, come back and tap 'I've paid ✅'")
            .setPositiveButton("Pay now") { _, _ -> startPayment() }
            .setNeutralButton("I've paid ✅") { _, _ -> verifyPayment() }
            .setNegativeButton("Later", null).show()
    }

    private fun startPayment() {
        val email = prefs.getString("email", "") ?: ""
        toast("Opening secure checkout…")
        Thread {
            val (ok, url, reference) = Api.payStart(userId, email)
            runOnUiThread {
                if (ok && url.isNotEmpty()) {
                    prefs.edit().putString("pay_ref", reference).apply()
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                } else {
                    toast("Payments not ready yet. (Server needs the Paystack key.)")
                }
            }
        }.start()
    }

    private fun verifyPayment() {
        val ref = prefs.getString("pay_ref", "") ?: ""
        if (ref.isEmpty()) { toast("Tap 'Pay now' first."); return }
        toast("Checking your payment…")
        Thread {
            val ok = Api.payVerify(ref, userId)
            runOnUiThread {
                if (ok) {
                    bizLocked = false
                    addAi("🎉 Payment confirmed — Business AI unlocked! Thank you!")
                } else {
                    toast("Payment not confirmed yet. If you just paid, wait a moment and try again.")
                }
            }
        }.start()
    }

    private fun switchToFree() {
        platform = "daily"; prefs.edit().putString("platform", "daily").apply(); bizLocked = false
        applyPlatform(greet = true)
    }

    private fun greetForPlatform() {
        addAi(when (platform) {
            "student" -> "🎓✨ Hey study buddy! Learning here is FUN — earn ⭐XP, level up, build 🔥streaks! Play brain games, solve riddles, or tap any tool. Let's gooo! 🚀"
            "business" -> "💼 Welcome to your Business AI. Plans, invoices, customer replies, marketing, pricing — tap a tool or tell me what you need."
            else -> "🌟 Hi! I'm your everyday AI helper. Ask me anything, tap a tool, or send a 📷 photo. How can I help?"
        })
    }

    private fun isOnline(): Boolean = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val n = cm.activeNetwork; val c = cm.getNetworkCapabilities(n)
        c?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) { false }

    // #2 Photo analysis (costs 1 image credit)
    private fun analyzePhoto(uri: android.net.Uri) {
        if (!isOnline()) { addAi("📴 I need internet to look at photos. Reconnect and try again."); return }
        withImageCredit { doAnalyzePhoto(uri) }
    }

    private fun doAnalyzePhoto(uri: android.net.Uri) {
        addUser("📷 (photo)")
        val thinking = addAi("👁️ Looking at your photo…")
        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val reply = Api.vision(userId, b64, pendingPhotoQ)
                runOnUiThread { (thinking.tag as? TextView)?.text = reply; speak(reply); scrollDown() }
            } catch (e: Exception) {
                runOnUiThread { (thinking.tag as? TextView)?.text = "Couldn't open that image." }
            }
        }.start()
    }

    /** Backend brain mode: daily maps to the general brain. */
    private fun backendMode(): String = if (platform == "daily") "general" else platform

    private fun switchPlatform(p: String) {
        if (platform == p) return
        platform = p; prefs.edit().putString("platform", p).apply()
        applyPlatform(greet = true)
        checkBusinessTrial()
    }

    private fun applyPlatform(greet: Boolean) {
        mode = backendMode()
        findViewById<TextView>(R.id.title).text = when (platform) {
            "student" -> "🎓 Student AI"; "business" -> "💼 Business AI"; else -> "🌟 Daily AI"
        } + (if (isCreator) "  👑" else "")
        // highlight active platform (creator switcher)
        fun hi(b: Button, on: Boolean) { b.setBackgroundResource(if (on) R.drawable.chip_on else R.drawable.chip_off); b.setTextColor(Color.parseColor(if (on) "#0A0E1A" else "#9Fb0d0")) }
        hi(findViewById(R.id.studentBtn), platform == "student")
        hi(findViewById(R.id.businessBtn), platform == "business")
        hi(findViewById(R.id.dailyBtn), platform == "daily")
        setupToolkit(platform)
        if (greet) {
            addAi(when (platform) {
                "student" -> "🎓✨ Hey study buddy! Learning here is FUN — earn ⭐XP, level up, and build study 🔥streaks! Play brain games, solve riddles, get fun facts, or tap any tool to learn. Let's gooo! 🚀 (Tap 🏆 My Progress to see your level!)"
                "business" -> "💼 Business mode! I help with plans, invoices, customer replies, marketing, pricing and growth. Tap a tool or tell me what you need."
                else -> "🌟 Daily mode! I'm your everyday helper — reminders, advice, ideas, answers, anything you need day to day. How can I help?"
            })
        }
    }

    private fun setupToolkit(platform: String) {
        val row = findViewById<LinearLayout>(R.id.toolkit)
        row.removeAllViews()
        val base = when (platform) {
            "student" -> listOf(
                "🏆 My Progress" to "__PROGRESS__",
                "🎮 Brain Game" to "Play a fun quick brain game or word game with me to sharpen my mind. Start now and keep score with lots of fun and emojis! 🎉",
                "🧩 Riddle" to "Give me a fun riddle to solve. Make it playful with emojis, and reveal the answer after I guess. 🧠",
                "🎲 Fun Fact" to "Tell me a surprising, fun educational fact with emojis that makes me go wow! 🤯",
                "🤔 Would You Rather" to "Ask me a fun 'would you rather' question that also makes me think and learn. 😄",
                "📚 Explain a Topic" to "Explain a topic to me in a fun, simple way with examples and emojis. Ask me what topic.",
                "📝 Summarize Notes" to "Summarize my notes into clear key points. Ask me to paste the notes.",
                "❓ Quiz Me" to "Quiz me to test my knowledge. Ask me the subject, then ask questions one at a time and mark my answers.",
                "🧮 Solve Problem" to "Help me solve a problem step by step, showing every step. Ask me the question.",
                "🔬 Science Help" to "Help me understand a science concept with a simple explanation and a real-life example. Ask me the concept.",
                "📐 Math Steps" to "Solve this maths step by step and explain each step. Ask me the maths problem.",
                "✍️ Write Essay" to "Help me write a well-structured essay. Ask me the topic, length and key points.",
                "📖 Study Plan" to "Create a study plan/schedule. Ask me my subjects and exam dates.",
                "🗂️ Flashcards" to "Make flashcards (Q&A) from my material. Ask me to paste the material.",
                "📕 Book Summary" to "Summarize a book or chapter for me. Ask me the title or to paste the text.",
                "🧠 Memory Tricks" to "Give me memory tricks/mnemonics to remember something. Ask me what to memorize.",
                "🗣️ Practice Language" to "Help me practice a language by chatting with corrections. Ask me which language and level.",
                "🌍 Translate" to "Translate text for me. Ask me the text and the language."
            )
            "business" -> listOf(
                "📋 Business Plan" to "Help me write a business plan. Ask me what my business is, then create a clear plan.",
                "🧾 Invoice" to "Create a professional invoice. Ask me the customer name, items, quantities and prices, then lay it out cleanly with a total.",
                "💬 Reply Customer" to "Help me reply to a customer professionally. Ask me what the customer said and what I want to say.",
                "📣 Marketing Post" to "Write a catchy marketing post to promote my business. Ask me what I'm promoting.",
                "💰 Price It" to "Help me price my product or service. Ask me about my costs and the product.",
                "🎯 Sales Pitch" to "Write a strong sales pitch. Ask me what I'm selling and to whom.",
                "📦 Product Desc" to "Write an attractive product description. Ask me about the product.",
                "📊 Business Advice" to "Give me practical business advice. Ask me what challenge I'm facing.",
                "🧾 Receipt" to "Make a clean receipt. Ask me the customer, items, amounts and date, then format it neatly with a total.",
                "📦 Inventory" to "Help me track my inventory. Ask me my items and quantities, and keep a clear list. Remember it for next time.",
                "💵 Expenses" to "Help me track business expenses. Ask me what I spent on and how much, then total it and note it.",
                "👥 Customers" to "Help me keep a customer list. Ask me a customer's name, contact and notes, and remember them.",
                "📈 Sales Report" to "Help me make a simple sales report. Ask me my sales figures, then summarize with totals and insights."
            )
            else -> listOf( // daily
                "💡 Quick Answer" to "Answer my question clearly and simply. Ask me what I want to know.",
                "📅 Plan My Day" to "Help me plan my day. Ask me what I need to get done today.",
                "🍳 Recipe/Food" to "Suggest a meal or recipe. Ask me what ingredients I have or what I feel like.",
                "💪 Health Tip" to "Give me a helpful health or wellness tip. Ask me what area (sleep, fitness, stress...).",
                "✍️ Write For Me" to "Write a message/email/text for me. Ask me what it's about and the tone.",
                "🧮 Calculate" to "Help me calculate or convert something. Ask me the numbers.",
                "🎬 Recommend" to "Recommend something (movie, music, book, gift). Ask me my taste.",
                "😊 Motivate Me" to "Give me a genuine motivational boost for today."
            )
        }
        val tools = buildList<Pair<String, String>> {
            add("📚 Teach AI" to "__TEACH__")
            addAll(base)
            if (isCreator) add("👑 Console" to "__CONSOLE__")
        }
        tools.forEach { (label, promptText) ->
            val b = Button(this)
            b.text = label; b.textSize = 12f
            b.setBackgroundResource(R.drawable.chip_off)
            b.setTextColor(Color.parseColor("#3BE0FF"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0); b.layoutParams = lp
            b.setOnClickListener {
                when (promptText) {
                    "__PROGRESS__" -> showProgress()
                    "__TEACH__" -> teachDialog()
                    "__CONSOLE__" -> consoleDialog()
                    else -> { addUser(label); if (platform == "student") awardXp(); sendToAi(promptText) }
                }
            }
            row.addView(b)
        }
    }

    // 📚 Teach My AI — paste knowledge the AI will use
    private fun teachDialog() {
        val box = EditText(this)
        box.hint = "Paste anything you want your AI to know — notes, business info, facts…"
        box.setLines(6); box.gravity = android.view.Gravity.TOP
        AlertDialog.Builder(this)
            .setTitle("📚 Teach My AI")
            .setMessage("Whatever you add here, your AI will remember and use in answers.")
            .setView(box)
            .setPositiveButton("Teach") { _, _ ->
                val text = box.text.toString().trim()
                if (text.length < 3) { toast("Type something to teach first"); return@setPositiveButton }
                teachText(text)
            }
            .setNeutralButton("📎 Upload file") { _, _ -> pickDoc.launch("*/*") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun teachText(text: String) {
        toast("Teaching…")
        Thread {
            val ok = Api.teach(userId, text)
            runOnUiThread { if (ok) addAi("📚 Got it! I've learned that and I'll use it from now on.") else toast("Couldn't save, try again") }
        }.start()
    }

    private fun teachFromDoc(uri: android.net.Uri) {
        addAi("📎 Reading your document…")
        Thread {
            val text = try {
                val name = (uri.lastPathSegment ?: "").lowercase()
                val mime = contentResolver.getType(uri) ?: ""
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                if (mime.contains("pdf") || name.endsWith(".pdf")) {
                    val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes)
                    val out = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
                    doc.close(); out
                } else {
                    String(bytes, Charsets.UTF_8)
                }
            } catch (e: Exception) { "" }
            if (text.isBlank()) {
                runOnUiThread { addAi("Hmm, I couldn't read text from that file. Try a PDF or a text file.") }
            } else {
                val ok = Api.teach(userId, text.take(20000))
                runOnUiThread { addAi(if (ok) "📚 Learned from your document! I'll use it in answers." else "Couldn't save it, try again.") }
            }
        }.start()
    }

    // 👑 Creator Console — submit feature requests + view them (creator only)
    private fun consoleDialog() {
        val box = EditText(this)
        box.hint = "Type a feature to add or a problem to fix…"
        box.setLines(3); box.gravity = android.view.Gravity.TOP
        AlertDialog.Builder(this)
            .setTitle("👑 Creator Console")
            .setMessage("Type what you want added or fixed. It's saved to your request list (the developer builds it).")
            .setView(box)
            .setPositiveButton("Submit") { _, _ ->
                val req = box.text.toString().trim()
                if (req.length < 3) { toast("Type a request first"); return@setPositiveButton }
                Thread {
                    Api.featureRequest(userId, prefs.getString("name", "Creator") ?: "Creator", req)
                    runOnUiThread { addAi("👑 Request saved: \"$req\". The developer will build it. ✅") }
                }.start()
            }
            .setNeutralButton("View requests") { _, _ -> showRequests() }
            .setNegativeButton("Close", null).show()
    }

    private fun showRequests() {
        Thread {
            val raw = Api.featureRequests()
            val list = try {
                val arr = org.json.JSONObject(raw).getJSONArray("requests")
                if (arr.length() == 0) "No requests yet." else (0 until arr.length()).joinToString("\n\n") {
                    val o = arr.getJSONObject(it)
                    "• ${o.optString("request")}  (${o.optString("status")}, ${o.optString("when")})"
                }
            } catch (_: Exception) { "Couldn't load requests." }
            runOnUiThread {
                AlertDialog.Builder(this).setTitle("👑 Your Requests").setMessage(list).setPositiveButton("OK", null).show()
            }
        }.start()
    }

    // #7 Reminders that fire even when the app is closed
    private fun scheduleReminder(text: String) {
        val low = text.lowercase()
        var triggerAt = 0L
        // "in N minutes/hours"
        val inM = Regex("in\\s+(\\d+)\\s*(minute|minutes|min|hour|hours|hr)").find(low)
        if (inM != null) {
            val n = inM.groupValues[1].toLong()
            val ms = if (inM.groupValues[2].startsWith("hour") || inM.groupValues[2] == "hr") n*3600_000 else n*60_000
            triggerAt = System.currentTimeMillis() + ms
        } else {
            // "at H[:MM] am/pm"
            val atM = Regex("at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(low)
            if (atM != null) {
                var h = atM.groupValues[1].toInt(); val min = atM.groupValues[2].toIntOrNull() ?: 0; val ap = atM.groupValues[3]
                if (ap == "pm" && h < 12) h += 12; if (ap == "am" && h == 12) h = 0
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, h); cal.set(java.util.Calendar.MINUTE, min); cal.set(java.util.Calendar.SECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                triggerAt = cal.timeInMillis
            }
        }
        if (triggerAt == 0L) { addAi("When should I remind you? Try 'remind me to call mom in 30 minutes' or 'at 5pm'."); return }
        val task = text.replace(Regex("(?i)remind me to|remind me|in\\s+\\d+.*|at\\s+\\d+.*"), "").trim().ifEmpty { "your reminder" }
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val i = Intent(this, ReminderReceiver::class.java).putExtra("task", task)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(this, (System.currentTimeMillis()%100000).toInt(), i, flags)
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
            val cal = java.util.Calendar.getInstance(); cal.timeInMillis = triggerAt
            val tstr = java.text.SimpleDateFormat("h:mm a", Locale.US).format(cal.time)
            addAi("⏰ Okay! I'll remind you to \"$task\" at $tstr.")
        } catch (e: Exception) { addAi("Couldn't set the reminder: ${e.message}") }
    }

    private fun logoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Log out?")
            .setMessage("Logged in as ${prefs.getString("name","you")} (${prefs.getString("email","")})")
            .setPositiveButton("Log out") { _, _ ->
                prefs.edit().remove("user_id").remove("name").remove("email")
                    .remove("is_creator").remove("chosen").remove("platform").apply()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun sendToAi(text: String) {
        if (bizLocked && platform == "business" && !isCreator) { subscribeDialog(); return }
        if (!isOnline()) { addAi(OfflineBrain.reply(text)); return }
        val thinking = addAi("🤔 Thinking…")
        Thread {
            val reply = Api.chat(userId, text, mode)
            runOnUiThread { (thinking.tag as? TextView)?.text = reply; speak(reply); scrollDown() }
        }.start()
    }

    // ── 🎮 Gamification (Student) ──
    private fun levelOf(xp: Int) = xp / 100 + 1
    private fun levelTitle(lvl: Int) = when {
        lvl < 3 -> "Rookie 🐣"; lvl < 6 -> "Learner 📗"; lvl < 10 -> "Scholar 🎓"
        lvl < 16 -> "Genius 🧠"; lvl < 25 -> "Master 🏅"; else -> "Legend 👑"
    }

    private fun awardXp(points: Int = 10) {
        val before = levelOf(prefs.getInt("xp", 0))
        val xp = prefs.getInt("xp", 0) + points
        // streak
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val last = prefs.getString("last_study_day", "") ?: ""
        var streak = prefs.getInt("streak", 0)
        if (last != today) {
            val cal = java.util.Calendar.getInstance(); cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            val yest = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
            streak = if (last == yest) streak + 1 else 1
        }
        prefs.edit().putInt("xp", xp).putInt("streak", streak).putString("last_study_day", today).apply()
        val after = levelOf(xp)
        if (after > before) {
            val msg = "🎉 LEVEL UP! You're now Level $after — ${levelTitle(after)}! Keep going! 🚀"
            addAi(msg); speak("Level up! You reached level $after!")
        }
    }

    private fun showProgress() {
        val xp = prefs.getInt("xp", 0); val lvl = levelOf(xp); val streak = prefs.getInt("streak", 0)
        val intoLevel = xp % 100; val bar = "▰".repeat(intoLevel / 10) + "▱".repeat(10 - intoLevel / 10)
        addUser("🏆 My Progress")
        addAi("🏆 *Your Learning Progress*\n\n" +
              "⭐ Level $lvl — ${levelTitle(lvl)}\n" +
              "✨ XP: $xp\n" +
              "$bar  ($intoLevel/100 to next level)\n" +
              "🔥 Study streak: $streak day${if (streak == 1) "" else "s"}\n\n" +
              (if (streak >= 3) "You're on fire! Keep the streak alive! 🔥" else "Study a little every day to grow your streak! 💪"))
    }

    private fun doSend() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        if (bizLocked && platform == "business" && !isCreator) { subscribeDialog(); return }
        if (platform == "student" && !isCreator) {
            if (!studentAllowed()) { offerStudentAd(); return }
            studentCount()
        }
        input.setText("")
        addUser(t)
        if (platform == "student") awardXp()
        val low = t.lowercase()
        val isImage = listOf("draw","image of","picture of","generate an image","create an image","make an image","make a picture","design an image","paint").any { low.contains(it) }
        if (isImage) { generateImage(cleanPrompt(t)); return }
        if (low.startsWith("remember ")) {
            val fact = t.substring(8).trim()
            Thread { Api.remember(userId, fact) }.start()
            addAi("Got it — I'll remember that. 🧠"); return
        }
        if (low.startsWith("remind me")) { scheduleReminder(t); return }
        if (!isOnline()) { addAi(OfflineBrain.reply(t)); return }
        val thinking = addAi("🤔 Thinking… (first reply can take ~30s if the AI was asleep)")
        Thread {
            val reply = Api.chat(userId, t, mode)
            runOnUiThread { (thinking.tag as? TextView)?.text = reply; speak(reply); scrollDown() }
        }.start()
    }

    private fun cleanPrompt(t: String): String =
        t.replace(Regex("(?i)(draw me|draw a|draw an|draw|generate an image of|generate an image|create an image of|create an image|make an image of|make an image|make a picture of|a picture of|image of|picture of|design an image of|paint me|paint a|paint)"), "").trim().ifEmpty { t }

    // ── Student daily free messages (earn more by watching an ad) ──
    private val FREE_STUDENT = 25
    private fun studentAllowed(): Boolean {
        val today = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
        if (prefs.getString("stu_day", "") != today)
            prefs.edit().putString("stu_day", today).putInt("stu_count", 0).putInt("stu_bonus", 0).apply()
        return prefs.getInt("stu_count", 0) < FREE_STUDENT + prefs.getInt("stu_bonus", 0)
    }
    private fun studentCount() = prefs.edit().putInt("stu_count", prefs.getInt("stu_count", 0) + 1).apply()
    private fun offerStudentAd() {
        AlertDialog.Builder(this)
            .setTitle("🎓 Daily messages used up")
            .setMessage("You've used your free messages for today! Watch 1 ad to get +15 more.")
            .setPositiveButton("Watch ad") { _, _ ->
                Ads.showRewarded(this,
                    onReward = { prefs.edit().putInt("stu_bonus", prefs.getInt("stu_bonus", 0) + 15).apply(); toast("🎉 +15 messages! Keep learning!") },
                    onUnavailable = { toast("No ad available right now — try again soon.") })
            }
            .setNegativeButton("Later", null).show()
    }

    // ── Image credits (earn by watching 5 ads) ──
    private fun imgCredits() = prefs.getInt("img_credits", 2)  // 2 free to start
    private fun setImgCredits(n: Int) = prefs.edit().putInt("img_credits", n).apply()

    private fun withImageCredit(action: () -> Unit) {
        if (isCreator || imgCredits() > 0) {
            if (!isCreator) setImgCredits(imgCredits() - 1); action(); return
        }
        AlertDialog.Builder(this)
            .setTitle("🖼️ Out of image credits")
            .setMessage("Each image (create, edit, or analyze) needs 1 credit.\nWatch 5 short ads to earn 1 credit. Continue?")
            .setPositiveButton("Watch 5 ads") { _, _ -> watchAdsForImage { action() } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun generateImage(prompt: String) = withImageCredit { doGenerateImage(prompt) }

    private fun watchAdsForImage(onEarned: () -> Unit) {
        var watched = 0
        fun next() {
            if (watched >= 5) { toast("🎉 You earned 1 image!"); onEarned(); return }
            Ads.showRewarded(this,
                onReward = { watched++; toast("Ad $watched/5 ✅"); next() },
                onUnavailable = { toast("No ad available right now — try again soon.") })
        }
        next()
    }

    private fun doGenerateImage(prompt: String) {
        val loading = addAi("🎨 Creating: $prompt …")
        Thread {
            try {
                val url = Api.imageUrl(prompt)
                val bmp = BitmapFactory.decodeStream(URL(url).openStream())
                runOnUiThread { (loading.tag as? TextView)?.text = "🎨 $prompt"; if (bmp != null) addImage(bmp); scrollDown() }
            } catch (e: Exception) {
                runOnUiThread { (loading.tag as? TextView)?.text = "Couldn't create image: ${e.message}" }
            }
        }.start()
    }

    // ── Voice ──
    private fun listen() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) { toast("Voice not available"); return }
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        val rec = recognizer!!
        val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (!callMode) toast("🎤 Listening…")
        rec.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(r: Bundle?) {
                val said = r?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!said.isNullOrEmpty()) { input.setText(said); doSend() }
                else if (callMode) listen()   // heard nothing, keep listening
            }
            override fun onError(e: Int) { if (callMode) input.postDelayed({ if (callMode) listen() }, 800) }
            override fun onReadyForSpeech(p0: Bundle?) {}; override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}; override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}; override fun onPartialResults(p0: Bundle?) {}; override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        rec.startListening(i)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(1.05f); tts?.setSpeechRate(1.0f)
            ttsReady = true
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onDone(id: String?) { if (callMode) runOnUiThread { listen() } }
                override fun onStart(id: String?) {}
                @Deprecated("") override fun onError(id: String?) {}
            })
        }
    }

    private fun toggleCall() {
        callMode = !callMode
        if (callMode) {
            muted = false; findViewById<Button>(R.id.muteBtn).text = "🔊"
            findViewById<Button>(R.id.callBtn).text = "📵"
            addAi("📞 Call started — just talk to me, hands-free! Tap 📵 to end.")
            listen()
        } else {
            findViewById<Button>(R.id.callBtn).text = "📞"
            try { recognizer?.cancel() } catch (_: Exception) {}
            tts?.stop()
            addAi("📴 Call ended.")
        }
    }
    private fun speak(text: String) {
        if (ttsReady && !muted) tts?.speak(text.take(600), TextToSpeech.QUEUE_FLUSH, null, "m")
    }

    // ── UI ──
    private fun addUser(t: String) = bubble(t, true)
    private fun addAi(t: String) = bubble(t, false)
    private fun bubble(text: String, user: Boolean): View {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(if (user) Color.parseColor("#0A0E1A") else Color.parseColor("#E6EEFF"))
        tv.textSize = 15.5f
        tv.setPadding(36, 26, 36, 26)
        tv.setBackgroundResource(if (user) R.drawable.bubble_user else R.drawable.bubble_ai)
        if (!user) {
            tv.setOnLongClickListener {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, tv.text.toString())
                }
                startActivity(Intent.createChooser(share, "Share / Send"))
                true
            }
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = if (user) Gravity.END else Gravity.START
        lp.setMargins(if (user) 90 else 12, 8, if (user) 12 else 90, 8)
        tv.layoutParams = lp
        val holder = LinearLayout(this); holder.tag = tv; holder.addView(tv)
        messages.addView(holder); scrollDown(); return holder
    }
    private fun addImage(bmp: android.graphics.Bitmap) {
        val iv = ImageView(this); iv.setImageBitmap(bmp); iv.adjustViewBounds = true
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(12, 4, 70, 12); iv.layoutParams = lp
        messages.addView(iv); scrollDown()
    }
    private fun scrollDown() { scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ── Floating bubble ──
    // Floating bubble is OPTIONAL — this toggles it on/off
    private fun toggleBubble() {
        if (prefs.getBoolean("bubble_on", false)) {
            stopService(Intent(this, BubbleService::class.java))
            prefs.edit().putBoolean("bubble_on", false).apply()
            findViewById<Button>(R.id.bubbleBtn).text = "💬 Float"
            toast("Floating bubble turned OFF")
        } else {
            startBubble()
        }
    }

    private fun startBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            toast("Allow 'display over other apps' to float me on your screen (optional)")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, BubbleService::class.java))
        prefs.edit().putBoolean("bubble_on", true).apply()
        findViewById<Button>(R.id.bubbleBtn).text = "💬 Off"
        toast("Bubble ON! Tap it anytime to chat 💬")
        moveTaskToBack(true)
    }

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        callMode = false
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
    }
}
