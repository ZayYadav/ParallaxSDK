package android.MetaCore

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.lang.reflect.Field
import kotlin.math.min

object AdvancedPopupHelper {

    private val handler = Handler(Looper.getMainLooper())

    private fun getTopActivity(): Activity? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val activitiesField: Field = activityThreadClass.getDeclaredField("mActivities")
            activitiesField.isAccessible = true
            val activities = activitiesField.get(activityThread) as Map<*, *>

            for (record in activities.values) {
                val activityRecord = record ?: continue
                val recordClass = activityRecord::class.java
                val pausedField = recordClass.getDeclaredField("paused")
                pausedField.isAccessible = true
                if (!pausedField.getBoolean(activityRecord)) {
                    val activityField = recordClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    return activityField.get(activityRecord) as? Activity
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun showAuto() {
        val activity = getTopActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        showPopup(activity)
    }

    private fun showPopup(activity: Activity) {
        handler.post {
            try {
                val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
                dialog.setCancelable(false)

                val webView = WebView(activity)
                webView.setBackgroundColor(Color.TRANSPARENT)
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                webView.isLongClickable = false
                webView.setOnLongClickListener { true }
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowContentAccess = false
                    allowFileAccess = true
                    blockNetworkLoads = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                }
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
                }

                val deviceInfo = JSONObject().apply {
                    put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                    put("android", Build.VERSION.RELEASE ?: "Unknown")
                    put("api", Build.VERSION.SDK_INT)
                    put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown")
                }.toString()

                webView.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun close() {
                        handler.post {
                            if (dialog.isShowing) dialog.dismiss()
                        }
                    }

                    @JavascriptInterface
                    fun getDeviceInfo(): String = deviceInfo
                }, "Android")

                webView.loadDataWithBaseURL(
                    "file:///android_res/drawable/",
                    HTML,
                    "text/html",
                    "utf-8",
                    null
                )

                dialog.setContentView(webView)
                dialog.setOnDismissListener {
                    webView.removeJavascriptInterface("Android")
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                }
                dialog.show()

                val metrics = activity.resources.displayMetrics
                dialog.window?.apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    setLayout(
                        min(dp(activity, 380), metrics.widthPixels - dp(activity, 24)),
                        min(dp(activity, 580), metrics.heightPixels - dp(activity, 32))
                    )
                    setGravity(Gravity.CENTER)
                    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    setDimAmount(0.78f)
                }
            } catch (_: Throwable) {
                // Expiry handling must never crash the host application.
            }
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }

    private const val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
