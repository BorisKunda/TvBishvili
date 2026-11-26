package com.bk.mmovies.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bk.mmovies.data.source.local.preferences.AuthCredentialsSharedPrefs
import com.bk.mmovies.keystore.FakeAndroidKeyStoreRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthCredentialsSharedPrefsTest {

    @get:Rule
    val fakeAndroidKeyStoreRule = FakeAndroidKeyStoreRule()

    private lateinit var apiKeyStorage: AuthCredentialsSharedPrefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyStorage = AuthCredentialsSharedPrefs(context)
        apiKeyStorage.clearStorage()
    }

    @Test
    fun testGetApiKeyReturnsNullIfNoKeySaved() {
        val result = apiKeyStorage.getSharedPrefApiKey()
        assertNull(result)
    }

    @Test
    fun testSaveKey() {
        val dummyKey = "123"
        apiKeyStorage.saveSharedPrefApiKey(dummyKey)
        val savedKey = apiKeyStorage.getSharedPrefApiKey()
        assertEquals(
                dummyKey,
                savedKey
                    )
    }

    @Test
    fun testRemoveKey() {
        val dummyKey = "123"
        apiKeyStorage.saveSharedPrefApiKey(dummyKey)
        apiKeyStorage.removeApiKey()
        val savedKey = apiKeyStorage.getSharedPrefApiKey()
        assertNull(savedKey)
    }

    @After
    fun tearDown() {
        apiKeyStorage.clearStorage()
    }
}
