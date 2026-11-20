package com.salmonllama.filematrix.core.vfs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class SftpCredentials(
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null
)

class SftpCredentialStore(context: Context) {

    private val prefs: SharedPreferences = createSecurePrefs(context)

    fun saveCredentials(
        profileId: String,
        password: String? = null,
        privateKey: String? = null,
        passphrase: String? = null
    ) {
        prefs.edit().apply {
            if (password != null) putString("pwd_$profileId", password) else remove("pwd_$profileId")
            if (privateKey != null) putString("key_$profileId", privateKey) else remove("key_$profileId")
            if (passphrase != null) putString("pass_$profileId", passphrase) else remove("pass_$profileId")
        }.apply()
    }

    fun getCredentials(profileId: String): SftpCredentials {
        return SftpCredentials(
            password = prefs.getString("pwd_$profileId", null),
            privateKey = prefs.getString("key_$profileId", null),
            passphrase = prefs.getString("pass_$profileId", null)
        )
    }

    fun clearCredentials(profileId: String) {
        prefs.edit()
            .remove("pwd_$profileId")
            .remove("key_$profileId")
            .remove("pass_$profileId")
            .apply()
    }

    companion object {
        private fun createSecurePrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "filematrix_sftp_credentials",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                context.getSharedPreferences("filematrix_sftp_credentials_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
