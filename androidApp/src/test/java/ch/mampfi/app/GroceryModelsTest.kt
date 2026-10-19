package ch.mampfi.app

import ch.mampfi.app.data.GroceryUnit
import ch.mampfi.app.data.quantityToBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroceryModelsTest {
    @Test fun `quantity input converts compatible units to base values`() {
        assertEquals(1_500, quantityToBase("1,5", GroceryUnit.KG))
        assertEquals(750, quantityToBase("0.75", GroceryUnit.L))
        assertEquals(3, quantityToBase("3", GroceryUnit.PIECE))
    }

    @Test fun `quantity rejects invalid and fractional base units`() {
        assertNull(quantityToBase("", GroceryUnit.G))
        assertNull(quantityToBase("-2", GroceryUnit.ML))
        assertNull(quantityToBase("1,5", GroceryUnit.PIECE))
    }
}
