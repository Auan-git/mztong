package com.ahu.ahutong.data.model

import java.io.Serializable

data class Tel(
    val name: String,
    val tel: String? = null, // 大兴校区号码（北京，区号 010）
    val tel2: String? = tel // 燕郊校区号码（河北三河燕郊，区号 0316）
) : Serializable
