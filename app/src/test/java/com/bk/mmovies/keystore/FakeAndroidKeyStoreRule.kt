package com.bk.mmovies.keystore

import android.security.keystore.KeyGenParameterSpec
import org.junit.rules.ExternalResource
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

private const val ANDROID_KEY_STORE_PROVIDER_NAME = "AndroidKeyStore"
private const val DEFAULT_AES_KEY_SIZE_BITS = 256

class FakeAndroidKeyStoreRule : ExternalResource() {

    override fun before() {
        Security.removeProvider(ANDROID_KEY_STORE_PROVIDER_NAME)
        InMemoryAndroidKeyStoreEntries.secretKeysByAlias.clear()
        Security.addProvider(FakeAndroidKeyStoreProvider())
    }

    override fun after() {
        Security.removeProvider(ANDROID_KEY_STORE_PROVIDER_NAME)
        InMemoryAndroidKeyStoreEntries.secretKeysByAlias.clear()
    }
}

private object InMemoryAndroidKeyStoreEntries {
    val secretKeysByAlias = mutableMapOf<String, SecretKey>()
}

private class FakeAndroidKeyStoreProvider : Provider(
        ANDROID_KEY_STORE_PROVIDER_NAME,
        1.0,
        "In-memory stand-in for the Android Keystore in JVM tests"
                                                     ) {
    init {
        put(
                "KeyStore.$ANDROID_KEY_STORE_PROVIDER_NAME",
                InMemoryAndroidKeyStoreSpi::class.java.name
           )
        put(
                "KeyGenerator.AES",
                InMemoryAndroidAesKeyGeneratorSpi::class.java.name
           )
    }
}

class InMemoryAndroidKeyStoreSpi : KeyStoreSpi() {

    private val secretKeysByAlias get() = InMemoryAndroidKeyStoreEntries.secretKeysByAlias

    override fun engineGetKey(
            alias: String,
            password: CharArray?
                             ): Key? = secretKeysByAlias[alias]

    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null

    override fun engineGetCertificate(alias: String): Certificate? = null

    override fun engineGetCreationDate(alias: String): Date? = if (alias in secretKeysByAlias) Date() else null

    override fun engineSetKeyEntry(
            alias: String,
            key: Key,
            password: CharArray?,
            chain: Array<out Certificate>?
                                  ) {
        secretKeysByAlias[alias] = key as SecretKey
    }

    override fun engineSetKeyEntry(
            alias: String,
            key: ByteArray,
            chain: Array<out Certificate>?
                                  ) {
        throw UnsupportedOperationException()
    }

    override fun engineSetCertificateEntry(
            alias: String,
            cert: Certificate
                                          ) {
        throw UnsupportedOperationException()
    }

    override fun engineDeleteEntry(alias: String) {
        secretKeysByAlias.remove(alias)
    }

    override fun engineAliases(): Enumeration<String> = Collections.enumeration(secretKeysByAlias.keys.toList())

    override fun engineContainsAlias(alias: String): Boolean = alias in secretKeysByAlias

    override fun engineSize(): Int = secretKeysByAlias.size

    override fun engineIsKeyEntry(alias: String): Boolean = alias in secretKeysByAlias

    override fun engineIsCertificateEntry(alias: String): Boolean = false

    override fun engineGetCertificateAlias(cert: Certificate): String? = null

    override fun engineStore(
            stream: OutputStream?,
            password: CharArray?
                            ) = Unit

    override fun engineLoad(
            stream: InputStream?,
            password: CharArray?
                           ) = Unit
}

class InMemoryAndroidAesKeyGeneratorSpi : KeyGeneratorSpi() {

    private var keyAlias: String? = null
    private var keySizeBits = DEFAULT_AES_KEY_SIZE_BITS

    override fun engineInit(random: SecureRandom?) = Unit

    override fun engineInit(
            params: AlgorithmParameterSpec?,
            random: SecureRandom?
                           ) {
        val keyGenParameterSpec = params as KeyGenParameterSpec
        keyAlias = keyGenParameterSpec.keystoreAlias
        if (keyGenParameterSpec.keySize > 0) {
            keySizeBits = keyGenParameterSpec.keySize
        }
    }

    override fun engineInit(
            keysize: Int,
            random: SecureRandom?
                           ) {
        keySizeBits = keysize
    }

    override fun engineGenerateKey(): SecretKey {
        val softwareKeyGenerator = KeyGenerator.getInstance("AES", "SunJCE")
        softwareKeyGenerator.init(keySizeBits)
        val generatedSecretKey = softwareKeyGenerator.generateKey()
        keyAlias?.let { alias -> InMemoryAndroidKeyStoreEntries.secretKeysByAlias[alias] = generatedSecretKey }
        return generatedSecretKey
    }
}
