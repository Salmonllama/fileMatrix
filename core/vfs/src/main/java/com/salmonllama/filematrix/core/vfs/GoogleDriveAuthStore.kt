package com.salmonllama.filematrix.core.vfs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.salmonllama.filematrix.core.model.GoogleDriveAccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class GoogleDriveTokenState(
    val accessToken: String,
    val refreshToken: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val expiresAtEpochMs: Long = 0L
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        return expiresAtEpochMs > 0L && nowMs >= (expiresAtEpochMs - 60_000L)
    }
}

interface GoogleDriveTokenProvider {
    fun getTokenState(): GoogleDriveTokenState?
    fun saveTokenState(state: GoogleDriveTokenState)
    fun saveAccountInfo(info: GoogleDriveAccountInfo)
    fun getAccountInfo(): GoogleDriveAccountInfo?
    fun clear()
    suspend fun refreshAccessToken(): Result<String>
}

class GoogleDriveAuthStore(context: Context) : GoogleDriveTokenProvider {

    private val prefs: SharedPreferences = createSecurePrefs(context)

    @Volatile
    private var pendingCodeVerifier: String? = null

    @Volatile
    private var pendingState: String? = null

    @Volatile
    private var pendingClientId: String? = null

    @Volatile
    private var pendingClientSecret: String? = null

    @Volatile
    private var pendingRedirectUri: String? = null

