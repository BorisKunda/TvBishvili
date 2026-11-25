package com.bk.mmovies.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsUrlRulesTest {

    @Test
    fun upgradedToHttps_replacesPlainHttpScheme() {
        assertEquals(
                "https://sylhetmirror.com/files/uploads/2026/11/dvvv.jpg",
                "http://sylhetmirror.com/files/uploads/2026/11/dvvv.jpg".upgradedToHttps()
                    )
    }

    @Test
    fun upgradedToHttps_isCaseInsensitiveOnScheme() {
        assertEquals("https://example.com/a.jpg", "HTTP://example.com/a.jpg".upgradedToHttps())
    }

    @Test
    fun upgradedToHttps_keepsQueryString() {
        assertEquals(
                "https://static1.squarespace.com/a.jpg?format=1500w",
                "http://static1.squarespace.com/a.jpg?format=1500w".upgradedToHttps()
                    )
    }

    @Test
    fun upgradedToHttps_leavesHttpsUnchanged() {
        assertEquals("https://example.com/a.jpg", "https://example.com/a.jpg".upgradedToHttps())
    }

    @Test
    fun upgradedToHttps_onlyChangesTheLeadingScheme() {
        assertEquals(
                "https://example.com/redirect?to=http://other.com/a.jpg",
                "http://example.com/redirect?to=http://other.com/a.jpg".upgradedToHttps()
                    )
    }

    @Test
    fun isConsentWallUrl_trueForYahooConsentLink() {
        assertTrue(
                "https://consent.yahoo.com/v2/collectConsent?sessionId=1_cc-session_abc".isConsentWallUrl()
                  )
    }

    @Test
    fun isConsentWallUrl_trueForOtherConsentHostsAndHttpScheme() {
        assertTrue("https://consent.google.com/ml?continue=https://example.com".isConsentWallUrl())
        assertTrue("http://CONSENT.example.com/x".isConsentWallUrl())
    }

    @Test
    fun isConsentWallUrl_falseForRealArticleLinks() {
        assertFalse("https://geektyrant.com/news/david-corenswet".isConsentWallUrl())
        assertFalse("https://www.example.com/consent.html".isConsentWallUrl())
        assertFalse("https://notconsent.example.com/a".isConsentWallUrl())
    }
}
