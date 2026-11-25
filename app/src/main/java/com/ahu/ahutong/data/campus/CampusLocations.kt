package com.ahu.ahutong.data.campus

/**
 * 民政职业大学校区位置（用于「小工具 → 校园地图」）。
 *
 * 经纬度使用高德坐标系（GCJ-02）。已核验的校园 POI 固定使用其坐标，
 * 其余校区可按地址解析，失败时使用预设坐标。
 */
data class CampusLocation(
    val id: String,
    val name: String,
    val address: String,
    val lng: Double,
    val lat: Double,
    val geocodeAddress: Boolean = true,
)

object CampusLocations {
    val list: List<CampusLocation> = listOf(
        CampusLocation(
            id = "daxing",
            name = "大兴校区",
            address = "北京市大兴区科苑东路1号",
            lng = 116.3490,
            lat = 39.7545,
        ),
        CampusLocation(
            id = "yanjiao",
            name = "燕郊校区",
            // 原北京社会管理职业学院燕郊校区，高德 POI B013C0IFZZ：
            // https://ditu.amap.com/place/B013C0IFZZ（2026-11-24 核验）
            address = "河北省三河市燕郊开发区燕灵路454号",
            lng = 116.796135,
            lat = 39.954483,
            geocodeAddress = false,
        ),
    )

    fun byId(id: String): CampusLocation = list.firstOrNull { it.id == id } ?: list.first()
}
