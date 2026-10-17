package org.yonicomponents.duplicateselection.testing

fun assertTrue(actual: Boolean) {
    if (!actual) throw AssertionError("Expected true")
}

fun assertFalse(actual: Boolean) {
    if (actual) throw AssertionError("Expected false")
}

fun assertEquals(expected: Any?, actual: Any?) = assertEquals("", expected, actual)

fun assertEquals(message: String, expected: Any?, actual: Any?) {
    if (expected != actual) throw AssertionError("$message expected <$expected>, got <$actual>")
}

fun <T : Throwable> assertThrows(expected: Class<T>, block: () -> Unit): T {
    try {
        block()
    } catch (actual: Throwable) {
        if (expected.isInstance(actual)) return expected.cast(actual)
        throw AssertionError("Expected ${expected.name}, got ${actual.javaClass.name}", actual)
    }
    throw AssertionError("Expected ${expected.name}, but no exception was thrown")
}
