package com.d4vram.pillmate2.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityParsingTest {
    @Test fun acceptsCommaAndDot() {
        assertEquals(1.25, parseQuantity("1,25")!!, 0.0)
        assertEquals(1.25, parseQuantity("1.25")!!, 0.0)
        assertEquals(8.0, parseQuantity(" 8 ")!!, 0.0)
        assertEquals(6.0, parseQuantity("6,")!!, 0.0) // half-typed decimal still counts
    }

    @Test fun rejectsUnusableDoses() {
        listOf("", " ", "0", "0,0", "-1", "1000", "abc", "1,2,3").forEach { assertNull(it, parseQuantity(it)) }
    }
}
