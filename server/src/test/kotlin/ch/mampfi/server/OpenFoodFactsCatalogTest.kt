package ch.mampfi.server

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenFoodFactsCatalogTest {
    @Test fun `parses German product and category from search response`() {
        val result = OpenFoodFactsCatalog().parse(
            """{"hits":{"hits":[{"_source":{"code":"761020001","product_name_de":"Vollmilch","product_name":"Milk","brands":"M-Classic","quantity":"1 l","categories_tags":["de:milch"]}}]}}""",
        ).single()

        assertEquals("Vollmilch", result.product.name)
        assertEquals("off:761020001", result.product.stableKey)
        assertEquals(GroceryCategory.DAIRY_EGGS, result.category)
    }
}
