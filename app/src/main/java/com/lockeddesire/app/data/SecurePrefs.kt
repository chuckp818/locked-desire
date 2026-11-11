package com.lockeddesire.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lockeddesire.app.security.Crypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("lock")

data class LockState(val pinSet: Boolean = false, val biometric: Boolean = false)

/** DataStore für App-Sperre und den verschlüsselten KI-Schlüssel. */
class SecurePrefs(private val context: Context) {
    private val pinHash = stringPreferencesKey("pin_hash")
    private val pinSalt = stringPreferencesKey("pin_salt")
    private val biometric = booleanPreferencesKey("biometric")
    private val apiKey = stringPreferencesKey("api_key")

    val lock: Flow<LockState> = context.store.data.map {
        LockState(!it[pinHash].isNullOrBlank(), it[biometric] ?: false)
    }

    suspend fun lockNow(): LockState = lock.first()

    suspend fun setPin(pin: String) {
        val salt = Crypto.newSalt()
        val hash = Crypto.hashPin(pin, salt)
        context.store.edit { it[pinSalt] = salt; it[pinHash] = hash }
    }

    suspend fun clearPin() = context.store.edit { it.remove(pinHash); it.remove(pinSalt); it[biometric] = false }.let { }

    suspend fun checkPin(pin: String): Boolean {
        val p = context.store.data.first()
        val salt = p[pinSalt] ?: return false
        return Crypto.hashPin(pin, salt) == p[pinHash]
    }

    suspend fun setBiometric(on: Boolean) = context.store.edit { it[biometric] = on }.let { }

    suspend fun setApiKey(key: String) = context.store.edit {
        if (key.isBlank()) it.remove(apiKey)
        else it[apiKey] = Base64.encodeToString(Crypto.encrypt(key.toByteArray()), Base64.NO_WRAP)
    }.let { }

    suspend fun apiKey(): String {
        val v = context.store.data.first()[apiKey] ?: return ""
        return try { String(Crypto.decrypt(Base64.decode(v, Base64.NO_WRAP))) } catch (_: Exception) { "" }
    }

    suspend fun clearAll() = context.store.edit { it.clear() }.let { }
}
