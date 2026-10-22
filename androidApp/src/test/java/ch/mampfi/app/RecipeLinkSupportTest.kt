package ch.mampfi.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeLinkSupportTest {
    @Test fun `supports Fooby and KptnCook https links`() {
        assertTrue(isSupportedRecipeLink("https://fooby.ch/de/rezepte/123/test"))
        assertTrue(isSupportedRecipeLink("https://share.kptncook.com/Dh4a/n7482bp6"))
        assertTrue(isSupportedRecipeLink("https://mobile.kptncook.com/recipe/example/d1b96d10"))
    }

    @Test fun `rejects insecure malformed and lookalike recipe links`() {
        assertFalse(isSupportedRecipeLink("http://share.kptncook.com/Dh4a/n7482bp6"))
        assertFalse(isSupportedRecipeLink("https://share.kptncook.acom/Dh4a/n7482bp6"))
        assertFalse(isSupportedRecipeLink("https://share.kptncook.com.example.test/Dh4a/n7482bp6"))
        assertFalse(isSupportedRecipeLink("not a URL"))
    }
}
