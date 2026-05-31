package com.myai.app

import android.Manifest
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
    private var mode = "general"
    private val userId by lazy { prefs.getString("user_id", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("user_id", it).apply() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        prefs = getSharedPreferences("myai", Context.MODE_PRIVATE)
        tts = TextToSpeech(this, this)

        messages = findViewById(R.id.messages)
        scroll = findViewById(R.id.scroll)
        input = findViewById(R.id.input)
        mode = "business"   // this is a Business AI

        setupToolkit()

        findViewById<Button>(R.id.sendBtn).setOnClickListener { doSend() }
        input.setOnEditorActionListener { _, _, _ -> doSend(); true }
        findViewById<Button>(R.id.micBtn).setOnClickListener { listen() }
        findViewById<Button>(R.id.muteBtn).setOnClickListener {
            muted = !muted
            (it as Button).text = if (muted) "🔇" else "🔊"
            if (muted) tts?.stop()
        }
        findViewById<Button>(R.id.bubbleBtn).setOnClickListener { startBubble() }

        Thread { Api.wake() }.start()
        addAi("💼 Welcome to your Business AI. I help with plans, invoices, customer replies, marketing, pricing and growth. Tap a tool above or just tell me what you need.")
        ensureMicPermission()
    }

    // Business toolkit — one-tap actions that send guided prompts
    private fun setupToolkit() {
        val row = findViewById<LinearLayout>(R.id.toolkit)
        row.removeAllViews()
        val tools = listOf(
            "📋 Business Plan" to "Help me write a business plan. Ask me what my business is, then create a clear plan.",
            "🧾 Invoice" to "Create a professional invoice. Ask me the customer name, items, quantities and prices, then lay it out cleanly with a total.",
            "💬 Reply Customer" to "Help me reply to a customer professionally. Ask me what the customer said and what I want to say.",
            "📣 Marketing Post" to "Write a catchy marketing post to promote my business. Ask me what I'm promoting.",
            "💰 Price It" to "Help me price my product or service. Ask me about my costs and the product.",
            "🎯 Sales Pitch" to "Write a strong sales pitch. Ask me what I'm selling and to whom.",
            "📦 Product Desc" to "Write an attractive product description. Ask me about the product.",
            "📊 Business Advice" to "Give me practical business advice. Ask me what challenge I'm facing."
        )
        tools.forEach { (label, promptText) ->
            val b = Button(this)
            b.text = label; b.textSize = 12f
            b.setBackgroundResource(R.drawable.chip_off)
            b.setTextColor(Color.parseColor("#3BE0FF"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0); b.layoutParams = lp
            b.setOnClickListener { addUser(label); sendToAi(promptText) }
            row.addView(b)
        }
    }

    private fun sendToAi(text: String) {
        val thinking = addAi("…")
        Thread {
            val reply = Api.chat(userId, text, mode)
            runOnUiThread { (thinking.tag as? TextView)?.text = reply; speak(reply); scrollDown() }
        }.start()
    }

    private fun doSend() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        addUser(t)
        val low = t.lowercase()
        val isImage = listOf("draw","image of","picture of","generate an image","create an image","make an image","make a picture","design an image","paint").any { low.contains(it) }
        if (isImage) { generateImage(cleanPrompt(t)); return }
        if (low.startsWith("remember ")) {
            val fact = t.substring(8).trim()
            Thread { Api.remember(userId, fact) }.start()
            addAi("Got it — I'll remember that. 🧠"); return
        }
        val thinking = addAi("…")
        Thread {
            val reply = Api.chat(userId, t, mode)
            runOnUiThread { (thinking.tag as? TextView)?.text = reply; speak(reply); scrollDown() }
        }.start()
    }

    private fun cleanPrompt(t: String): String =
        t.replace(Regex("(?i)(draw me|draw a|draw an|draw|generate an image of|generate an image|create an image of|create an image|make an image of|make an image|make a picture of|a picture of|image of|picture of|design an image of|paint me|paint a|paint)"), "").trim().ifEmpty { t }

    private fun generateImage(prompt: String) {
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
        val rec = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        toast("🎤 Listening…")
        rec.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(r: Bundle?) {
                val said = r?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!said.isNullOrEmpty()) { input.setText(said); doSend() }
                rec.destroy()
            }
            override fun onError(e: Int) { rec.destroy() }
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
    private fun startBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            toast("Allow 'display over other apps' to float me on your screen")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, BubbleService::class.java))
        toast("Bubble on! Tap it anytime to chat 💬")
        moveTaskToBack(true)
    }

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onDestroy() { super.onDestroy(); try { tts?.shutdown() } catch (_: Exception) {} }
}
