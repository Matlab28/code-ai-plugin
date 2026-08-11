package com.codeai.reviewer.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewFiltersTest {
    @Test
    fun `rejects sensitive filenames`() {
        assertTrue(ReviewFilters.isSensitive(".env.local", ""))
        assertTrue(ReviewFilters.isSensitive("config/private.key", ""))
        assertTrue(ReviewFilters.isSensitive(".aws/credentials", ""))
    }

    @Test
    fun `rejects likely secrets in content`() {
        assertTrue(ReviewFilters.isSensitive("App.kt", "api_key=abcdefghijklmnop"))
        assertTrue(ReviewFilters.isSensitive("App.kt", "Authorization: Bearer abcdefghijklmnop"))
    }

    @Test
    fun `allows normal source and supports disabling content scan`() {
        assertFalse(ReviewFilters.isSensitive("src/main/java/App.java", "class App {}"))
        assertFalse(ReviewFilters.isSensitive("src/App.kt", "password=not-a-real-example", scanSecrets = false))
    }
}
