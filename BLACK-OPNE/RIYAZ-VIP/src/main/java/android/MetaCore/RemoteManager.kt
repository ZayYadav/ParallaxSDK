package android.MetaCore

import android.MetaCore.IRemoteManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.lsposed.lsparanoid.Obfuscate
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.RNative
import top.niunaijun.blackbox.core.env.BEnvironment
import java.io.File
import java.net.URL
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Obfuscate
class RemoteManager private constructor() : IRemoteManager.Stub() {

    companion object {
        @JvmField
        val JUNIT_JAR = File(BEnvironment.getCacheDir(), "junit.apk")

        @JvmField
        val EMPTY_JAR = File(BEnvironment.getCacheDir(), "empty.apk")

        private const val MAX_RETRIES = 3
        private val exe: ExecutorService = Executors.newSingleThreadExecutor()
        private val renewalExecutor = Executors.newSingleThreadScheduledExecutor()
        @Volatile private var renewalTask: ScheduledFuture<*>? = null

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

    override fun activateSdk(userkey: String?) {
        renewalTask?.cancel(false)
        val normalizedKey = userkey?.trim().orEmpty()
        if (normalizedKey.isEmpty()) {
            nk.clearActivation("Activation key is required")
            return
        }

        exe.execute {
            val context = BlackBoxCore.getContext()
            val packageName = BlackBoxCore.getHostPkg().orEmpty()
            if (packageName.isEmpty()) {
                nk.clearActivation("Host package is unavailable")
                return@execute
            }

            val client = SecureSdkApiClient(context)
            var lastFailure = "Secure activation failed"
            for (attempt in 0..MAX_RETRIES) {
                try {
                    val response = client.activate(
                        normalizedKey,
                        packageName,
                        getAppName(context, packageName),
                        deviceId(),
                    )
                    applySecureResponse(context, response, normalizedKey)
                    return@execute
                } catch (throwable: Throwable) {
                    lastFailure = throwable.message ?: "Secure activation failed"
                    nk.clearActivation(lastFailure)
                    if (attempt < MAX_RETRIES && isRetryable(throwable)) {
                        nk.Msg = "Secure connection retry ${attempt + 1}/$MAX_RETRIES"
                        try {
                            Thread.sleep(1_000L shl attempt)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    } else {
                        break
                    }
                }
            }
            // Do not expose panel/security failure details in notifications.
            showNotificationSafe("SDK ACTIVATE FAILED", "SDK NOT ACTIVATED")
        }
    }

    private fun applySecureResponse(context: Context, data: JSONObject, licenseKey: String) {
        val serverMode = data.optString("server_mode", "offline").lowercase(Locale.ROOT)
        val message = data.optString("message", "Activation rejected")
        if (data.optString("status") != "success" || serverMode != "online") {
            nk.clearActivation(message)
            sEnableDaemonService = false
            sHideRoot = false
            sHideXposed = false
            if (serverMode == "maintenance" || serverMode == "offline") {
                showServerNotification("KESHAVXOWNER SDK", "SDK NOT ACTIVATED", "warning")
            }
            return
        }

        val leaseExpiresAt = data.optLong("lease_expires_at", 0L)
        val serverTime = data.optLong("server_time", 0L)
        if (leaseExpiresAt <= serverTime || leaseExpiresAt <= System.currentTimeMillis() / 1000L) {
            nk.clearActivation("Invalid activation lease")
            return
        }

        val packageName = context.packageName
        val signingSha256 = SecureSdkApiClient(context).appSigningCertificateSha256(packageName)
        val authorizedPackage = data.optString("authorized_package", "")
        val authorizedSigning = data.optString("authorized_signing_sha256", "")

        val nativeAuthorized = RNative.authorizeSdkSession(
            context,
            packageName,
            signingSha256,
            authorizedPackage,
            authorizedSigning,
            data.optString("_server_response_canonical", ""),
            data.optString("_server_response_signature", ""),
            data.optString("_server_identity_canonical", ""),
            data.optString("_server_identity_signature", ""),
            leaseExpiresAt,
            serverTime,
        )
        if (data.optInt("java_native_auth", 1) == 1 && !nativeAuthorized) {
            nk.clearActivation("Native authorization cross-check failed")
            return
        }
        // Native session validation is used by nk.getActivatedSdk() even when a
        // legacy license has java_native_auth disabled, so never persist a
        // successful lease unless the native identity/session state is valid.
        if (!RNative.isSdkSessionValid(serverTime)) {
            nk.clearActivation("Native activation session was not established")
            return
        }

        val expiry = data.optString("expiry", "")
        context.getSharedPreferences(nk.PREFERENCE_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("activated", true)
            .putString("expiry", expiry)
            .putLong("lease_expires_at", leaseExpiresAt)
            .putLong("verified_server_time", serverTime)
            .putLong("verified_elapsed_realtime", android.os.SystemClock.elapsedRealtime())
            .putString("authorized_package", authorizedPackage)
            .putString("authorized_signing_sha256", authorizedSigning)
            .putString("package_policy", data.optString("package_policy", ""))
            .putString("signing_policy", data.optString("signing_policy", ""))
            .putString("device_policy", data.optString("device_policy", ""))
            .putInt("toggle_expiry", data.optInt("toggle_expiry", 0))
            .putInt("toggle_feature1", data.optInt("feature1", 0))
            .putInt("toggle_feature2", data.optInt("feature2", 0))
            .apply()

        nk.setHidden("online")
        nk.Msg = "SDK activated - secure lease verified"
        renewalTask?.cancel(false)
        val renewAfter = maxOf(60L, leaseExpiresAt - serverTime - 60L)
        renewalTask = renewalExecutor.schedule({ activateSdk(licenseKey) }, renewAfter, TimeUnit.SECONDS)

        isDaemon(data.optInt("feature1", 0) == 1)
        ishideRoot(data.optInt("feature2", 0) == 1)

        if (data.has("server_notification")) {
            val notification = data.optJSONObject("server_notification")
            if (notification?.optInt("enabled", 0) == 1) {
                showServerNotification(
                    notification.optString("title", "System notice"),
                    notification.optString("message", ""),
                    notification.optString("iconType", "event"),
                )
            }
        }
        showNotificationSafe("SDK ACTIVATED", "Secure panel lease active")
    }

    private fun isRetryable(throwable: Throwable): Boolean {
        return throwable is java.net.SocketTimeoutException
            || throwable is java.net.ConnectException
            || throwable is java.net.UnknownHostException
            || throwable is javax.net.ssl.SSLException
    }

    override fun getActivatedSdk(): Boolean {
        return try {
            val result = nk.getActivatedSdk()
            nk.Msg = if (result) "✅ SDK IS ACTIVATED" else "❌ SDK IS NOT ACTIVATED"
            result
        } catch (_: Exception) {
            nk.Msg = "ERROR: FAILED TO GET ACTIVATE STATUS"
            false
        }
    }

    override fun getServerMessage(): String {
        return try {
            val msg = nk.getServerMessage()
            if (msg.isEmpty()) "No server message" else msg
        } catch (_: Exception) {
            "Error: Failed to get server message"
        }
    }

    override fun getNetwork(): Boolean {
        return try {
            val net = nk.isSystemApp()
            nk.Msg = if (net) "✅ Network: Connected" else "❌ Network: Disconnected"
            net
        } catch (_: Exception) {
            nk.Msg = "Error: Failed to check network status"
            false
        }
    }

    private fun deviceId(): String {
        return try {
            val ctx = BlackBoxCore.getContext()
            android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            ) ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun getAppName(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun isDaemon(enabled: Boolean) {
        sEnableDaemonService = enabled
        nk.Msg = if (enabled) "Daemon: ENABLED" else "Daemon: DISABLED"
    }

    private fun ishideRoot(enabled: Boolean) {
        sHideRoot = enabled
        nk.Msg = if (enabled) "Root Hide: ENABLED" else "Root Hide: DISABLED"
    }

    private fun showNotificationSafe(title: String, message: String) {
        try {
            showNotification(BlackBoxCore.getContext(), title, message)
        } catch (_: Throwable) {
        }
    }

    private val CHANNEL_ID = "meta_sdk_updates"
    private val CHANNEL_NAME = "Meta SDK Updates"

    private fun showNotification(ctx: Context, title: String, msg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            channel.description = "SDK ACTIVATE OR UPDATE NOTIFICATIONS"
            channel.enableLights(true)
            channel.lightColor = Color.BLUE
            channel.enableVibration(true)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        nm.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification.build())
    }

    private fun showServerNotification(title: String, msg: String, type: String) {
        val ctx = BlackBoxCore.getContext()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "meta_server"
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "SERVER", NotificationManager.IMPORTANCE_HIGH),
            )
        }

        val lowerType = type.lowercase()
        val icon = when {
            lowerType.contains("warn") || lowerType.contains("alert") -> android.R.drawable.stat_sys_warning
            lowerType.contains("event") -> android.R.drawable.star_big_on
            lowerType.contains("update") -> android.R.drawable.stat_sys_download_done
            else -> android.R.drawable.ic_dialog_info
        }
        nm.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(msg)
                .setColor(Color.CYAN)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun showImageNotification(title: String, msg: String, img: String, base: String) {
        exe.execute {
            try {
                if (img.isEmpty()) return@execute
                val url = if (base.isNotEmpty()) "$base/$img" else img
                val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                val ctx = BlackBoxCore.getContext()
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "meta_img"
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(
                        NotificationChannel(channelId, "IMG", NotificationManager.IMPORTANCE_HIGH),
                    )
                }
                nm.notify(
                    System.currentTimeMillis().toInt(),
                    NotificationCompat.Builder(ctx, channelId)
                        .setSmallIcon(android.R.drawable.sym_def_app_icon)
                        .setContentTitle(title)
                        .setContentText(msg)
                        .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
                        .setAutoCancel(true)
                        .build(),
                )
            } catch (_: Exception) {
            }
        }
    }
}
