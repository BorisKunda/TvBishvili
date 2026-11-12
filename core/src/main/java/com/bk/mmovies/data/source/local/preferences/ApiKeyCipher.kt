package com.bk.mmovies.data.source.local.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiKeyCipher {

    fun encrypt(plainText: String): String? {
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    getOrCreateSecretKey()
                       )
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + encryptedBytes
            Base64.encodeToString(
                    payload,
                    Base64.NO_WRAP
                                 )
        } catch (exception: Exception) {
            null
        }
    }

    fun decrypt(encodedPayload: String): String? {
        return try {
            val payload = Base64.decode(
                    encodedPayload,
                    Base64.NO_WRAP
                                       )
            val initializationVector = payload.copyOfRange(
                    0,
                    INITIALIZATION_VECTOR_SIZE_BYTES
                                                          )
            val encryptedBytes = payload.copyOfRange(
                    INITIALIZATION_VECTOR_SIZE_BYTES,
                    payload.size
                                                    )
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateSecretKey(),
                    GCMParameterSpec(
                            AUTHENTICATION_TAG_SIZE_BITS,
                            initializationVector
                                    )
                       )
            String(
                    cipher.doFinal(encryptedBytes),
                    Charsets.UTF_8
                  )
        } catch (exception: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingSecretKey = keyStore.getKey(
                SECRET_KEY_ALIAS,
                null
                                               ) as? SecretKey
        if (existingSecretKey != null) return existingSecretKey

        val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
                                                   )
        keyGenerator.init(
                KeyGenParameterSpec.Builder(
                        SECRET_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                                           )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(SECRET_KEY_SIZE_BITS)
                        .build()
                         )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val SECRET_KEY_ALIAS = "mmovies_api_key_secret"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val SECRET_KEY_SIZE_BITS = 256
        const val INITIALIZATION_VECTOR_SIZE_BYTES = 12
        const val AUTHENTICATION_TAG_SIZE_BITS = 128
    }
}
