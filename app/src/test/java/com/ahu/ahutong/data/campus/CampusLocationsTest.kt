package com.ahu.ahutong.data.campus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampusLocationsTest {

    @Test
    fun `provides both campuses with usable coordinates`() {
        val list = CampusLocations.list
        assertEquals(2, list.size)
        assertTrue(list.any { it.name == "大兴校区" })
        assertTrue(list.any { it.name == "燕郊校区" })
        list.forEach { campus ->
            assertTrue(campus.address.isNotBlank(), campus.id)
            assertTrue(campus.lng in 70.0..140.0, "lng out of range: ${campus.id}")
            assertTrue(campus.lat in 3.0..55.0, "lat out of range: ${campus.id}")
        }
    }

    @Test
    fun `byId falls back to the first campus`() {
        assertEquals("daxing", CampusLocations.list.first().id)
        assertEquals("yanjiao", CampusLocations.byId("yanjiao").id)
        assertEquals("daxing", CampusLocations.byId("unknown").id)
    }

    @Test
    fun `yanjiao uses the verified campus POI instead of a road geocode`() {
        val campus = CampusLocations.byId("yanjiao")
        assertEquals("河北省三河市燕郊开发区燕灵路454号", campus.address)
        assertEquals(116.796135, campus.lng, 0.000001)
        assertEquals(39.954483, campus.lat, 0.000001)
        assertFalse(campus.geocodeAddress)
    }
}
