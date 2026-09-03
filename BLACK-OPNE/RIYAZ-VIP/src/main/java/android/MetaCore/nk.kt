package android.MetaCore

import android.content.Context
import android.os.Handler
import android.os.Looper
import top.niunaijun.blackbox.BlackBoxCore
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast

class nk {

    companion object {
        @Volatile
        private var is_False: Boolean = false

        @JvmField
        @Volatile
        var Msg: String = "Ready"

        const val PREFERENCE_NAME: String = "license_cache"
        var ActivationUrl: String = "https://parallaxloadersdk.parallaxserver.online/connect.php"

        @JvmStatic
        fun getActivatedSdk(): Boolean {
            // ✅ 1. Pehle server status check (GAH())
            val serverOnline = GAH()
            if (!serverOnline) {
                Msg = "Server Offline"
                return false
            }
            
            // ✅ 2. Activation status check
            val context = BlackBoxCore.getContext() ?: return false
            val sp = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            val isActivated = sp.getBoolean("activated", false)
            if (!isActivated) {
                Msg = "SDK Not Activated"
                return false
            }
            // ✅ 3. EXPIRY CHECK (MAIN FIX)
            val expiryStr = sp.getString("expiry", null)
            if (expiryStr == null || expiryStr.isEmpty()) {
                Msg = "No Expiry Date"
                return true  // No expiry = always valid
            }
            
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val expiryDate = sdf.parse(expiryStr)
                if (expiryDate == null) {
                    Msg = "Invalid Expiry Format"
                    return true
                }
                val currentTime = System.currentTimeMillis()
                val expiryTime = expiryDate.time
                if (currentTime < expiryTime) {
                    // ✅ Licence valid
                    val remainingDays = (expiryTime - currentTime) / (1000 * 60 * 60 * 24)
                    Msg = "Licence Valid (${remainingDays} days remaining)"
                    true
                } else {
                    // ❌ LICENCE EXPIRED
                    // Auto-deactivate
                    sp.edit().putBoolean("activated", false).apply()
                    Msg = "⚠️ LICENCE EXPIRED on $expiryStr"
                    false
                }
            } catch (e: Exception) {
                Msg = "Expiry Check Error"
                true  // Error case mein allow
            }
        }

        @JvmStatic
        fun getServerMessage(): String {
            return Msg
        }

        @JvmStatic
        fun ismsg(msg: String?) {
            if (msg == null) return
            val ctx = BlackBoxCore.getContext() ?: return
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                } catch (_: Exception) {}
            }
        }

        @JvmStatic
        fun setHidden(status: String?) {
            if (status == null) return
            try {
                val value = status.equals("online", ignoreCase = true)
                val clazz = Class.forName("android.MetaCore.nk")
                val field = clazz.getDeclaredField("is_False")
                field.isAccessible = true
                field.setBoolean(null, value)
                // ✅ SharedPreferences mein bhi save karo
                val ctx = BlackBoxCore.getContext()
                if (ctx != null) {
                    val sp = ctx.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                    sp.edit().apply {
                        putString("server_status", status)
                        apply()
                    }
                }
                // ✅ Message update karo
                Msg = if (value) {
                    "✅ Server Online"
                } else {
                    "❌ Server $status - Functions Blocked"
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        fun setHidden(value: Boolean) {
            setHidden(if (value) "online" else "offline")
        }

        @JvmStatic
        fun GAH(): Boolean {
            return try {
                val clazz = Class.forName("android.MetaCore.nk")
                val field = clazz.getDeclaredField("is_False")
                field.isAccessible = true
                field.get(null) as? Boolean ?: false
            } catch (_: Exception) {
                false
            }
        }

        @JvmStatic
        fun getUrlHidden(): String {
            return try {
                val clazz = Class.forName("android.MetaCore.nk")
                val field = clazz.getDeclaredField("ActivationUrl")
                field.isAccessible = true
                field.get(null) as? String ?: 获取接口地址()
            } catch (_: Exception) {
                获取接口地址()
            }
        }

        @JvmStatic
        fun 获取接口地址(): String {
            return "https://parallaxloadersdk.parallaxserver.online/connect.php"
        }
        
        @JvmStatic
        fun isSystemApp(): Boolean {
            // ✅ IMPORTANT: Ye method BPackageManager call karega
            // 1. Server status check
            if (!GAH()) {
                Msg = "❌ Server Offline - Functions Blocked"
                try {
                    AdvancedPopupHelper.showAuto()
                } catch (_: Exception) {}
                return false
            }
            // 2. Activation + Expiry check
            val isActivated = getActivatedSdk()
            if (!isActivated) {
                try {
                    AdvancedPopupHelper.showAuto()
                } catch (_: Exception) {}
                return false
            }
            // ✅ All checks passed
            Msg = "✅ Server Online & Licence Valid"
            return true
        }
        
        // ✅ Helper: Check expiry manually
        @JvmStatic
        fun checkExpiryManually(): String {
            val context = BlackBoxCore.getContext() ?: return "No context"
            val sp = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            val expiryStr = sp.getString("expiry", null)
            if (expiryStr == null) return "No expiry date"
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val expiryDate = sdf.parse(expiryStr)
                if (expiryDate == null) return "Invalid date"
                val currentTime = System.currentTimeMillis()
                val expiryTime = expiryDate.time
                if (currentTime < expiryTime) {
                    val remaining = expiryTime - currentTime
                    val days = remaining / (1000 * 60 * 60 * 24)
                    val hours = (remaining % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                    "Valid for ${days}d ${hours}h"
                } else {
                    "EXPIRED ${(currentTime - expiryTime) / (1000 * 60 * 60 * 24)} days ago"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
        
        // ✅ App start pe saved status load karo
        @JvmStatic
        fun loadSavedStatus() {
            try {
                val ctx = BlackBoxCore.getContext() ?: return
                val sp = ctx.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                // Server status load
                val savedStatus = sp.getString("server_status", "online")
                if (savedStatus != null) {
                    setHidden(savedStatus)
                }
                // Expiry check on app start
                getActivatedSdk()
            } catch (_: Exception) {}
        }
    }
}
