package android.MetaCore

import android.MetaCore.IRemoteManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.RemoteException
import androidx.core.app.NotificationCompat
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import java.io.File
import top.niunaijun.blackbox.core.env.BEnvironment
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RemoteManager private constructor() : IRemoteManager.Stub() {

    companion object {
    
         @JvmField
         val JUNIT_JAR = File(BEnvironment.getCacheDir(), "junit.apk")
         
         @JvmField
         val EMPTY_JAR = File(BEnvironment.getCacheDir(), "empty.apk")
        
        private const val TAG = "MetaActivationManager"
        private const val CT = 45000
        private const val RT = 60000
        private const val MAX_RETRIES = 3
        private val exe: ExecutorService = Executors.newSingleThreadExecutor()

        @Volatile
        private var instance: RemoteManager? = null

        @JvmField
        @Volatile
        var sEnableDaemonService: Boolean = true

        @JvmField
        @Volatile
        var sHideRoot: Boolean = true

        @JvmField
        @Volatile
        var sHideXposed: Boolean = true

        @JvmStatic
        fun getInstance(): RemoteManager {
            return instance ?: synchronized(this) {
                instance ?: RemoteManager().also { instance = it }
            }
        }
    }

    private fun iv(u: String?): Boolean {
        return u != null && u.startsWith("https://") && !u.contains(" ") && !u.contains("\"")
    }

    override fun activateSdk(userkey: String?) {
        val bc: String
        try {
            val nc = Class.forName("android.MetaCore.nk")
            val m = nc.getMethod("getUrlHidden")
            bc = m.invoke(null) as String
        } catch (e: Exception) {
            e.printStackTrace()
            nk.Msg = "Error: Failed to get API URL - ${e.message}"
            return
        }

        if (!iv(bc)) {
            nk.Msg = "Error: Invalid API URL format"
            return
        }

        if (userkey == null || userkey.trim().isEmpty()) {
            nk.Msg = "Error: User key cannot be empty"
            return
        }

        exe.execute {
            var retryCount = 0
            var success = false
            var lastError: String? = null
            
            while (retryCount <= MAX_RETRIES && !success) {
                var conn: HttpURLConnection? = null
                try {
                    val ctx = BlackBoxCore.getContext()
                    val pkg = BlackBoxCore.getHostPkg() ?: ""

                    if (pkg.isEmpty()) {
                        nk.Msg = "Error: Package name not found"
                        success = true
                        return@execute
                    }

                    val appName = getAppName(ctx, pkg)
                    val deviceId = deviceId()
                    
                    // Create POST data
                    val pd = ("user_key=" + URLEncoder.encode(userkey, "UTF-8") + 
                    "&package_name=" + URLEncoder.encode(pkg, "UTF-8") + 
                    "&app_name=" + URLEncoder.encode(appName, "UTF-8") + 
                    "&device_id=" + URLEncoder.encode(deviceId, "UTF-8"))
                    
                    val pdb = pd.toByteArray(StandardCharsets.UTF_8)
                    
                    // Create URL connection
                    conn = URL(bc).openConnection() as HttpURLConnection
                    
                    // Apply timeout settings
                    conn.connectTimeout = CT
                    conn.readTimeout = RT
                    
                    // Optimize connection settings
                    conn.instanceFollowRedirects = false
                    conn.useCaches = false
                    conn.setRequestProperty("Connection", "close")
                    conn.setRequestProperty("Accept-Encoding", "identity")
                    conn.setRequestProperty("User-Agent", "MetaSDK/1.0")
                    
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.setRequestProperty("Content-Length", pdb.size.toString())
                    conn.doOutput = true

                    // Write data
                    conn.outputStream.use { os -> 
                        os.write(pdb)
                        os.flush()
                    }

                    val rc = conn.responseCode
                    
                    if (rc != HttpURLConnection.HTTP_OK) {
                        lastError = "Server Error: Http $rc - ${conn.responseMessage}"
                        nk.setHidden("Offline")
                        nk.Msg = lastError
                        
                        // Don't retry on specific errors
                        if (rc == 404 || rc == 403 || rc == 400) {
                            showNotificationSafe("SDK ACTIVATE FAILED", "SERVER ERROR: $rc")
                            success = true
                            return@execute
                        }
                        
                        // For server errors, retry
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            nk.Msg = "Server Error: Retrying... ($retryCount/${MAX_RETRIES})"
                            Thread.sleep(3000)
                            continue
                        } else {
                            showNotificationSafe("SDK ACTIVATE FAILED", "SERVER ERROR: $rc")
                            success = true
                            return@execute
                        }
                    }

                    // Read response
                    val res = StringBuilder()
                    
                    try {
                        val inputStream = conn.inputStream
                        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 8192).use { br ->
                            var line: String?
                            while (br.readLine().also { line = it } != null) {
                                res.append(line)
                            }
                        }
                    } catch (ste: SocketTimeoutException) {
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            nk.Msg = "Read Timeout: Retrying... ($retryCount/${MAX_RETRIES})"
                            Thread.sleep(3000)
                            continue
                        } else {
                            throw ste
                        }
                    }

                    if (res.isEmpty()) {
                        lastError = "Empty response from server"
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            nk.Msg = "Empty Response: Retrying... ($retryCount/${MAX_RETRIES})"
                            Thread.sleep(3000)
                            continue
                        } else {
                            nk.setHidden("Offline")
                            nk.Msg = "Error: Empty response from server"
                            showNotificationSafe("SDK ACTIVATE FAILED", "EMPTY RESPONSE")
                            success = true
                            return@execute
                        }
                    }

                    val data = JSONObject(res.toString())

                    // ===== CHECK SERVER MODE FIRST =====
                    val serverMode = data.optString("server_mode", "online")
                    val serverMessage = data.optString("message", data.optString("reason", ""))
                    
                    if (serverMode.equals("maintenance", true)) {
                        // MAINTENANCE MODE
                        nk.setHidden("Offline")
                        nk.Msg = "SERVER MAINTENANCE: ${if (serverMessage.isNotEmpty()) serverMessage else "Server under maintenance"}"
                        
                        // Save maintenance state
                        val sp = ctx.getSharedPreferences("server_status", Context.MODE_PRIVATE)
                        sp.edit().apply {
                            putString("server_mode", "maintenance")
                            putString("message", serverMessage)
                            apply()
                        }
                        
                        // Show maintenance notification
                        showServerNotification("🛠️ MAINTENANCE MODE", if (serverMessage.isNotEmpty()) serverMessage else "Server under maintenance", "warning")
                        
                        // Disable all features during maintenance
                        sEnableDaemonService = false
                        sHideRoot = false
                        sHideXposed = false
                        success = true
                        return@execute
                    }
                    
                    if (serverMode.equals("offline", true)) {
                        // OFFLINE MODE
                        nk.setHidden("Offline")
                        nk.Msg = "SERVER OFFLINE: ${if (serverMessage.isNotEmpty()) serverMessage else "Server is offline"}"
                        
                        // Save offline state
                        val sp = ctx.getSharedPreferences("server_status", Context.MODE_PRIVATE)
                        sp.edit().apply {
                            putString("server_mode", "offline")
                            putString("message", serverMessage)
                            apply()
                        }
                        
                        // Show offline notification
                        showServerNotification("🔴 OFFLINE MODE", if (serverMessage.isNotEmpty()) serverMessage else "Server is offline", "error")
                        
                        // Disable all features when offline
                        sEnableDaemonService = false
                        sHideRoot = false
                        sHideXposed = false
                        success = true
                        return@execute
                    }

                    // ===== PROCEED WITH NORMAL LICENSE CHECK =====
                    if (data.getString("status") == "success") {
                        nk.setHidden("online")
                        nk.Msg = "✅ Sdk Activated Successfully"

                        val exp = data.optString("expiry", "")
                        val toggleExpiryValue = data.optInt("toggle_expiry", 0)
                        val feature1 = data.optInt("feature1", 0)
                        val feature2 = data.optInt("feature2", 0)

                        val sp = BlackBoxCore.getContext().getSharedPreferences(nk.PREFERENCE_NAME, Context.MODE_PRIVATE)
                        
                        // ✅ IMPORTANT: EXPIRY CHECK AND SAVE
                        if (exp.isNotEmpty()) {
                            try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                val expiryDate = sdf.parse(exp)
                                
                                if (expiryDate != null) {
                                    val currentTime = System.currentTimeMillis()
                                    val expiryTime = expiryDate.time
                                    
                                    if (currentTime < expiryTime) {
                                        // ✅ Licence valid - save karo
                                        sp.edit().apply {
                                            putBoolean("activated", true)
                                            putString("expiry", exp)
                                            putInt("toggle_expiry", toggleExpiryValue)
                                            putInt("toggle_feature1", feature1)
                                            putInt("toggle_feature2", feature2)
                                            apply()
                                        }
                                        
                                        val remainingDays = (expiryTime - currentTime) / (1000 * 60 * 60 * 24)
                                        nk.Msg = "✅ Licence Valid (${remainingDays} days remaining)"
                                        
                                    } else {
                                        // ❌ Licence already expired
                                        nk.Msg = "❌ Licence Expired on $exp"
                                        sp.edit().putBoolean("activated", false).apply()
                                        success = true
                                        return@execute
                                    }
                                } else {
                                    // Invalid date format
                                    sp.edit().apply {
                                        putBoolean("activated", true)
                                        putString("expiry", exp)
                                        apply()
                                    }
                                }
                            } catch (e: Exception) {
                                // Date parse error
                                sp.edit().apply {
                                    putBoolean("activated", true)
                                    putString("expiry", exp)
                                    apply()
                                }
                                nk.Msg = "Licence saved (date parse error)"
                            }
                        } else {
                            // No expiry date
                            sp.edit().apply {
                                putBoolean("activated", true)
                                putString("expiry", "")
                                putInt("toggle_expiry", toggleExpiryValue)
                                putInt("toggle_feature1", feature1)
                                putInt("toggle_feature2", feature2)
                                apply()
                            }
                            nk.Msg = "✅ Licence Activated (No expiry)"
                        }

                        if (toggleExpiryValue == 1 && exp.isNotEmpty()) {
                            showNotificationSafe("✅ SDK ACTIVATED", "Expiry: $exp")
                        }

                        if (feature1 == 1) isDaemon(true) else isDaemon(false)
                        if (feature2 == 1) ishideRoot(true) else ishideRoot(false)

                        // Server notification
                        if (data.has("server_notification")) {
                            val sn = data.getJSONObject("server_notification")
                            if (sn.optInt("enabled", 0) == 1) {
                                showServerNotification(
                                    sn.optString("title", "Event"),
                                    sn.optString("message", ""),
                                    sn.optString("iconType", "event")
                                )
                            }
                        }

                        // Extra image notification
                        if (data.has("extra_notification")) {
                            val ex = data.getJSONObject("extra_notification")
                            if (ex.optInt("enabled", 0) == 1) {
                                showImageNotification(
                                    ex.optString("title", ""),
                                    ex.optString("message", ""),
                                    ex.optString("image", ""),
                                    ex.optString("notfiy_base_url")
                                )
                            }
                        }

                    } else {
                        nk.setHidden("Offline")
                        val reason = data.optString("reason", "Unknown error")
                        nk.Msg = "❌ ACTIVATE Failed: $reason"

                        val toggleExpiryValue = data.optInt("toggle_expiry", 0)
                        val feature1 = data.optInt("feature1", 0)
                        val feature2 = data.optInt("feature2", 0)

                        if (toggleExpiryValue == 1) {
                            showNotificationSafe("SDK ACTIVATE", "FAILED")
                        }

                        if (feature1 == 1) isDaemon(true) else isDaemon(false)
                        if (feature2 == 1) ishideRoot(true) else ishideRoot(false)

                        if (data.has("server_notification")) {
                            val sn = data.getJSONObject("server_notification")
                            if (sn.optInt("enabled", 0) == 1) {
                                showServerNotification(
                                    sn.optString("title", "Event"),
                                    sn.optString("message", ""),
                                    sn.optString("iconType", "event")
                                )
                            }
                        }

                        if (data.has("extra_notification")) {
                            val ex = data.getJSONObject("extra_notification")
                            if (ex.optInt("enabled", 0) == 1) {
                                showImageNotification(
                                    ex.optString("title", ""),
                                    ex.optString("message", ""),
                                    ex.optString("image", ""),
                                    ex.optString("base_url")
                                )
                            }
                        }
                    }
                    
                    success = true

                } catch (ste: SocketTimeoutException) {
                    lastError = "Connection timeout"
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        nk.Msg = "Timeout: Retrying... ($retryCount/${MAX_RETRIES})"
                        Thread.sleep(3000)
                        continue
                    } else {
                        nk.setHidden("Offline")
                        nk.Msg = "Error: Connection timeout"
                        showNotificationSafe("SDK ACTIVATE FAILED", "CONNECTION TIMEOUT")
                        success = true
                        return@execute
                    }
                } catch (e: java.net.ConnectException) {
                    lastError = "Cannot connect to server"
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        nk.Msg = "Connect Error: Retrying... ($retryCount/${MAX_RETRIES})"
                        Thread.sleep(3000)
                        continue
                    } else {
                        nk.setHidden("Offline")
                        nk.Msg = "Error: Cannot connect to server"
                        showNotificationSafe("SDK ACTIVATE FAILED", "NO CONNECTION")
                        success = true
                        return@execute
                    }
                } catch (e: java.net.UnknownHostException) {
                    lastError = "Invalid server URL"
                    nk.setHidden("Offline")
                    nk.Msg = "Error: Invalid server URL"
                    showNotificationSafe("SDK ACTIVATE FAILED", "INVALID URL")
                    success = true
                    return@execute
                } catch (e: Exception) {
                    lastError = e.message ?: "Unknown error"
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        nk.Msg = "Error: Retrying... ($retryCount/${MAX_RETRIES})"
                        Thread.sleep(3000)
                        continue
                    } else {
                        nk.setHidden("Offline")
                        nk.Msg = "Unexpected Error: ${e.message}"
                        showNotificationSafe("SDK ACTIVATE FAILED", "ERROR")
                        success = true
                        return@execute
                    }
                } finally {
                    try {
                        conn?.disconnect()
                    } catch (e: Exception) {
                        // Ignore disconnect errors
                    }
                }
            }
            
            // If we exit loop without success
            if (!success) {
                nk.setHidden("Offline")
                val finalError = lastError ?: "Unknown error"
                nk.Msg = "Failed after $MAX_RETRIES attempts: $finalError"
                showNotificationSafe("SDK ACTIVATE FAILED", "MAX RETRIES EXCEEDED")
            }
        }
    }

    override fun getActivatedSdk(): Boolean {
        return try {
            val result = nk.getActivatedSdk()
            nk.Msg = if (result) "✅ SDK IS ACTIVATED" else "❌ SDK IS NOT ACTIVATED"
            result
        } catch (e: Exception) {
            nk.Msg = "ERROR: FAILED TO GET ACTIVATE STATUS"
            false
        }
    }

    override fun getServerMessage(): String {
        return try {
            val msg = nk.getServerMessage()
            if (msg.isNullOrEmpty()) "No server message" else msg
        } catch (e: Exception) {
            "Error: Failed to get server message"
        }
    }

    override fun getNetwork(): Boolean {
        return try {
            val net = nk.isSystemApp()
            nk.Msg = if (net) "✅ Network: Connected" else "❌ Network: Disconnected"
            net
        } catch (e: Exception) {
            nk.Msg = "Error: Failed to check network status"
            false
        }
    }

    private fun deviceId(): String {
        return try {
            val ctx = BlackBoxCore.getContext()
            android.provider.Settings.Secure.getString(ctx.contentResolver,android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getAppName(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun isDaemon(d: Boolean) {
        if (d) {
            nk.Msg = "Daemon: ENABLED"
            sEnableDaemonService = true
        } else {
            nk.Msg = "Daemon: DISABLED"
            sEnableDaemonService = false
        }
    }

    private fun ishideRoot(h: Boolean) {
        if (h) {
            nk.Msg = "Root Hide: ENABLED"
            sHideRoot = true
        } else {
            nk.Msg = "Root Hide: DISABLED"
            sHideRoot = false
        }
    }

    // ---------------- Notification Helpers ----------------
    private fun showNotificationSafe(title: String, message: String) {
        try {
            val ctx = BlackBoxCore.getContext()
            showNotification(ctx, title, message)
        } catch (_: Throwable) { }
    }

    private val CHANNEL_ID = "meta_sdk_updates"
    private val CHANNEL_NAME = "Meta SDK Updates"

    private fun showNotification(ctx: Context, title: String, msg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID,CHANNEL_NAME,NotificationManager.IMPORTANCE_HIGH)
            ch.description = "SDK ACTIVATE OR UPDATE NOTIFICATIONS"
            ch.enableLights(true)
            ch.lightColor = Color.BLUE
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val nb = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        nm.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), nb.build())
    }

    // ================= NOTIFICATIONS =================
    private fun showServerNotification(title:String,msg:String,type:String){
        val ctx=BlackBoxCore.getContext()
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch="meta_server"
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel(ch,"SERVER",NotificationManager.IMPORTANCE_HIGH))

        val t=type.lowercase()
        val icon=when{
            t.contains("warn")||t.contains("alert")->android.R.drawable.stat_sys_warning
            t.contains("event")->android.R.drawable.star_big_on
            t.contains("update")->android.R.drawable.stat_sys_download_done
            else->android.R.drawable.ic_dialog_info
        }
        nm.notify(System.currentTimeMillis().toInt(),NotificationCompat.Builder(ctx,ch)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(msg)
            .setColor(Color.CYAN)
            .setAutoCancel(true)
            .build())
    }

    private fun showImageNotification(title:String,msg:String,img:String,base:String){
        exe.execute{
            try{
                if(img.isEmpty()) return@execute
                val url= if(base.isNotEmpty()) "$base/$img" else img
                val bmp=BitmapFactory.decodeStream(URL(url).openStream())
                val ctx=BlackBoxCore.getContext()
                val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val ch="meta_img"
                if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel(ch,"IMG",NotificationManager.IMPORTANCE_HIGH))
                nm.notify(System.currentTimeMillis().toInt(),NotificationCompat.Builder(ctx,ch)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setContentTitle(title)
                    .setContentText(msg)
                    .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp))
                    .setAutoCancel(true)
                    .build())
            }catch(_:Exception){}
        }
    }
}
