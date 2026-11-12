package com.bk.mmovies.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bk.mmovies.data.source.local.preferences.AuthCredentialsSharedPrefs
import com.bk.mmovies.data.source.remote.QUERY_PARAM_API_KEY
import com.bk.mmovies.data.source.remote.interceptor.ApiKeyRejectionInterceptor
import com.bk.mmovies.data.source.remote.keyrefresh.ApiKeyRefresher
import com.bk.mmovies.data.source.remote.keyrefresh.ApiKeyRefresherHolder
import com.bk.mmovies.data.source.remote.keyrefresh.ApiKeyRejectionEvents
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress

private const val MOCK_SERVER_DOTTED_HOST_NAME = "mock.tmdb.test"
private const val REJECTED_KEY = "rejected-key"
private const val REPLACEMENT_KEY = "replacement-key"
private const val INVALID_KEY_BODY =
        """{"status_code":7,"status_message":"Invalid API key.","success":false}"""
private const val SESSION_DENIED_BODY =
        """{"status_code":3,"status_message":"Authentication failed.","success":false}"""

@RunWith(AndroidJUnit4::class)
class ApiKeyRejectionInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiKeyStorage: AuthCredentialsSharedPrefs
    private lateinit var refresherHolder: ApiKeyRefresherHolder
    private lateinit var rejectionEvents: ApiKeyRejectionEvents
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var rejectionCollectorJob: Job
    private var originalApiKey: String? = null
    private var rejectionEventCount = 0
    private var refresherCallCount = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyStorage = AuthCredentialsSharedPrefs(context)
        originalApiKey = apiKeyStorage.getSharedPrefApiKey()
        apiKeyStorage.saveSharedPrefApiKey(REJECTED_KEY)
        refresherHolder = ApiKeyRefresherHolder()
        rejectionEvents = ApiKeyRejectionEvents()
        rejectionCollectorJob = CoroutineScope(Dispatchers.Unconfined).launch {
            rejectionEvents.rejections.collect { rejectionEventCount++ }
        }
        val heldCertificate = HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .addSubjectAlternativeName(MOCK_SERVER_DOTTED_HOST_NAME)
                .build()
        val certificates = HandshakeCertificates.Builder()
                .heldCertificate(heldCertificate)
                .addTrustedCertificate(heldCertificate.certificate)
                .build()
        mockWebServer = MockWebServer()
        mockWebServer.protocols = listOf(Protocol.HTTP_1_1)
        mockWebServer.useHttps(
                certificates.sslSocketFactory(),
                false
                              )
        mockWebServer.start()
        okHttpClient = OkHttpClient.Builder()
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return listOf(InetAddress.getByName(mockWebServer.hostName))
                    }
                })
                .sslSocketFactory(
                        certificates.sslSocketFactory(),
                        certificates.trustManager
                                 )
                .addInterceptor(
                        ApiKeyRejectionInterceptor(
                                apiKeyStorage,
                                refresherHolder,
                                rejectionEvents,
                                Gson()
                                                  )
                               )
                .build()
    }

    @After
    fun tearDown() {
        rejectionCollectorJob.cancel()
        mockWebServer.shutdown()
        val keyToRestore = originalApiKey
        if (keyToRestore != null) {
            apiKeyStorage.saveSharedPrefApiKey(keyToRestore)
        } else {
            apiKeyStorage.removeApiKey()
        }
    }

    private fun refresherReturning(replacementKey: String?) {
        refresherHolder.refresher = ApiKeyRefresher {
            refresherCallCount++
            replacementKey
        }
    }

    private fun executeRequest(path: String = "/3/movie/popular"): Response {
        val url = mockWebServer.url(path)
                .newBuilder()
                .host(MOCK_SERVER_DOTTED_HOST_NAME)
                .addQueryParameter(QUERY_PARAM_API_KEY, REJECTED_KEY)
                .build()
        return okHttpClient.newCall(Request.Builder().url(url).build()).execute()
    }

    @Test
    fun rejectedKeyIsReplacedAndCallIsRetriedOnce() {
        refresherReturning(REPLACEMENT_KEY)
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = executeRequest()

        assertEquals(200, response.code)
        assertEquals(2, mockWebServer.requestCount)
        mockWebServer.takeRequest()
        val retriedRequest = mockWebServer.takeRequest()
        assertEquals(REPLACEMENT_KEY, retriedRequest.requestUrl?.queryParameter(QUERY_PARAM_API_KEY))
        assertEquals(REPLACEMENT_KEY, apiKeyStorage.getSharedPrefApiKey())
        assertEquals(0, rejectionEventCount)
    }

    @Test
    fun replacementKeyAlsoRejectedNotifiesAndStopsAfterOneRetry() {
        refresherReturning(REPLACEMENT_KEY)
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))

        val response = executeRequest()

        assertEquals(401, response.code)
        assertEquals(2, mockWebServer.requestCount)
        assertEquals(1, rejectionEventCount)
        assertNull(apiKeyStorage.getSharedPrefApiKey())
    }

    @Test
    fun unauthorizedForOtherReasonsIsLeftAlone() {
        refresherReturning(REPLACEMENT_KEY)
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(SESSION_DENIED_BODY))

        val response = executeRequest()

        assertEquals(401, response.code)
        assertEquals(1, mockWebServer.requestCount)
        assertEquals(0, refresherCallCount)
        assertEquals(0, rejectionEventCount)
        assertEquals(REJECTED_KEY, apiKeyStorage.getSharedPrefApiKey())
    }

    @Test
    fun refresherReturningSameKeyDoesNotRetryAndNotifies() {
        refresherReturning(REJECTED_KEY)
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))

        val response = executeRequest()

        assertEquals(401, response.code)
        assertEquals(1, mockWebServer.requestCount)
        assertEquals(1, rejectionEventCount)
        assertNull(apiKeyStorage.getSharedPrefApiKey())
    }

    @Test
    fun refresherFailingDoesNotRetryAndNotifies() {
        refresherReturning(null)
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))

        val response = executeRequest()

        assertEquals(401, response.code)
        assertEquals(1, mockWebServer.requestCount)
        assertEquals(1, refresherCallCount)
        assertEquals(1, rejectionEventCount)
        assertNull(apiKeyStorage.getSharedPrefApiKey())
    }

    @Test
    fun afterAKeyWasDiscardedASecondRejectionDoesNotRefetchAgain() {
        refresherReturning(null)
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))

        executeRequest()
        executeRequest()

        assertEquals(1, refresherCallCount)
        assertEquals(2, rejectionEventCount)
    }

    @Test
    fun apiKeyValidationRequestIsNotIntercepted() {
        refresherReturning(REPLACEMENT_KEY)
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))

        val response = executeRequest("/3/authentication")

        assertEquals(401, response.code)
        assertEquals(1, mockWebServer.requestCount)
        assertEquals(0, refresherCallCount)
        assertEquals(0, rejectionEventCount)
    }

    @Test
    fun withoutRefresherTheResponseIsReturnedUntouched() {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(INVALID_KEY_BODY))

        val response = executeRequest()

        assertEquals(401, response.code)
        assertEquals(1, mockWebServer.requestCount)
        assertEquals(0, rejectionEventCount)
    }
}
