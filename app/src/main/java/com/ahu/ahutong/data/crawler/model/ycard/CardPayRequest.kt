package com.ahu.ahutong.data.crawler.model.ycard

import com.ahu.ahutong.data.SchoolConfig
import com.ahu.ahutong.data.crawler.utils.generateNonce
import com.ahu.ahutong.data.crawler.utils.getTimestamp
import com.ahu.ahutong.data.crawler.utils.sha256

class CardPayRequest(orderId: String) : RequestBody() {

    init {
        val time = getTimestamp()
        val nonce = generateNonce()
        val appId = "56321"
        val payStep = "2"
        val payType = "BANKCARD"
        val payTypeId = "63"
        val redirectUrl = SchoolConfig.YCARD_PAYMENT_REDIRECT_URL
        val userAgent = "h5"
        val synAccessSource = "h5"

        addParams(
            mapOf(
                "paytypeid" to payTypeId,
                "paytype" to payType,
                "paystep" to payStep,
                "orderid" to orderId,
                "redirect_url" to redirectUrl,
                "userAgent" to userAgent,
                "APP_ID" to appId,
                "TIMESTAMP" to time,
                "SIGN_TYPE" to "SHA256",
                "NONCE" to nonce,
                // SIGN 里的 redirect_url 必须与上面实际提交的 redirect_url 完全一致。
                "SIGN" to sha256("APP_ID=$appId&NONCE=$nonce&SIGN_TYPE=SHA256&TIMESTAMP=$time&orderid=$orderId&paystep=$payStep&paytype=$payType&paytypeid=$payTypeId&redirect_url=$redirectUrl&userAgent=$userAgent&SECRET_KEY=0osTIhce7uPvDKHz6aa67bhCukaKoYl4").uppercase(),
                "synAccessSource" to synAccessSource
            )
        )
    }

}