    override fun getTokenState(): GoogleDriveTokenState? {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        if (access.isBlank()) return null
        return GoogleDriveTokenState(
            accessToken = access,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null),
            clientId = prefs.getString(KEY_CLIENT_ID, null),
            clientSecret = prefs.getString(KEY_CLIENT_SECRET, null),
            expiresAtEpochMs = prefs.getLong(KEY_EXPIRES_AT, 0L)
        )
    }

    override fun saveTokenState(state: GoogleDriveTokenState) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, state.accessToken)
            if (state.refreshToken != null) putString(KEY_REFRESH_TOKEN, state.refreshToken)
            if (state.clientId != null) putString(KEY_CLIENT_ID, state.clientId)
            if (state.clientSecret != null) putString(KEY_CLIENT_SECRET, state.clientSecret)
            putLong(KEY_EXPIRES_AT, state.expiresAtEpochMs)
        }.apply()
    }

    override fun saveAccountInfo(info: GoogleDriveAccountInfo) {
        val used = info.quotaUsedBytes
        val total = info.quotaTotalBytes
        prefs.edit().apply {
            putString(KEY_ACCOUNT_EMAIL, info.email)
            putString(KEY_ACCOUNT_NAME, info.displayName)
            if (used != null) putLong(KEY_QUOTA_USED, used) else remove(KEY_QUOTA_USED)
            if (total != null) putLong(KEY_QUOTA_TOTAL, total) else remove(KEY_QUOTA_TOTAL)
        }.apply()
    }

    override fun getAccountInfo(): GoogleDriveAccountInfo? {
        val email = prefs.getString(KEY_ACCOUNT_EMAIL, null) ?: return null
        val name = prefs.getString(KEY_ACCOUNT_NAME, null) ?: email
        val used = if (prefs.contains(KEY_QUOTA_USED)) prefs.getLong(KEY_QUOTA_USED, 0L) else null
        val total = if (prefs.contains(KEY_QUOTA_TOTAL)) prefs.getLong(KEY_QUOTA_TOTAL, 0L) else null
        return GoogleDriveAccountInfo(
            email = email,
            displayName = name,
            quotaUsedBytes = used,
            quotaTotalBytes = total
        )
    }

    fun getSavedClientId(): String? = prefs.getString(KEY_CLIENT_ID, null)

    override fun clear() {
        prefs.edit().clear().apply()
        pendingCodeVerifier = null
        pendingState = null
    }

    fun buildPkceAuthorizationUrl(
        clientId: String,
        clientSecret: String? = null,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): String {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateRandomState()

        pendingCodeVerifier = verifier
        pendingState = state
        pendingClientId = clientId.trim()
        pendingClientSecret = clientSecret?.trim()?.takeIf { it.isNotEmpty() }
        pendingRedirectUri = redirectUri

        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()

        val params = listOf(
            "client_id" to clientId.trim(),
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to DRIVE_SCOPES,
            "access_type" to "offline",
            "prompt" to "consent",
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state
        ).joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

        return "$GOOGLE_AUTH_ENDPOINT?$params"
    }

    suspend fun exchangeAuthorizationCode(
        code: String,
        returnedState: String?
    ): Result<GoogleDriveTokenState> = withContext(Dispatchers.IO) {
        runCatching {
            val expectedState = pendingState
            if (expectedState != null && returnedState != null && expectedState != returnedState) {
                throw SecurityException("OAuth state mismatch; please retry sign-in")
            }
            val verifier = pendingCodeVerifier
                ?: throw IllegalStateException("No active OAuth sign-in session found")
            val clientId = pendingClientId ?: getSavedClientId()
                ?: throw IllegalStateException("Missing OAuth Client ID")
            val redirectUri = pendingRedirectUri ?: DEFAULT_REDIRECT_URI

            val formBody = buildList {
                add("grant_type" to "authorization_code")
                add("code" to code)
                add("client_id" to clientId)
                add("redirect_uri" to redirectUri)
                add("code_verifier" to verifier)
                pendingClientSecret?.let { add("client_secret" to it) }
            }.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

            val responseJson = postFormTokenRequest(formBody)
            val accessToken = responseJson.getString("access_token")
            val refreshToken = responseJson.optString("refresh_token", "").takeIf { it.isNotBlank() }
            val expiresIn = responseJson.optLong("expires_in", 3600L)

            val tokenState = GoogleDriveTokenState(
                accessToken = accessToken,
                refreshToken = refreshToken ?: getTokenState()?.refreshToken,
                clientId = clientId,
                clientSecret = pendingClientSecret,
                expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L)
            )
            saveTokenState(tokenState)
            pendingCodeVerifier = null
            pendingState = null
            tokenState
        }
    }

    override suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = getTokenState()
                ?: throw IllegalStateException("Google Drive is not connected")
            val refreshToken = current.refreshToken?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No refresh token available; please re-authenticate Google Drive")
            val clientId = current.clientId?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Missing OAuth Client ID for token refresh")

            val formBody = buildList {
                add("grant_type" to "refresh_token")
                add("refresh_token" to refreshToken)
                add("client_id" to clientId)
                current.clientSecret?.takeIf { it.isNotBlank() }?.let { add("client_secret" to it) }
            }.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

            val responseJson = postFormTokenRequest(formBody)
            val newAccess = responseJson.getString("access_token")
            val expiresIn = responseJson.optLong("expires_in", 3600L)

            val updated = current.copy(
                accessToken = newAccess,
                expiresAtEpochMs = System.currentTimeMillis() + (expiresIn * 1000L)
            )
            saveTokenState(updated)
            newAccess
        }
    }

    private fun postFormTokenRequest(formBody: String): JSONObject {
        val conn = (URL(GOOGLE_TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(formBody) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Google OAuth token exchange failed (HTTP $code): $body")
            }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_REDIRECT_URI = "com.salmonllama.filematrix://oauth2redirect"
        private const val GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val DRIVE_SCOPES = "https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/userinfo.email"

        private const val KEY_ACCESS_TOKEN = "gdrive_access_token"
        private const val KEY_REFRESH_TOKEN = "gdrive_refresh_token"
        private const val KEY_CLIENT_ID = "gdrive_client_id"
        private const val KEY_CLIENT_SECRET = "gdrive_client_secret"
        private const val KEY_EXPIRES_AT = "gdrive_expires_at"
        private const val KEY_ACCOUNT_EMAIL = "gdrive_account_email"
        private const val KEY_ACCOUNT_NAME = "gdrive_account_name"
        private const val KEY_QUOTA_USED = "gdrive_quota_used"
        private const val KEY_QUOTA_TOTAL = "gdrive_quota_total"

        internal fun generateCodeVerifier(): String {
            val bytes = ByteArray(48)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        internal fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        private fun generateRandomState(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

        private fun createSecurePrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "filematrix_gdrive_auth",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                context.getSharedPreferences("filematrix_gdrive_auth_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
