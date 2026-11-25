package com.ahu.ahutong.ui.screen.setup

import com.ahu.ahutong.data.SchoolConfig

private const val JWXT_SSO_HOST = SchoolConfig.JWXT_HOST
private const val JWXT_SSO_PATH = SchoolConfig.JWXT_SSO_LOGIN_PATH

internal fun isJwxtSsoUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false

    return runCatching {
        val uri = java.net.URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(JWXT_SSO_HOST, ignoreCase = true) &&
            uri.path?.trimEnd('/') == JWXT_SSO_PATH
    }.getOrDefault(false)
}
