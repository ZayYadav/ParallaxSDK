package android.MetaCore

import android.content.Context
import android.os.Handler
import android.os.Looper
import top.niunaijun.blackbox.BlackBoxCore
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import org.lsposed.lsparanoid.Obfuscate
import top.niunaijun.blackbox.core.RNative

@Obfuscate
class nk {

    companion object {
        @Volatile
        private var is_False: Boolean = false

        @Volatile
        private var lastIdentityCheckElapsed: Long = 0L

        private const val IDENTITY_RECHECK_MS = 30_000L

        @JvmField
        @Volatile
        var Msg: String = "Ready"

        const val PREFERENCE_NAME: String = "license_cache"

        @JvmStatic
        fun getActivatedSdk(): Boolean {
            val context = BlackBoxCore.getContext() ?: return false
            val sp = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            if (!GAH() || !sp.getBoolean("activated", false)) {
                Msg = "SDK not activated"
                return false
            }

            val leaseExpiry = sp.getLong("lease_expires_at", 0L)
            val verifiedServerTime = sp.getLong("verified_server_time", 0L)
            val verifiedElapsed = sp.getLong("verified_elapsed_realtime", 0L)
            val elapsedNow = android.os.SystemClock.elapsedRealtime()
            if (leaseExpiry <= 0L || verifiedServerTime <= 0L || verifiedElapsed <= 0L || elapsedNow < verifiedElapsed) {
                clearActivation("Activation lease is invalid")
                return false
            }

            val authorizedPackage = sp.getString("authorized_package", "").orEmpty()
            val authorizedSigning = sp.getString("authorized_signing_sha256", "").orEmpty()
            if (authorizedPackage.isEmpty()
                || authorizedSigning.length != 64
                || context.packageName != authorizedPackage) {
                clearActivation("Activation identity is invalid")
                return false
            }

            if (lastIdentityCheckElapsed == 0L
                || elapsedNow < lastIdentityCheckElapsed
                || elapsedNow - lastIdentityCheckElapsed >= IDENTITY_RECHECK_MS) {
                try {
                    val currentSigning = SecureSdkApiClient(context)
                        .appSigningCertificateSha256(authorizedPackage)
                    if (currentSigning != authorizedSigning) {
                        clearActivation("Installed APK signing identity changed")
                        return false
                    }
                    lastIdentityCheckElapsed = elapsedNow
                } catch (_: Throwable) {
                    clearActivation("Installed APK identity verification failed")
                    return false
                }
            }

            val monotonicServerNow = verifiedServerTime + (elapsedNow - verifiedElapsed) / 1000L
            val effectiveNow = maxOf(System.currentTimeMillis() / 1000L, monotonicServerNow)
            if (effectiveNow >= leaseExpiry || !RNative.isSdkSessionValid(effectiveNow)) {
                clearActivation("Activation lease expired; reconnect to the panel")
                return false
            }

            Msg = "Secure activation lease valid"
            return true
        }

        @JvmStatic
        fun clearActivation(reason: String = "SDK not activated") {
            is_False = false
            lastIdentityCheckElapsed = 0L
            try {
                RNative.clearSdkSession()
            } catch (_: Throwable) {
            }
            try {
                BlackBoxCore.getContext()?.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean("activated", false)
                    ?.remove("lease_expires_at")
                    ?.remove("verified_server_time")
                    ?.remove("verified_elapsed_realtime")
                    ?.remove("authorized_package")
                    ?.remove("authorized_signing_sha256")
                    ?.remove("package_policy")
                    ?.remove("signing_policy")
                    ?.remove("device_policy")
                    ?.putString("server_status", "offline")
                    ?.apply()
            } catch (_: Throwable) {
            }
            Msg = reason
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
                } catch (_: Exception) {
                }
            }
        }

        @JvmStatic
        fun setHidden(status: String?) {
            if (status == null) return
            try {
                val value = status.equals("online", ignoreCase = true)
                is_False = value
                val ctx = BlackBoxCore.getContext()
                if (ctx != null) {
                    val sp = ctx.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                    sp.edit().apply {
                        putString("server_status", status)
                        apply()
                    }
                }
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
            return is_False
        }

        @JvmStatic
        fun getUrlHidden(): String {
            return 获取接口地址()
        }

        @JvmStatic
        fun 获取接口地址(): String {
            return RNative.getSdkPanelEndpoint()
        }

        @JvmStatic
        fun isSystemApp(): Boolean {
            if (!GAH()) {
                Msg = "❌ Server Offline - Functions Blocked"
                try {
                    AdvancedPopupHelper.showAuto()
                } catch (_: Exception) {
                }
                return false
            }
            val isActivated = getActivatedSdk()
            if (!isActivated) {
                try {
                    AdvancedPopupHelper.showAuto()
                } catch (_: Exception) {
                }
                return false
            }
            Msg = "✅ Server Online & Licence Valid"
            return true
        }

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

        @JvmStatic
        fun loadSavedStatus() {
            try {
                is_False = true
                lastIdentityCheckElapsed = 0L
                if (!getActivatedSdk()) {
                    is_False = false
                }
            } catch (_: Exception) {
                is_False = false
            }
        }
    }
}
