package com.ahu.ahutong.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 用系统浏览器打开外部链接；没有可用浏览器时给 Toast 提示。
 *
 * 意见反馈、贡献名单等入口统一走这里，避免各页面重复写 Intent 逻辑。
 */
fun openUrlSafely(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "无法打开链接，请检查是否安装了浏览器", Toast.LENGTH_SHORT).show()
    }
}