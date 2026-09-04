package android.MetaCore

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

object AdvancedPopupHelper {

    private const val TAG = "AdvancedPopupHelper"

    private const val AUTO_CLOSE_SECONDS = 12
    private const val MAX_WIDTH_DP = 410
    private const val MAX_HEIGHT_DP = 660

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentDialog: WeakReference<Dialog>? = null

    @Volatile
    private var currentWebView: WeakReference<WebView>? = null

    private val showing = AtomicBoolean(false)

    // ------------------------------------------------------------------------
    // PUBLIC
    // ------------------------------------------------------------------------

    @JvmStatic
    fun showAuto() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showAutoInternal()
        } else {
            mainHandler.post {
                showAutoInternal()
            }
        }
    }

    @JvmStatic
    fun dismiss() {
        mainHandler.post {
            dismissInternal()
        }
    }

    @JvmStatic
    fun isShowing(): Boolean {
        return try {
            currentDialog?.get()?.isShowing == true
        } catch (_: Throwable) {
            false
        }
    }

    // ------------------------------------------------------------------------
    // ACTIVITY
    // ------------------------------------------------------------------------

    private fun showAutoInternal() {
        try {
            val activity = getTopActivity() ?: return

            if (activity.isFinishing) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                if (activity.isDestroyed) return
            }

            showPopup(activity)

        } catch (_: Throwable) {
            // Popup must never crash the host.
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getTopActivity(): Activity? {
        return try {

            val activityThreadClass =
                Class.forName("android.app.ActivityThread")

            val currentActivityThread =
                activityThreadClass
                    .getDeclaredMethod("currentActivityThread")
                    .apply {
                        isAccessible = true
                    }
                    .invoke(null)
                    ?: return null

            val activitiesField =
                activityThreadClass
                    .getDeclaredField("mActivities")
                    .apply {
                        isAccessible = true
                    }

            val activities =
                activitiesField.get(currentActivityThread) as? Map<*, *>
                    ?: return null

            var fallbackActivity: Activity? = null

            for (recordObject in activities.values) {

                val record = recordObject ?: continue

                val activity = getActivityFromRecord(record) ?: continue

                if (fallbackActivity == null) {
                    fallbackActivity = activity
                }

                val paused = readBooleanField(record, "paused")
                    ?: readBooleanField(record, "mPaused")
                    ?: false

                val stopped = readBooleanField(record, "stopped")
                    ?: readBooleanField(record, "mStopped")
                    ?: false

                if (!paused && !stopped) {
                    return activity
                }
            }

            fallbackActivity

        } catch (_: Throwable) {
            null
        }
    }

    private fun getActivityFromRecord(record: Any): Activity? {
        return try {

            val names = arrayOf(
                "activity",
                "mActivity"
            )

            for (name in names) {
                try {
                    val field = findField(record.javaClass, name)
                    if (field != null) {
                        field.isAccessible = true

                        val value = field.get(record)

                        if (value is Activity) {
                            return value
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            null

        } catch (_: Throwable) {
            null
        }
    }

    private fun readBooleanField(
        instance: Any,
        name: String
    ): Boolean? {

        return try {

            val field =
                findField(instance.javaClass, name)
                    ?: return null

            field.isAccessible = true

            when (val value = field.get(instance)) {
                is Boolean -> value
                else -> null
            }

        } catch (_: Throwable) {
            null
        }
    }

    private fun findField(
        clazz: Class<*>,
        name: String
    ): Field? {

        var current: Class<*>? = clazz

        while (current != null) {

            try {
                return current.getDeclaredField(name)
            } catch (_: Throwable) {
            }

            current = current.superclass
        }

        return null
    }

    // ------------------------------------------------------------------------
    // DIALOG
    // ------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPopup(activity: Activity) {

        mainHandler.post {

            if (!showing.compareAndSet(false, true)) {
                return@post
            }

            try {

                dismissInternal(resetFlag = false)

                val dialog =
                    Dialog(
                        activity,
                        android.R.style.Theme_Translucent_NoTitleBar
                    )

                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)

                val webView = WebView(activity)

                currentDialog = WeakReference(dialog)
                currentWebView = WeakReference(webView)

                configureWebView(
                    webView = webView,
                    dialog = dialog,
                    activity = activity
                )

                webView.loadDataWithBaseURL(
                    "file:///android_res/drawable/",
                    HTML,
                    "text/html",
                    "UTF-8",
                    null
                )

                dialog.setContentView(webView)

                dialog.setOnDismissListener {

                    try {
                        cleanupWebView(webView)
                    } catch (_: Throwable) {
                    }

                    currentWebView = null
                    currentDialog = null

                    showing.set(false)
                }

                dialog.show()

                configureWindow(
                    activity = activity,
                    dialog = dialog
                )

            } catch (_: Throwable) {

                showing.set(false)

                try {
                    dismissInternal()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun configureWindow(
        activity: Activity,
        dialog: Dialog
    ) {

        val window =
            dialog.window ?: return

        try {

            val metrics =
                activity.resources.displayMetrics

            val horizontalMargin =
                dp(activity, 18)

            val verticalMargin =
                dp(activity, 32)

            val availableWidth =
                (metrics.widthPixels - horizontalMargin)
                    .coerceAtLeast(dp(activity, 280))

            val availableHeight =
                (metrics.heightPixels - verticalMargin)
                    .coerceAtLeast(dp(activity, 400))

            val targetWidth =
                min(
                    dp(activity, MAX_WIDTH_DP),
                    availableWidth
                )

            val targetHeight =
                min(
                    dp(activity, MAX_HEIGHT_DP),
                    availableHeight
                )

            window.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )

            window.setLayout(
                targetWidth,
                targetHeight
            )

            window.setGravity(Gravity.CENTER)

            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            window.addFlags(
                WindowManager.LayoutParams.FLAG_SECURE
            )

            window.setDimAmount(0.82f)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor =
                    Color.TRANSPARENT

                window.navigationBarColor =
                    Color.BLACK
            }

        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------------
    // WEBVIEW
    // ------------------------------------------------------------------------

    @SuppressLint(
        "SetJavaScriptEnabled",
        "JavascriptInterface"
    )
    private fun configureWebView(
        webView: WebView,
        dialog: Dialog,
        activity: Activity
    ) {

        webView.setBackgroundColor(
            Color.TRANSPARENT
        )

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.overScrollMode =
            View.OVER_SCROLL_NEVER

        webView.isLongClickable = false

        webView.setOnLongClickListener {
            true
        }

        try {
            webView.setLayerType(
                View.LAYER_TYPE_HARDWARE,
                null
            )
        } catch (_: Throwable) {
        }

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = false

            databaseEnabled = false

            allowContentAccess = false

            allowFileAccess = true

            blockNetworkLoads = true

            loadsImagesAutomatically = true

            cacheMode =
                WebSettings.LOAD_NO_CACHE

            defaultTextEncodingName =
                "UTF-8"

            javaScriptCanOpenWindowsAutomatically =
                false

            setSupportMultipleWindows(false)

            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP
            ) {

                mixedContentMode =
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                safeBrowsingEnabled = true
            }
        }

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?
                ): Boolean {
                    return true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(
                        view,
                        request,
                        error
                    )
                }
            }

        val deviceInfo =
            buildDeviceInfo(activity)

        val bridge =
            object {

                @JavascriptInterface
                fun close() {

                    mainHandler.post {

                        try {

                            if (dialog.isShowing) {
                                dialog.dismiss()
                            }

                        } catch (_: Throwable) {
                        }
                    }
                }

                @JavascriptInterface
                fun getDeviceInfo(): String {
                    return deviceInfo
                }

                @JavascriptInterface
                fun getAutoCloseSeconds(): Int {
                    return AUTO_CLOSE_SECONDS
                }
            }

        webView.addJavascriptInterface(
            bridge,
            "Android"
        )
    }

    private fun cleanupWebView(
        webView: WebView
    ) {

        try {
            webView.removeJavascriptInterface(
                "Android"
            )
        } catch (_: Throwable) {
        }

        try {
            webView.stopLoading()
        } catch (_: Throwable) {
        }

        try {
            webView.webChromeClient = null
        } catch (_: Throwable) {
        }

        try {
            webView.webViewClient =
                WebViewClient()
        } catch (_: Throwable) {
        }

        try {
            webView.clearHistory()
        } catch (_: Throwable) {
        }

        try {
            webView.clearCache(true)
        } catch (_: Throwable) {
        }

        try {
            webView.loadUrl(
                "about:blank"
            )
        } catch (_: Throwable) {
        }

        try {
            webView.removeAllViews()
        } catch (_: Throwable) {
        }

        try {
            webView.destroy()
        } catch (_: Throwable) {
        }
    }

    private fun dismissInternal(
        resetFlag: Boolean = true
    ) {

        try {

            currentDialog
                ?.get()
                ?.let {

                    if (it.isShowing) {
                        it.dismiss()
                    }
                }

        } catch (_: Throwable) {
        }

        currentDialog = null
        currentWebView = null

        if (resetFlag) {
            showing.set(false)
        }
    }

    // ------------------------------------------------------------------------
    // DEVICE INFO
    // ------------------------------------------------------------------------

    private fun buildDeviceInfo(
        activity: Activity
    ): String {

        return try {

            JSONObject().apply {

                put(
                    "manufacturer",
                    safeText(Build.MANUFACTURER)
                )

                put(
                    "model",
                    safeText(Build.MODEL)
                )

                put(
                    "device",
                    buildString {

                        append(
                            safeText(
                                Build.MANUFACTURER
                            )
                        )

                        if (!Build.MODEL.isNullOrBlank()) {

                            append(" ")

                            append(
                                safeText(
                                    Build.MODEL
                                )
                            )
                        }
                    }.trim()
                )

                put(
                    "android",
                    Build.VERSION.RELEASE
                        ?: "Unknown"
                )

                put(
                    "api",
                    Build.VERSION.SDK_INT
                )

                put(
                    "abi",
                    Build.SUPPORTED_ABIS
                        ?.firstOrNull()
                        ?: "Unknown"
                )

                put(
                    "brand",
                    safeText(Build.BRAND)
                )

                put(
                    "product",
                    safeText(Build.PRODUCT)
                )

                put(
                    "density",
                    activity.resources
                        .displayMetrics
                        .density
                )

            }.toString()

        } catch (_: Throwable) {

            """{
                "device":"Unknown",
                "android":"Unknown",
                "api":0,
                "abi":"Unknown"
            }""".trimIndent()
        }
    }

    private fun safeText(
        value: String?
    ): String {

        return value
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }
            ?: "Unknown"
    }

    private fun dp(
        activity: Activity,
        value: Int
    ): Int {

        return (
            value *
                activity.resources
                    .displayMetrics
                    .density
            ).toInt()
    }

    // ------------------------------------------------------------------------
    // HTML
    // ------------------------------------------------------------------------

    private const val HTML = """
<!DOCTYPE html>

<html lang="en">

<head>

<meta charset="UTF-8">

<meta
    name="viewport"
    content="
        width=device-width,
        initial-scale=1,
        maximum-scale=1,
        user-scalable=no
    "
>

<meta
    name="color-scheme"
    content="dark"
>

<style>

:root {

    --bg: #06080d;

    --surface: #0d1119;

    --surface2: #111722;

    --gold: #e7bd63;

    --gold2: #8f641f;

    --danger: #ff5871;

    --danger2: #a71e3a;

    --text: #f3f5f8;

    --muted: #8f99a8;

    --border: rgba(255,255,255,.08);

    --goldBorder: rgba(231,189,99,.32);

    --dangerBorder: rgba(255,88,113,.24);
}

* {
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
}

html,
body {

    width: 100%;

    height: 100%;

    margin: 0;

    padding: 0;

    overflow: hidden;
}

body {

    display: flex;

    align-items: center;

    justify-content: center;

    background: transparent;

    color: var(--text);

    font-family:
        -apple-system,
        BlinkMacSystemFont,
        "Segoe UI",
        Roboto,
        Arial,
        sans-serif;

    -webkit-font-smoothing: antialiased;
}

.shell {

    position: relative;

    width: 100%;

    height: 100%;

    display: flex;

    align-items: center;

    justify-content: center;

    padding: 8px;
}

.card {

    position: relative;

    width: 100%;

    max-width: 382px;

    max-height: 100%;

    overflow: hidden;

    border:
        1px solid
        var(--goldBorder);

    border-radius: 30px;

    background:
        radial-gradient(
            circle at 50% -10%,
            rgba(231,189,99,.17),
            transparent 36%
        ),
        linear-gradient(
            150deg,
            rgba(22,28,39,.995),
            rgba(7,9,14,.998) 68%
        );

    box-shadow:
        0 32px 90px rgba(0,0,0,.82),
        0 0 0 1px rgba(255,255,255,.015),
        inset 0 1px rgba(255,255,255,.045);

    animation:
        cardEnter
        .62s
        cubic-bezier(.18,.92,.25,1)
        both;
}

.card::before {

    content: "";

    position: absolute;

    z-index: 0;

    width: 260px;

    height: 260px;

    left: 50%;

    top: -200px;

    transform:
        translateX(-50%);

    border-radius: 50%;

    background:
        rgba(231,189,99,.35);

    filter:
        blur(80px);

    pointer-events: none;
}

.card::after {

    content: "";

    position: absolute;

    z-index: 0;

    width: 180px;

    height: 180px;

    right: -100px;

    bottom: -100px;

    border-radius: 50%;

    background:
        rgba(255,88,113,.08);

    filter:
        blur(54px);

    pointer-events: none;
}

.inner {

    position: relative;

    z-index: 2;

    padding:
        20px
        19px
        18px;
}

/* --------------------------------------------------------- */
/* TOP BAR                                                   */
/* --------------------------------------------------------- */

.top {

    display: flex;

    align-items: center;

    justify-content: space-between;

    gap: 12px;
}

.identity {

    display: flex;

    align-items: center;

    gap: 12px;

    min-width: 0;
}

.logo {

    position: relative;

    width: 54px;

    height: 54px;

    min-width: 54px;

    display: grid;

    place-items: center;

    overflow: hidden;

    border:
        1px solid
        rgba(231,189,99,.4);

    border-radius: 17px;

    background:
        linear-gradient(
            145deg,
            #191d25,
            #090b10
        );

    box-shadow:
        0 10px 24px rgba(0,0,0,.45),
        inset 0 1px rgba(255,255,255,.05);
}

.logo::before {

    content: "";

    position: absolute;

    inset: -30%;

    background:
        conic-gradient(
            transparent,
            rgba(231,189,99,.6),
            transparent 28%
        );

    animation:
        rotateGlow
        5s
        linear
        infinite;
}

.logoInner {

    position: relative;

    z-index: 2;

    width: 46px;

    height: 46px;

    display: grid;

    place-items: center;

    border-radius: 14px;

    background:
        linear-gradient(
            145deg,
            #151922,
            #080a0f
        );
}

.logoMark {

    font-size: 22px;

    line-height: 1;

    font-weight: 950;

    color: var(--gold);

    letter-spacing: -1px;

    text-shadow:
        0 0 22px
        rgba(231,189,99,.34);
}

.copy {
    min-width: 0;
}

.eyebrow {

    color: var(--gold);

    font-size: 8px;

    font-weight: 900;

    letter-spacing: 2.2px;

    text-transform: uppercase;
}

.brand {

    margin-top: 4px;

    overflow: hidden;

    color: #f3f5f8;

    font-size: 17px;

    font-weight: 850;

    letter-spacing: .25px;

    text-overflow: ellipsis;

    white-space: nowrap;
}

.secure {

    display: flex;

    align-items: center;

    gap: 6px;

    padding:
        6px
        9px;

    border:
        1px solid
        rgba(102,227,158,.16);

    border-radius: 999px;

    background:
        rgba(102,227,158,.06);

    color: #91e9b8;

    font-size: 7px;

    font-weight: 850;

    letter-spacing: 1.1px;

    white-space: nowrap;
}

.secureDot {

    width: 5px;

    height: 5px;

    border-radius: 50%;

    background: #66e39e;

    box-shadow:
        0 0 9px
        #66e39e;
}

/* --------------------------------------------------------- */
/* HERO                                                      */
/* --------------------------------------------------------- */

.hero {

    position: relative;

    margin-top: 18px;

    padding:
        20px
        14px
        18px;

    overflow: hidden;

    text-align: center;

    border:
        1px solid
        var(--dangerBorder);

    border-radius: 23px;

    background:
        radial-gradient(
            circle at 50% 0,
            rgba(255,88,113,.11),
            transparent 55%
        ),
        rgba(255,255,255,.018);
}

.hero::after {

    content: "";

    position: absolute;

    width: 140px;

    height: 140px;

    left: 50%;

    top: 10px;

    transform:
        translateX(-50%);

    border-radius: 50%;

    background:
        rgba(255,88,113,.06);

    filter:
        blur(35px);

    pointer-events: none;
}

.lockOuter {

    position: relative;

    z-index: 2;

    width: 76px;

    height: 76px;

    margin:
        0
        auto
        15px;

    display: grid;

    place-items: center;

    border-radius: 50%;

    background:
        radial-gradient(
            circle,
            rgba(255,88,113,.17),
            rgba(255,88,113,.025)
        );

    box-shadow:
        0 0 0 8px rgba(255,88,113,.025),
        0 0 38px rgba(255,88,113,.09);

    animation:
        pulse
        2.3s
        ease-in-out
        infinite;
}

.lockInner {

    width: 56px;

    height: 56px;

    display: grid;

    place-items: center;

    border:
        1px solid
        rgba(255,88,113,.24);

    border-radius: 50%;

    background:
        linear-gradient(
            145deg,
            rgba(255,88,113,.13),
            rgba(255,255,255,.018)
        );
}

.lockInner svg {

    width: 27px;

    height: 27px;

    fill: none;

    stroke: var(--danger);

    stroke-width: 1.75;

    stroke-linecap: round;

    stroke-linejoin: round;

    filter:
        drop-shadow(
            0 0 8px
            rgba(255,88,113,.45)
        );
}

.heroTitle {

    position: relative;

    z-index: 2;

    margin: 0;

    color: #ffffff;

    font-size: 23px;

    font-weight: 900;

    letter-spacing: -.2px;
}

.heroText {

    position: relative;

    z-index: 2;

    max-width: 285px;

    margin:
        8px
        auto
        0;

    color: var(--muted);

    font-size: 11px;

    line-height: 1.62;
}

.status {

    position: relative;

    z-index: 2;

    display: inline-flex;

    align-items: center;

    gap: 7px;

    margin-top: 14px;

    padding:
        7px
        12px;

    border:
        1px solid
        rgba(255,88,113,.24);

    border-radius: 999px;

    background:
        rgba(255,88,113,.075);

    color: #ffc1ca;

    font-size: 8px;

    font-weight: 900;

    letter-spacing: 1.45px;

    text-transform: uppercase;
}

.statusDot {

    width: 6px;

    height: 6px;

    border-radius: 50%;

    background:
        var(--danger);

    box-shadow:
        0 0 10px
        var(--danger);

    animation:
        statusPulse
        1.3s
        ease-in-out
        infinite;
}

/* --------------------------------------------------------- */
/* INFO GRID                                                 */
/* --------------------------------------------------------- */

.sectionTitle {

    margin:
        16px
        1px
        8px;

    color: #697486;

    font-size: 7px;

    font-weight: 900;

    letter-spacing: 2px;

    text-transform: uppercase;
}

.grid {

    display: grid;

    grid-template-columns:
        1fr
        1fr;

    gap: 8px;
}

.info {

    position: relative;

    min-width: 0;

    padding:
        11px
        11px
        10px;

    overflow: hidden;

    border:
        1px solid
        var(--border);

    border-radius: 15px;

    background:
        linear-gradient(
            145deg,
            rgba(255,255,255,.042),
            rgba(255,255,255,.018)
        );
}

.info::before {

    content: "";

    position: absolute;

    width: 40px;

    height: 40px;

    right: -18px;

    top: -18px;

    border-radius: 50%;

    background:
        rgba(231,189,99,.07);
}

.info label {

    position: relative;

    z-index: 1;

    display: block;

    margin-bottom: 5px;

    color: #667184;

    font-size: 7px;

    font-weight: 850;

    letter-spacing: 1.15px;

    text-transform: uppercase;
}

.info strong {

    position: relative;

    z-index: 1;

    display: block;

    overflow: hidden;

    color: #e9edf2;

    font-size: 10px;

    font-weight: 750;

    letter-spacing: .1px;

    text-overflow: ellipsis;

    white-space: nowrap;
}

/* --------------------------------------------------------- */
/* NOTICE                                                    */
/* --------------------------------------------------------- */

.notice {

    display: flex;

    align-items: flex-start;

    gap: 11px;

    margin-top: 11px;

    padding:
        12px;

    border:
        1px solid
        rgba(231,189,99,.12);

    border-radius: 15px;

    background:
        linear-gradient(
            135deg,
            rgba(231,189,99,.085),
            rgba(231,189,99,.025)
        );
}

.noticeIcon {

    width: 30px;

    height: 30px;

    min-width: 30px;

    display: grid;

    place-items: center;

    border-radius: 10px;

    background:
        rgba(231,189,99,.075);
}

.noticeIcon svg {

    width: 17px;

    height: 17px;

    fill: none;

    stroke: var(--gold);

    stroke-width: 1.8;

    stroke-linecap: round;

    stroke-linejoin: round;
}

.noticeText {

    color: #aab3c1;

    font-size: 9px;

    line-height: 1.55;
}

.noticeText b {
    color: #d8dee8;
}

/* --------------------------------------------------------- */
/* BUTTON                                                    */
/* --------------------------------------------------------- */

.actions {
    margin-top: 13px;
}

.closeButton {

    position: relative;

    width: 100%;

    height: 50px;

    overflow: hidden;

    border: 0;

    border-radius: 16px;

    outline: none;

    background:
        linear-gradient(
            105deg,
            #9e7027,
            #d9ad55 45%,
            #f2d681
        );

    color: #090b0e;

    font-size: 10px;

    font-weight: 950;

    letter-spacing: 1.45px;

    text-transform: uppercase;

    box-shadow:
        0 12px 32px
        rgba(231,189,99,.17);
}

.closeButton::before {

    content: "";

    position: absolute;

    width: 60px;

    height: 160%;

    top: -30%;

    left: -100px;

    transform:
        rotate(20deg);

    background:
        linear-gradient(
            90deg,
            transparent,
            rgba(255,255,255,.38),
            transparent
        );

    animation:
        shine
        3.3s
        ease-in-out
        infinite;
}

.closeButton:active {

    transform:
        scale(.985);
}

/* --------------------------------------------------------- */
/* TIMER                                                     */
/* --------------------------------------------------------- */

.timerRow {

    display: flex;

    align-items: center;

    justify-content: center;

    gap: 5px;

    margin-top: 11px;

    color: #667183;

    font-size: 8px;

    letter-spacing: .45px;
}

.timerValue {

    min-width: 16px;

    color: var(--gold);

    font-weight: 900;
}

.progress {

    position: relative;

    width: 100%;

    height: 3px;

    margin-top: 9px;

    overflow: hidden;

    border-radius: 99px;

    background:
        rgba(255,255,255,.065);
}

.progressFill {

    width: 100%;

    height: 100%;

    transform-origin: left;

    border-radius: inherit;

    background:
        linear-gradient(
            90deg,
            var(--danger),
            #dc7e65,
            var(--gold)
        );

    box-shadow:
        0 0 10px
        rgba(231,189,99,.26);
}

.footer {

    margin-top: 11px;

    color: #414a57;

    text-align: center;

    font-size: 7px;

    font-weight: 750;

    letter-spacing: 1.35px;

    text-transform: uppercase;
}

/* --------------------------------------------------------- */
/* ANIMATION                                                 */
/* --------------------------------------------------------- */

@keyframes cardEnter {

    0% {
        opacity: 0;

        transform:
            translateY(24px)
            scale(.94);
    }

    70% {
        opacity: 1;

        transform:
            translateY(-2px)
            scale(1.005);
    }

    100% {
        opacity: 1;

        transform:
            translateY(0)
            scale(1);
    }
}

@keyframes rotateGlow {

    from {
        transform:
            rotate(0deg);
    }

    to {
        transform:
            rotate(360deg);
    }
}

@keyframes pulse {

    0%,
    100% {
        transform:
            scale(1);
    }

    50% {
        transform:
            scale(1.045);

        box-shadow:
            0 0 0 12px rgba(255,88,113,.018),
            0 0 43px rgba(255,88,113,.11);
    }
}

@keyframes statusPulse {

    0%,
    100% {
        opacity: 1;
    }

    50% {
        opacity: .45;
    }
}

@keyframes shine {

    0%,
    60% {
        left: -100px;
    }

    100% {
        left: calc(100% + 80px);
    }
}

/* Small displays */

@media (max-height: 560px) {

    .inner {
        padding: 14px;
    }

    .hero {
        margin-top: 12px;
        padding: 14px 10px;
    }

    .lockOuter {
        width: 58px;
        height: 58px;
        margin-bottom: 9px;
    }

    .lockInner {
        width: 44px;
        height: 44px;
    }

    .heroTitle {
        font-size: 19px;
    }

    .heroText {
        font-size: 9px;
        line-height: 1.45;
    }

    .sectionTitle {
        margin-top: 11px;
    }

    .notice {
        padding: 9px;
    }

    .closeButton {
        height: 44px;
    }
}

</style>

</head>

<body>

<div class="shell">

<main
    class="card"
    role="dialog"
    aria-label="License expired"
>

<div class="inner">

    <header class="top">

        <div class="identity">

            <div class="logo">

                <div class="logoInner">

                    <div class="logoMark">
                        P
                    </div>

                </div>

            </div>

            <div class="copy">

                <div class="eyebrow">
                    Parallax / OneCore
                </div>

                <div class="brand">
                    Runtime Security
                </div>

            </div>

        </div>

        <div class="secure">

            <span class="secureDot"></span>

            SECURE

        </div>

    </header>


    <section class="hero">

        <div class="lockOuter">

            <div class="lockInner">

                <svg
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                >

                    <rect
                        x="5"
                        y="10"
                        width="14"
                        height="11"
                        rx="3"
                    />

                    <path
                        d="
                            M8 10V7
                            a4 4 0 0 1 8 0
                            v3
                        "
                    />

                    <path
                        d="M12 14v3"
                    />

                </svg>

            </div>

        </div>


        <h1 class="heroTitle">
            License expired
        </h1>


        <p class="heroText">

            This protected runtime session
            is no longer authorized.

            Renew your activation key
            before continuing.

        </p>


        <div class="status">

            <span class="statusDot"></span>

            Access revoked

        </div>

    </section>


    <div class="sectionTitle">
        Runtime environment
    </div>


    <section class="grid">

        <div class="info">

            <label>
                Device
            </label>

            <strong id="device">
                Detecting...
            </strong>

        </div>


        <div class="info">

            <label>
                Android
            </label>

            <strong id="android">
                Detecting...
            </strong>

        </div>


        <div class="info">

            <label>
                Runtime API
            </label>

            <strong id="api">
                Detecting...
            </strong>

        </div>


        <div class="info">

            <label>
                Architecture
            </label>

            <strong id="abi">
                Detecting...
            </strong>

        </div>

    </section>


    <section class="notice">

        <div class="noticeIcon">

            <svg viewBox="0 0 24 24">

                <path
                    d="
                        M12 3
                        l7 3
                        v5
                        c0 4.6-2.9 8-7 10
                        -4.1-2
                        -7-5.4
                        -7-10
                        V6
                        l7-3z
                    "
                />

                <path
                    d="M9 12l2 2 4-4"
                />

            </svg>

        </div>


        <div class="noticeText">

            <b>
                Parallax Core
            </b>

            supports Android API 24–36.

            Contact your authorized
            provider to renew access.

        </div>

    </section>


    <section class="actions">

        <button
            class="closeButton"
            type="button"
            onclick="closeSecurely()"
        >

            Close securely

        </button>


        <div class="timerRow">

            Closing automatically in

            <span
                id="timer"
                class="timerValue"
            >
                --
            </span>

            seconds

        </div>


        <div class="progress">

            <div
                id="progressFill"
                class="progressFill"
            ></div>

        </div>


        <div class="footer">
            Parallax protected runtime
        </div>

    </section>

</div>

</main>

</div>


<script>

(function () {

    "use strict";

    var closed = false;

    function safeAndroidCall(
        callback
    ) {

        try {

            if (
                typeof Android !== "undefined"
            ) {

                callback(Android);
            }

        } catch (ignore) {
        }
    }


    function setText(
        id,
        value
    ) {

        try {

            var element =
                document.getElementById(id);

            if (!element) {
                return;
            }

            if (
                value === null ||
                value === undefined ||
                value === ""
            ) {

                value = "Unknown";
            }

            element.textContent =
                String(value);

        } catch (ignore) {
        }
    }


    function loadDeviceInfo() {

        safeAndroidCall(
            function (bridge) {

                try {

                    var raw =
                        bridge.getDeviceInfo();

                    var info =
                        JSON.parse(raw);

                    setText(
                        "device",
                        info.device
                    );

                    setText(
                        "android",
                        "Android " +
                        String(
                            info.android || "Unknown"
                        )
                    );

                    setText(
                        "api",
                        "API " +
                        String(
                            info.api || "Unknown"
                        )
                    );

                    setText(
                        "abi",
                        info.abi
                    );

                } catch (ignore) {
                }
            }
        );
    }


    function closeSecurely() {

        if (closed) {
            return;
        }

        closed = true;

        var card =
            document.querySelector(
                ".card"
            );

        if (card) {

            card.style.transition =
                "opacity .18s ease, transform .18s ease";

            card.style.opacity =
                "0";

            card.style.transform =
                "scale(.96)";
        }

        setTimeout(
            function () {

                safeAndroidCall(
                    function (bridge) {
                        bridge.close();
                    }
                );

            },
            170
        );
    }


    window.closeSecurely =
        closeSecurely;


    var duration =
        12;

    safeAndroidCall(
        function (bridge) {

            try {

                var customDuration =
                    Number(
                        bridge
                            .getAutoCloseSeconds()
                    );

                if (
                    isFinite(customDuration) &&
                    customDuration > 0
                ) {

                    duration =
                        customDuration;
                }

            } catch (ignore) {
            }
        }
    );


    var remaining =
        duration;

    var timer =
        document.getElementById(
            "timer"
        );

    var progress =
        document.getElementById(
            "progressFill"
        );


    if (timer) {
        timer.textContent =
            String(remaining);
    }


    if (progress) {

        progress.style.transition =
            "transform " +
            String(duration) +
            "s linear";

        progress.style.transform =
            "scaleX(1)";

        requestAnimationFrame(
            function () {

                requestAnimationFrame(
                    function () {

                        progress.style.transform =
                            "scaleX(0)";
                    }
                );
            }
        );
    }


    loadDeviceInfo();


    var interval =
        setInterval(
            function () {

                remaining -= 1;

                if (remaining < 0) {
                    remaining = 0;
                }

                if (timer) {
                    timer.textContent =
                        String(remaining);
                }

                if (remaining <= 0) {

                    clearInterval(
                        interval
                    );

                    closeSecurely();
                }

            },
            1000
        );


})();

</script>

</body>

</html>
"""
}
