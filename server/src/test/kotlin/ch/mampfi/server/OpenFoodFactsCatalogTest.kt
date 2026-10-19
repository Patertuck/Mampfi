package ch.mampfi.server

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenFoodFactsCatalogTest {
    @Test fun `parses German product and category from search response`() {
        val result = OpenFoodFactsCatalog().parse(
            """{"hits":[{"code":"761020001","product_name_de":"Vollmilch","product_name":"Milk","brands":["M-Classic"],"quantity":"1 l","image_front_url":"https://images.example/milk.jpg","categories_tags":["de:milch"]}]}""",
        ).single()

        assertEquals("Vollmilch", result.product.name)
        assertEquals("off:761020001", result.product.stableKey)
        assertEquals("M-Classic", result.product.brand)
        assertEquals(GroceryCategory.DAIRY_EGGS, result.category)
    }
}