:root{color-scheme:dark;--gold:#d5a94f;--paper:#eef1f5;--muted:#97a1b1;--danger:#ff667a;--surface:#111722}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{width:100%;height:100%;margin:0;overflow:hidden}
body{display:flex;align-items:center;justify-content:center;padding:10px;background:transparent;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:var(--paper)}
.card{position:relative;width:100%;max-width:352px;padding:18px;overflow:hidden;border:1px solid rgba(213,169,79,.45);border-radius:28px;background:linear-gradient(145deg,rgba(24,30,41,.98),rgba(5,7,11,.99));box-shadow:0 26px 70px rgba(0,0,0,.7),0 0 40px rgba(213,169,79,.09);animation:enter .55s cubic-bezier(.2,.8,.2,1) both}
.card:before{content:'';position:absolute;width:230px;height:230px;left:50%;top:-150px;transform:translateX(-50%);border-radius:50%;background:rgba(213,169,79,.18);filter:blur(52px);pointer-events:none}
.top{position:relative;display:flex;align-items:center;gap:12px}
.logo{width:58px;height:58px;object-fit:cover;border-radius:16px;border:1px solid rgba(213,169,79,.5);box-shadow:0 8px 28px rgba(0,0,0,.45)}
.eyebrow{font-size:9px;line-height:1.4;font-weight:800;letter-spacing:2px;color:var(--gold)}
.brand{margin-top:3px;font-size:18px;font-weight:850;letter-spacing:.4px}
.hero{position:relative;margin-top:18px;padding:18px 14px;text-align:center;border:1px solid rgba(255,102,122,.22);border-radius:20px;background:linear-gradient(135deg,rgba(255,102,122,.12),rgba(255,255,255,.025))}
.lock{position:relative;width:58px;height:58px;margin:0 auto 12px;display:grid;place-items:center;border-radius:50%;background:rgba(255,102,122,.12);box-shadow:0 0 0 8px rgba(255,102,122,.035);animation:pulse 1.8s ease-in-out infinite}
.lock svg{width:27px;height:27px;fill:none;stroke:var(--danger);stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.hero h1{margin:0;font-size:21px;letter-spacing:.4px}
.hero p{margin:7px auto 0;max-width:260px;color:var(--muted);font-size:11px;line-height:1.55}
.status{display:inline-flex;align-items:center;gap:7px;margin-top:12px;padding:7px 11px;border:1px solid rgba(255,102,122,.24);border-radius:999px;color:#ffd5dc;background:rgba(255,102,122,.08);font-size:9px;font-weight:800;letter-spacing:1px}
.dot{width:6px;height:6px;border-radius:50%;background:var(--danger);box-shadow:0 0 9px var(--danger)}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px}
.info{padding:10px;border:1px solid rgba(255,255,255,.08);border-radius:14px;background:rgba(255,255,255,.035);min-width:0}
.info label{display:block;margin-bottom:4px;color:#6f7a8c;font-size:8px;font-weight:800;letter-spacing:1px;text-transform:uppercase}
.info strong{display:block;overflow:hidden;color:#e8ebf0;font-size:10px;text-overflow:ellipsis;white-space:nowrap}
.notice{display:flex;align-items:center;gap:10px;margin-top:12px;padding:11px 12px;border-radius:14px;background:rgba(213,169,79,.08);color:#c8d0dc;font-size:10px;line-height:1.45}
.notice svg{width:20px;min-width:20px;fill:none;stroke:var(--gold);stroke-width:1.8}
.actions{margin-top:14px}
button{width:100%;height:48px;border:0;border-radius:15px;background:linear-gradient(90deg,#b98a35,#efd27c);color:#090b0f;font-size:11px;font-weight:900;letter-spacing:1.2px;text-transform:uppercase;box-shadow:0 10px 28px rgba(213,169,79,.18)}
.timer{margin-top:10px;color:#6f798a;text-align:center;font-size:9px;letter-spacing:.5px}
.timer b{color:var(--gold)}
.bar{height:3px;margin-top:8px;overflow:hidden;border-radius:99px;background:rgba(255,255,255,.08)}
.bar span{display:block;width:100%;height:100%;transform-origin:left;background:linear-gradient(90deg,var(--danger),var(--gold));animation:drain 10s linear forwards}
@keyframes enter{from{opacity:0;transform:translateY(20px) scale(.96)}to{opacity:1;transform:none}}
@keyframes pulse{50%{transform:scale(1.06);box-shadow:0 0 0 12px rgba(255,102,122,.025)}}
@keyframes drain{to{transform:scaleX(0)}}
</style>
</head>
<body>
<main class="card">
  <header class="top">
<div><div class="eyebrow">KESHAVXOWNER / ONECORE</div><div class="brand">Runtime access</div></div>
  </header>
  <section class="hero">
    <div class="lock">
      <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3"/></svg>
    </div>
    <h1>License expired</h1>
    <p>This secure runtime session is no longer authorized. Renew the activation key before continuing.</p>
    <div class="status"><span class="dot"></span> ACCESS REVOKED</div>
  </section>
  <section class="grid">
    <div class="info"><label>Device</label><strong id="device">Loading...</strong></div>
    <div class="info"><label>Android</label><strong id="android">Loading...</strong></div>
    <div class="info"><label>Runtime API</label><strong id="api">Loading...</strong></div>
    <div class="info"><label>Architecture</label><strong id="abi">Loading...</strong></div>
  </section>
  <div class="notice">
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l7 3v5c0 4.6-2.9 8-7 10-4.1-2-7-5.4-7-10V6l7-3z"/><path d="M9 12l2 2 4-4"/></svg>
    <span>KESHAVXOWNER Core supports API 24-36. Contact your authorized provider to renew access.</span>
  </div>
  <div class="actions">
    <button type="button" onclick="Android.close()">Close securely</button>
    <div class="timer">Closing automatically in <b id="timer">10</b>s</div>
    <div class="bar"><span></span></div>
  </div>
</main>
<script>
(function(){
  try{
    var info=JSON.parse(Android.getDeviceInfo());
    ['device','android','api','abi'].forEach(function(key){
      document.getElementById(key).textContent=String(info[key]||'Unknown');
    });
  }catch(ignore){}
  var seconds=10;
  var timer=document.getElementById('timer');
  var interval=setInterval(function(){
    seconds-=1;
    timer.textContent=String(Math.max(0,seconds));
    if(seconds<=0){clearInterval(interval);Android.close();}
  },1000);
})();
</script>
</body>
</html>
"""
}
