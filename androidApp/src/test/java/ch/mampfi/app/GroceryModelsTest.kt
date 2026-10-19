package ch.mampfi.app

import ch.mampfi.app.data.GroceryUnit
import ch.mampfi.app.data.quantityToBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroceryModelsTest {
    @Test fun `quantity input converts compatible units to base values`() {
        assertEquals(1_500.0, quantityToBase("1,5", GroceryUnit.KG))
        assertEquals(750.0, quantityToBase("0.75", GroceryUnit.L))
        assertEquals(3.0, quantityToBase("3", GroceryUnit.PIECE))
    }

    @Test fun `quantity rejects invalid values and accepts fractional units`() {
        assertNull(quantityToBase("", GroceryUnit.G))
        assertNull(quantityToBase("-2", GroceryUnit.ML))
        assertEquals(1.5, quantityToBase("1,5", GroceryUnit.PIECE))
    }
}
