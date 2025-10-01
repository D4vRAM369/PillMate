package com.d4vram.pillmate2.data.voice

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object VoiceNoteEncryption {
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).apply {
        secureRandom.nextBytes(this)
    }

    fun generateIv(): ByteArray = ByteArray(IV_LENGTH_BYTES).apply {
        secureRandom.nextBytes(this)
    }

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val keySpec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secret = factory.generateSecret(keySpec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    fun encryptFile(key: SecretKey, iv: ByteArray, input: File, output: File) {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        FileInputStream(input).use { inputStream ->
            FileOutputStream(output).use { fileOutput ->
                CipherOutputStream(fileOutput, cipher).use { cipherOutput ->
                    inputStream.copyTo(cipherOutput)
                }
            }
        }
    }

    fun decryptFile(key: SecretKey, iv: ByteArray, input: File, output: File) {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        FileInputStream(input).use { inputStream ->
            CipherInputStream(inputStream, cipher).use { cipherInput ->
                FileOutputStream(output).use { fileOutput ->
                    cipherInput.copyTo(fileOutput)
                }
            }
        }
    }

    fun wipePassword(password: CharArray?) {
        password?.fill('\u0000')
    }
}
