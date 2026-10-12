package com.baer.mealiekiosk

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import kotlin.math.abs

const val TAG = "MealieKiosk"
private const val PREFS = "kiosk"
private const val KEY_URL = "url"
const val EXTRA_URL = "url"
private const val RETRY_MS = 5000L
private const val EXIT_HOLD_MS = 3000L
private val RECIPE_PATH = Regex("^/g/[^/]+/r/[^/]+/?$")

class MainActivity : ComponentActivity() {
    companion object {
        /** Set by [PostureService] when the tablet was flipped down, so the next resume starts over. */
        @Volatile
        var deployed = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var startUrl: String? = null
    private lateinit var web: WebView
    private lateinit var setup: View
    private lateinit var status: TextView
    private lateinit var feedback: TextView
    private lateinit var voice: VoiceController
    private var kioskJs = ""
    private var failedUrl: String? = null
    private var cookMode = false
    private var micAsked = false
    private var exitX = 0f
    private var exitY = 0f

    private val retry = Runnable { (failedUrl ?: startUrl)?.let { web.loadUrl(it) } }
    private val openSettings = Runnable {
        Log.i(TAG, "hidden exit to settings")
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private val hideFeedback = Runnable { feedback.visibility = View.GONE }
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateVoice() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // No setTurnScreenOn: wake() re-launches this activity, which re-arms that flag, and it
        // turns the display back on over the lock screen right after lockNow(). wake() lights it.
        setShowWhenLocked(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        kioskJs = assets.open("kiosk.js").bufferedReader().use { it.readText() }
        WebView.setWebContentsDebuggingEnabled(true)

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = KioskClient()
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            textSize = 24f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        feedback = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC1E1E1E"))
            textSize = 20f
            setPadding(32, 16, 32, 16)
            visibility = View.GONE
        }
        setup = setupView()
        setContentView(FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-1, -1))
            addView(setup, FrameLayout.LayoutParams(-1, -1))
            addView(feedback, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack()
            }
        })

        voice = VoiceController(this, CommandParser()) { command, text ->
            runOnUiThread {
                when (command.kind) {
                    Kind.DOWN -> js("mealieKiosk.scroll(1)")
                    Kind.UP -> js("mealieKiosk.scroll(-1)")
                    Kind.NEXT -> js("mealieKiosk.step(1)")
                    Kind.PREV -> js("mealieKiosk.step(-1)")
                    Kind.FOCUS -> js("mealieKiosk.focus(${command.step})")
                    Kind.DONE -> js("mealieKiosk.done(${command.step})")
                }
                showFeedback(text)
            }
        }
        startUrl = urlFromIntent(intent) ?: getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URL, null)
        if (savedInstanceState != null && startUrl != null) web.restoreState(savedInstanceState) else open()
        startForegroundService(Intent(this, PostureService::class.java))
    }

    // Changes the server later: adb shell am start -n com.baer.mealiekiosk/.MainActivity --es url <url>
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        urlFromIntent(intent)?.let { open() }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (deployed) {
            deployed = false
            startUrl?.let { web.loadUrl(it) }
        }
        updateVoice()
    }

    override fun onPause() {
        voice.stop()
        handler.removeCallbacks(openSettings)
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        voice.close()
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Hidden exit: hold a finger in the top-left corner for 3 s. Touches still reach the page.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val corner = 96 * resources.displayMetrics.density
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (ev.x < corner && ev.y < corner) {
                exitX = ev.x
                exitY = ev.y
                handler.postDelayed(openSettings, EXIT_HOLD_MS)
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.x - exitX) > slop || abs(ev.y - exitY) > slop) handler.removeCallbacks(openSettings)
            else -> handler.removeCallbacks(openSettings)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun open() {
        val url = startUrl
        setup.visibility = if (url == null) View.VISIBLE else View.GONE
        if (url != null) web.loadUrl(url)
    }

    private fun store(url: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply()
        startUrl = url
    }

    private fun urlFromIntent(intent: Intent): String? {
        val raw = intent.getStringExtra(EXTRA_URL) ?: return null
        val url = ServerUrl.parse(raw) ?: return null.also { Log.w(TAG, "ignored url extra $raw") }
        store(url)
        return url
    }

    // First run only: one field for the server address. Clearing app data shows it again.
    private fun setupView(): View {
        val input = EditText(this).apply {
            hint = "https://mealie.example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            isSingleLine = true
            textSize = 22f
        }
        fun save() {
            val url = ServerUrl.parse(input.text.toString())
                ?: return run { input.error = getString(R.string.setup_invalid) }
            store(url)
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
            hideSystemBars()
            open()
        }
        input.setOnEditorActionListener { _, _, _ -> save(); true }
        val width = (480 * resources.displayMetrics.density).toInt()
        // Top-aligned: the soft keyboard covers the lower half of a landscape screen.
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            addView(TextView(context).apply {
                text = getString(R.string.setup_title)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 24f
            })
            addView(input, LinearLayout.LayoutParams(width, -2))
            addView(Button(context).apply {
                text = getString(R.string.setup_open)
                setOnClickListener { save() }
            })
            visibility = View.GONE
        }
    }

    private fun js(call: String) = web.evaluateJavascript("window.mealieKiosk&&$call", null)

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Voice runs only in Mealie's cook mode, so the microphone is off the rest of the time.
    private fun updateVoice() {
        val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (!cookMode || !resumed) return voice.stop()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.start()
        } else if (!micAsked) {
            micAsked = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showFeedback(text: String) {
        feedback.text = text
        feedback.visibility = View.VISIBLE
        handler.removeCallbacks(hideFeedback)
        handler.postDelayed(hideFeedback, 2000)
    }

    private fun showError(url: String, reason: String) {
        Log.w(TAG, "load failed $url: $reason")
        failedUrl = url
        status.text = getString(R.string.unreachable, reason)
        status.visibility = View.VISIBLE
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, RETRY_MS)
    }

    private inner class KioskClient : WebViewClient() {
        private var failed = false

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
            !ServerUrl.sameHost(request.url.toString(), startUrl ?: "")

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            failed = false
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            failed = true
            showError(request.url.toString(), error.description.toString())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (!request.isForMainFrame || response.statusCode < 500) return
            failed = true
            showError(request.url.toString(), "HTTP ${response.statusCode}")
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (failed) return
            failedUrl = null
            status.visibility = View.GONE
            view.evaluateJavascript(kioskJs, null)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            val uri = Uri.parse(url)
            val onRecipe = RECIPE_PATH.matches(uri.path ?: "")
            val cook = onRecipe && uri.getQueryParameter("cook") == "true"
            if (cook != cookMode) {
                cookMode = cook
                Log.i(TAG, "cook mode=$cook $url")
                updateVoice()
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.e(TAG, "renderer gone, crash=${detail.didCrash()}")
            (view.parent as ViewGroup).removeView(view)
            view.destroy()
            recreate()
            return true
        }
    }
}
