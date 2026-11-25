package com.ahu.ahutong.ui.screen.setup

import com.ahu.ahutong.data.SchoolConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebLoginUrlMatcherTest {

    private val ssoLoginUrl = SchoolConfig.JWXT_SSO_LOGIN_URL
    private val ssoLoginPath = SchoolConfig.JWXT_SSO_LOGIN_PATH

    @Test
    fun recognizesJwxtSsoUrlWithTicketAndTrailingSlash() {
        assertTrue(isJwxtSsoUrl(ssoLoginUrl))
        assertTrue(isJwxtSsoUrl("$ssoLoginUrl/"))
        assertTrue(isJwxtSsoUrl("$ssoLoginUrl?ticket=ST-1"))
    }

    @Test
    fun rejectsOtherOrUntrustedUrls() {
        assertFalse(isJwxtSsoUrl(SchoolConfig.JWXT_HOME_URL))
        assertFalse(isJwxtSsoUrl("http://${SchoolConfig.JWXT_HOST}$ssoLoginPath"))
        assertFalse(isJwxtSsoUrl("https://${SchoolConfig.JWXT_HOST}.example.com$ssoLoginPath"))
        assertFalse(isJwxtSsoUrl("not a url"))
        assertFalse(isJwxtSsoUrl(null))
    }
}
