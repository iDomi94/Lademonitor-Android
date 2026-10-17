package com.dominiqueherbrigpersonalteam.lademonitor.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Port of the iOS `KeychainStore`: stores the auth token in encrypted storage rather than plain
 * preferences (the token is a secret).
 *
 * The Android Keystore behind [EncryptedSharedPreferences] is not as dependable as the iOS
 * Keychain: right after a reboot or an app update (exactly when the reminder receiver starts the
 * process via BOOT_COMPLETED / MY_PACKAGE_REPLACED) and on some devices at random, opening it
 * fails. The old version then fell back to the PLAIN preferences of the same file for the whole
 * process lifetime - where the token key is stored encrypted under a different name, so
 * [readToken] returned null and the app showed the login screen although the token was still
 * there and still valid on the server. That was the "logs out by itself" bug.
 *
 * Now:
 * - opening the encrypted store is retried at start, and again on every later access as long as
 *   it has not worked yet (a failure is never final for the process),
 * - the last known token is kept in memory, so a transient read error cannot sign the user out,
 * - only if the encrypted store cannot be opened at all, a token is kept in a SEPARATE plain file
 *   ([FALLBACK_FILE]), and moved into the encrypted store as soon as that works again.
 */
object TokenStore {

    private const val TAG = "TokenStore"
    private const val FILE = "lademonitor_secure"
    private const val FALLBACK_FILE = "lademonitor_token_fallback"
    private const val KEY_TOKEN = "authToken"
    private const val KEY_DELETE_PENDING = "deletePending"
    private const val OPEN_ATTEMPTS = 3

    private lateinit var appContext: Context
    private lateinit var fallback: SharedPreferences

    @Volatile private var secure: SharedPreferences? = null
    @Volatile private var cached: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        fallback = appContext.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
        cached = readFromStores(attempts = OPEN_ATTEMPTS)
    }

    fun readToken(): String? {
        cached?.let { return it }
        return readFromStores().also { cached = it }
    }

    fun saveToken(token: String) {
        cached = token
        val store = secureOrNull()
        if (store != null) {
            store.edit().putString(KEY_TOKEN, token).commit()
            fallback.edit().remove(KEY_TOKEN).remove(KEY_DELETE_PENDING).apply()
        } else {
            fallback.edit().putString(KEY_TOKEN, token).remove(KEY_DELETE_PENDING).commit()
        }
    }

    fun deleteToken() {
        cached = null
        val store = secureOrNull()
        store?.edit()?.remove(KEY_TOKEN)?.commit()
        // Signed out while the encrypted store was unavailable: its copy must not come back later.
        fallback.edit().remove(KEY_TOKEN).putBoolean(KEY_DELETE_PENDING, store == null).commit()
        appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).commit()
    }

    private fun readFromStores(attempts: Int = 1): String? {
        val store = secureOrNull(attempts)
        if (fallback.getBoolean(KEY_DELETE_PENDING, false)) {
            if (store == null) return fallback.getString(KEY_TOKEN, null)
            store.edit().remove(KEY_TOKEN).commit()
            fallback.edit().remove(KEY_DELETE_PENDING).apply()
        }
        val fromSecure = store?.let {
            try {
                it.getString(KEY_TOKEN, null)
            } catch (e: Exception) {
                Log.w(TAG, "Reading the encrypted token failed", e)
                null
            }
        }
        if (fromSecure != null) return fromSecure
        // The old fallback wrote the token in plain text into the encrypted file itself.
        val legacy = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val fromLegacy = legacy.getString(KEY_TOKEN, null)
        val fromFallback = fromLegacy ?: fallback.getString(KEY_TOKEN, null) ?: return null
        // Saved while the encrypted store was unavailable: move it there now that it works.
        if (store != null) {
            store.edit().putString(KEY_TOKEN, fromFallback).commit()
            fallback.edit().remove(KEY_TOKEN).apply()
            if (fromLegacy != null) legacy.edit().remove(KEY_TOKEN).apply()
        }
        return fromFallback
    }

    @Synchronized
    private fun secureOrNull(attempts: Int = 1): SharedPreferences? {
        secure?.let { return it }
        repeat(attempts) { attempt ->
            try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val opened = EncryptedSharedPreferences.create(
                    appContext,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                secure = opened
                return opened
            } catch (e: Exception) {
                Log.w(TAG, "Opening the encrypted store failed (attempt ${attempt + 1})", e)
                if (attempt < attempts - 1) Thread.sleep(100L * (attempt + 1))
            }
        }
        return null
    }
}
