package link.routix.sdk

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object Routix {
    private const val TAG = "RoutixSDK"
    private const val VERSION = "1.0.4"
    private var apiKey: String? = null
    private const val baseUrl: String = "https://api.routix.link"
    private val executor = Executors.newSingleThreadExecutor()
    private var attributionListener: ((RoutixMatch) -> Unit)? = null

    /**
     * Sets a global listener for attribution events.
     * This will be triggered whenever a match is found via [handleDeepLink] or [resolve].
     */
    fun setAttributionListener(listener: (RoutixMatch) -> Unit) {
        this.attributionListener = listener
    }

    // MARK: - Initialization

    fun initialize(context: Context, apiKey: String) {
        this.apiKey = apiKey
    }

    // MARK: - Deep Link Handling (Direct App Links)

    /**
     * Parses a direct App Link URL when the app is already installed.
     * Extracts Routix parameters from the URL intent.
     *
     * @param url The full deep link URL string.
     * @return A [RoutixMatch] if it's a valid Routix link, or `null`.
     */
    fun handleDeepLink(url: String): RoutixMatch? {
        try {
            val uri = android.net.Uri.parse(url)
            val shortCode = uri.getQueryParameter("code") ?: uri.getQueryParameter("ref")
                ?: return null

            return RoutixMatch(
                success = true,
                shortCode = shortCode,
                originalUrl = url,
                matchSource = "app_link",
                confidence = 1.0,
                metadata = null,
                timestamp = isoTimestamp()
            ).also { match ->
                attributionListener?.invoke(match)
            }
        } catch (e: Exception) {
            return null
        }
    }

    // MARK: - Attribution Resolution

    /**
     * Resolves the attribution for this install.
     *
     * Attempts Android Install Referrer first (deterministic, 100% accurate).
     * Falls back to probabilistic server-side fingerprinting if unavailable.
     *
     * Should be called once on first app launch.
     *
     * @param context Application or Activity context.
     * @param callback Called with a [RoutixMatch] if attributed, or `null` if organic.
     */
    fun resolve(context: Context, callback: (RoutixMatch?) -> Unit) {
        if (apiKey == null) {
            Log.e(TAG, "SDK not initialized. Call initialize() first.")
            callback(null)
            return
        }

        val prefs = context.getSharedPreferences("routix_sdk", Context.MODE_PRIVATE)
        if (prefs.getBoolean("resolved", false)) {
            callback(null)
            return
        }

        // Guard against double-callback: onInstallReferrerSetupFinished and
        // onInstallReferrerServiceDisconnected can both fire on some OEM devices.
        val callbackFired = AtomicBoolean(false)

        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                if (!callbackFired.compareAndSet(false, true)) return

                var referrer: String? = null
                if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                    try {
                        referrer = referrerClient.installReferrer.installReferrer
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not read install referrer: ${e.message}")
                    }
                }

                referrerClient.endConnection()
                executeResolve(context, referrer, callback)
            }

            override fun onInstallReferrerServiceDisconnected() {
                // Only fire if onInstallReferrerSetupFinished hasn't already run
                if (callbackFired.compareAndSet(false, true)) {
                    executeResolve(context, null, callback)
                }
            }
        })
    }

    private fun executeResolve(context: Context, token: String?, callback: (RoutixMatch?) -> Unit) {
        executor.execute {
            try {
                val deviceInfo = getDeviceInfo(context)
                val payload = JSONObject().apply {
                    put("install_referrer", token)
                    put("device_info", deviceInfo)
                }

                val result = makePostRequest("/api/v1/sdk/resolve", payload)
                if (result != null && result.optBoolean("success", false)) {
                    context.getSharedPreferences("routix_sdk", Context.MODE_PRIVATE)
                        .edit().putBoolean("resolved", true).apply()

                    val meta = result.optJSONObject("metadata")
                    val match = RoutixMatch(
                        success = true,
                        shortCode = result.optString("short_code").takeIf { it.isNotEmpty() },
                        originalUrl = result.optString("original_url").takeIf { it.isNotEmpty() }
                            ?: meta?.optString("original_url"),
                        matchSource = result.optString("attribution_source").takeIf { it.isNotEmpty() }
                            ?: result.optString("match_type").takeIf { it.isNotEmpty() },
                        confidence = result.optDouble("confidence", 1.0),
                        metadata = jsonToMap(meta),
                        timestamp = result.optString("timestamp").takeIf { it.isNotEmpty() }
                    ).also { match ->
                        attributionListener?.invoke(match)
                    }
                    callback(match)
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Resolve error: ${e.message}")
                callback(null)
            }
        }
    }

    // MARK: - Conversion Tracking

    /** Track when a user installs the app via a specific link. */
    fun trackInstall(code: String) = trackEvent(code, "install")

    /** Track a lead event attributed to a specific link. */
    fun trackLead(code: String, metadata: Map<String, Any>? = null) =
        trackEvent(code, "lead", metadata)

    /** Track a sale/revenue event attributed to a specific link. */
    fun trackSale(code: String, amount: Double, currency: String = "USD") =
        trackEvent(code, "sale", mapOf("amount" to amount, "currency" to currency))

    /**
     * Track a custom event attributed to a specific link.
     * @param code The Routix short link code (e.g. "SUMMER24").
     * @param eventType Your custom event name (e.g. "tutorial_complete").
     */
    fun trackLinkEvent(code: String, eventType: String, metadata: Map<String, Any>? = null) =
        trackEvent(code, "track", (metadata ?: emptyMap()) + mapOf("event_type" to eventType))

    /**
     * Track a workspace-level custom event independent of any link.
     * Useful for general app analytics (e.g. sign-up, onboarding steps).
     */
    fun trackCustomEvent(eventType: String, metadata: Map<String, Any>? = null) {
        if (apiKey == null) return
        executor.execute {
            val payload = JSONObject().apply {
                metadata?.forEach { (k, v) -> put(k, v) }
                put("event_type", eventType)
                put("sdk_v", "android-$VERSION")
                put("timestamp", isoTimestamp())
            }
            makePostRequest("/api/v1/track", payload)
        }
    }

    private fun trackEvent(code: String, type: String, metadata: Map<String, Any>? = null) {
        executor.execute {
            val payload = JSONObject().apply {
                metadata?.forEach { (k, v) -> put(k, v) }
                put("sdk_v", "android-$VERSION")
                put("timestamp", isoTimestamp())
            }
            makePostRequest("/api/v1/links/$code/$type", payload)
        }
    }

    // MARK: - Networking

    private fun makePostRequest(endpoint: String, payload: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl$endpoint")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000 // 5 seconds
            conn.readTimeout = 5000    // 5 seconds
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("X-SDK-Version", "android-$VERSION")
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            if (conn.responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                return JSONObject(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        return null
    }

    // MARK: - Device Info (for fingerprint matching)

    private fun getDeviceInfo(context: Context): JSONObject {
        val pInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) { null }

        val prefs = context.getSharedPreferences("routix_sdk", Context.MODE_PRIVATE)

        // 1. Anonymous Device ID
        var anonId = prefs.getString("anon_id", null)
        if (anonId == null) {
            anonId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("anon_id", anonId).apply()
        }

        // 2. First Open Timestamp
        var firstOpen = prefs.getString("first_open", null)
        if (firstOpen == null) {
            firstOpen = isoTimestamp()
            prefs.edit().putString("first_open", firstOpen).apply()
        }

        val metrics = context.resources.displayMetrics

        return JSONObject().apply {
            put("sdk_version", "android-$VERSION")
            put("app_id", context.packageName)
            put("app_version", pInfo?.versionName ?: "")
            put("build_number", pInfo?.versionCode?.toString() ?: "")
            put("os", "android")
            put("os_version", Build.VERSION.RELEASE)
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("screen_width", metrics.widthPixels)
            put("screen_height", metrics.heightPixels)
            put("locale", Locale.getDefault().toString())
            put("timezone", TimeZone.getDefault().id)
            put("anonymous_device_id", anonId)
            put("first_open_timestamp", firstOpen)
        }
    }

    // MARK: - Helpers

    /** ISO 8601 timestamp compatible with Android API 19+ (no java.time required). */
    private fun isoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun jsonToMap(json: JSONObject?): Map<String, Any>? {
        if (json == null) return null
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key -> map[key] = json.get(key) }
        return map
    }
}
