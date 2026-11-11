package com.lockeddesire.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Verschlüsselung über den Android Keystore (AES-256-GCM). */
object Crypto {
    private const val ALIAS = "lockeddesire_master"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val out = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + out
    }

    fun decrypt(data: ByteArray): ByteArray {
        val ivLen = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data.copyOfRange(1 + ivLen, data.size))
    }

    /** Zufälliges Datenbank-Passwort, verschlüsselt im App-Speicher abgelegt. */
    fun databasePassphrase(context: Context): ByteArray {
        val f = File(context.filesDir, "db.key")
        if (f.exists()) {
            try { return decrypt(f.readBytes()) } catch (_: Exception) { }
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pass = Base64.encodeToString(raw, Base64.NO_WRAP).toByteArray()
        // Alte, nicht mehr entschlüsselbare Datenbank entfernen
        context.getDatabasePath("lockeddesire.db").let { db ->
            if (db.exists()) { db.delete(); File(db.path + "-wal").delete(); File(db.path + "-shm").delete() }
        }
        f.writeBytes(encrypt(pass))
        return pass
    }

    // ---- Verschlüsselte Dateien (Toy-Fotos) ----
    private fun photoDir(context: Context) = File(context.filesDir, "vault").apply { mkdirs() }

    fun savePhoto(context: Context, bytes: ByteArray): String {
        val name = "p_" + System.currentTimeMillis() + "_" + (1000..9999).random() + ".bin"
        File(photoDir(context), name).writeBytes(encrypt(bytes))
        return name
    }

    fun loadPhoto(context: Context, name: String): ByteArray? {
        if (name.isBlank()) return null
        val f = File(photoDir(context), name)
        if (!f.exists()) return null
        return try { decrypt(f.readBytes()) } catch (_: Exception) { null }
    }

    fun deletePhoto(context: Context, name: String) {
        if (name.isNotBlank()) File(photoDir(context), name).delete()
    }

    fun deleteAllPhotos(context: Context) {
        photoDir(context).listFiles()?.forEach { it.delete() }
    }

    // ---- PIN ----
    fun newSalt(): String = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)

    fun hashPin(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 60000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ---- Passwortgeschützter Export ----
    private const val MAGIC = "LDX1"

    fun encryptWithPassword(plain: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, passwordKey(password, salt), GCMParameterSpec(128, iv))
        return MAGIC.toByteArray() + salt + iv + cipher.doFinal(plain)
    }

    fun decryptWithPassword(data: ByteArray, password: String): ByteArray {
        require(data.size > 32 && String(data.copyOfRange(0, 4)) == MAGIC) { "Keine gültige Exportdatei" }
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, passwordKey(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(data.copyOfRange(32, data.size))
    }

    private fun passwordKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
