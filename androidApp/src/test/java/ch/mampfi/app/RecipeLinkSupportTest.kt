package ch.mampfi.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeLinkSupportTest {
    @Test fun `supports Fooby and KptnCook https links`() {
        assertTrue(isSupportedRecipeLink("https://fooby.ch/de/rezepte/123/test"))
        assertTrue(isSupportedRecipeLink("https://little.fooby.ch/de/rezepte/19291/aelpler-one-pot"))
        assertTrue(isSupportedRecipeLink("https://share.kptncook.com/Dh4a/n7482bp6"))
        assertTrue(isSupportedRecipeLink("https://mobile.kptncook.com/recipe/example/d1b96d10"))
        assertTrue(isSupportedRecipeLink("https://app.kptncook.com/de/recipes/example/d1b96d10"))
    }

    @Test fun `rejects insecure malformed and lookalike recipe links`() {
        assertFalse(isSupportedRecipeLink("http://share.kptncook.com/Dh4a/n7482bp6"))
        assertFalse(isSupportedRecipeLink("https://share.kptncook.acom/Dh4a/n7482bp6"))
        assertFalse(isSupportedRecipeLink("https://share.kptncook.com.example.test/Dh4a/n7482bp6"))
        assertFalse(isSupportedRecipeLink("not a URL"))
    }

    @Test fun `extraction action is available for any stored recipe link while idle`() {
        assertTrue(canRequestRecipeExtraction("https://fooby.ch/de/rezepte/123/test"))
        assertTrue(canRequestRecipeExtraction("https://share.kptncook.com/Dh4a/n7482bp6"))
        assertTrue(canRequestRecipeExtraction("https://another-provider.example/recipe"))
        assertFalse(canRequestRecipeExtraction(null))
        assertFalse(canRequestRecipeExtraction("  "))
        assertFalse(canRequestRecipeExtraction("https://fooby.ch/de/rezepte/123/test", extracting = true))
    }
}
