package com.ahu.ahutong.ui.state

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel

/**
 * 贡献名单数据源。
 *
 * 民职通基于开源项目 AHUTong 二次开发，团队成员信息待补充。
 * 这里不保留原项目作者的个人姓名与联系方式，统一以占位团队信息展示。
 */
class DeveloperViewModel : ViewModel() {
    interface CardInfo {
        val name: String
        val desc: String
        val contact: String
        val img: String
        val onclick: (Context) -> Unit
    }

    class Developer(override val name: String, override val desc: String, override val contact: String) :
        CardInfo {

        override val img: String
            get() = ""
        override val onclick: (Context) -> Unit
            get() = { context ->
                Toast.makeText(
                    context,
                    "团队成员信息待补充，欢迎通过项目仓库 Issue 反馈",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    class Partner(override val name: String, override val desc: String) : CardInfo {
        override val contact: String = ""
        override val img: String = ""
        override val onclick: (Context) -> Unit
            get() = { context ->
                Toast.makeText(context, "欢迎通过项目仓库 Issue 联系我们", Toast.LENGTH_SHORT).show()
            }
    }

    val developers by lazy {
        listOf(
            Developer(
                "民职通开发组（待补充）",
                "校园服务客户端维护与二次开发",
                "",
            ),
        )
    }
    val partners by lazy {
        listOf(
            Partner(
                "加入我们",
                "欢迎民政职业大学的同学一起完善民职通，参与方式请关注项目仓库。",
            ),
        )
    }
}
