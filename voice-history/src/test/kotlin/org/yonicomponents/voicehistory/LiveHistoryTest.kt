package org.yonicomponents.voicehistory

/** Deterministic examples of the buffer contract, with no provider or Android dependencies. */
private var passed = 0

private fun test(name: String, body: () -> Unit) {
    try {
        body()
        passed++
        println("PASS $name")
    } catch (failure: Throwable) {
        System.err.println("FAIL $name: ${failure.message}")
        throw failure
    }
}

private fun <T> equal(expected: T, actual: T) {
    check(expected == actual) { "Expected <$expected>, got <$actual>" }
}

private fun LiveHistory.snapshot() = snapshot(emptyList())
private fun LiveHistory.users() = snapshot().filter { it.first == "user" }.map { it.second }
private fun LiveHistory.omitted() = snapshot().any { it.first == "omitted" }
private fun bounded(history: List<Pair<String, String>>) {
    check(history.size <= 32)
    check(history.sumOf { it.second.toByteArray(Charsets.UTF_8).size } <= 5000)
    check(history.all { it.second.codePointCount(0, it.second.length) <= 1000 })
    check(history.count { it.first == "omitted" } <= 1)
}

fun main() {
    test("cut preserves prior input and omits interrupted output") {
        val h = LiveHistory()
        h.add(true, "What did I ask?", 10, null)
        h.add(false, "An interrupted answer", 21, null)
        h.cut(40, null)
        equal(listOf("What did I ask?"), h.users())
        check(h.omitted())
        val previous = h.snapshot()
        h.clear()
        check(h.snapshot().isEmpty())
        equal(previous, h.snapshot(previous))
    }

    test("cut keeps old and new user units separate") {
        val h = LiveHistory()
        h.add(true, "Do not create a reminder", 10, null)
        h.cut(30, null)
        h.add(true, "What time is it?", 31, null)
        equal(listOf("Do not create a reminder", "What time is it?"), h.users())
    }

    test("cut rejects unowned and boundary-crossing units whole") {
        val h = LiveHistory()
        h.add(true, "Old context", 1, null)
        h.add(true, "Unowned post-boundary input", 41, null)
        h.add(true, "Older interruption", 43, 6)
        h.add(true, "Crossing input", 39, 7)
        h.add(true, "Post-boundary suffix", 46, 7)
        h.cut(40, 7)
        check(h.users().isEmpty())
        check(h.omitted())
    }

    test("oversized continuous input cannot leave an imperative suffix") {
        val h = LiveHistory()
        h.add(true, "Do not create a reminder; " + "x".repeat(1000), 1, null)
        h.add(true, "remind me tomorrow", 3, null)
        check(h.omitted())
        check(h.users().isEmpty())
    }

    test("small deltas cannot bypass the combined unit limit") {
        val h = LiveHistory()
        h.add(true, "Do not execute; " + "x".repeat(600), 1, null)
        h.add(true, "y".repeat(500), 3, null)
        h.add(true, "execute now", 5, null)
        check(h.omitted())
        check(h.users().isEmpty())
        h.cut(7, null)
        h.add(true, "A new question", 8, null)
        equal(listOf("A new question"), h.users())
    }

    test("assistant backchannel cannot revive an omitted input suffix") {
        val h = LiveHistory()
        h.add(true, "Do not create a reminder; " + "x".repeat(1000), 1, null)
        h.add(false, "Okay?", 3, null)
        h.add(true, "remind me tomorrow", 5, null)
        check(h.omitted())
        check(h.users().isEmpty())
        h.cut(7, null)
        h.add(true, "A new question", 8, null)
        equal(listOf("A new question"), h.users())
    }

    test("unit-count eviction removes all same-generation user suffixes") {
        val h = LiveHistory()
        h.add(true, "Do not execute; this is an example", 1, null)
        repeat(20) { i ->
            h.add(false, "Okay?", 3L + i * 4, null)
            h.add(true, "remind me tomorrow", 5L + i * 4, null)
        }
        check(h.omitted())
        check(h.users().isEmpty())
        bounded(h.snapshot())
    }

    test("byte-budget eviction removes all same-generation user suffixes") {
        val h = LiveHistory()
        h.add(true, "Do not execute; this is an example", 1, null)
        repeat(5) { i ->
            h.add(false, "Okay? " + "é".repeat(600), 3L + i * 4, null)
            h.add(true, "remind me tomorrow " + "é".repeat(600), 5L + i * 4, null)
        }
        check(h.omitted())
        check(h.users().isEmpty())
        bounded(h.snapshot())
    }

    test("imported snapshot eviction cannot retain orphaned user suffixes") {
        val previous = listOf("user" to "Do not execute; this is an example") +
            (1..15).flatMap { i ->
                listOf("assistant" to "Okay? " + "é".repeat(60),
                    "user" to "Suffix-$i " + "é".repeat(60))
            }
        val h = LiveHistory()
        val fresh = "Current question " + "😀".repeat(900)
        h.add(true, fresh, 1, null)
        val result = h.snapshot(previous)
        check(result.any { it.first == "omitted" })
        equal(listOf(fresh), result.filter { it.first == "user" }.map { it.second })
        bounded(result)
    }

    test("sustained input stays bounded including the omission marker") {
        val h = LiveHistory()
        repeat(300) { i ->
            h.add(true, if (i == 0) "Do not execute;" else "x".repeat(80), i * 2L, null)
        }
        check(h.omitted())
        check(h.users().isEmpty())
        repeat(50) { i ->
            h.cut(1000L + i * 2, null)
            h.add(true, "Unit-$i " + "x".repeat(80), 1001L + i * 2, null)
        }
        val result = h.snapshot()
        bounded(result)
        equal(1, result.count { it.first == "omitted" })
        check(result.any { it.second.startsWith("Unit-49 ") })
        check(result.filter { it.first == "user" }.all { it.second.startsWith("Unit-") })
    }

    test("Unicode limit counts code points without slicing an oversized unit") {
        val h = LiveHistory()
        val exact = "😀".repeat(1000)
        h.add(true, exact, 1, null)
        equal(listOf(exact), h.users())
        h.add(true, "😀", 3, null)
        check(h.omitted())
        check(h.users().isEmpty())
    }

    test("clear removes omission state and unblocks speakers") {
        val h = LiveHistory()
        h.add(true, "x".repeat(1001), 1, null)
        check(h.omitted())
        h.clear()
        check(h.snapshot().isEmpty())
        h.add(true, "A new question", 2, null)
        equal(listOf("A new question"), h.users())
        check(!h.omitted())
    }

    test("availability tradeoff: unrelated later input is omitted until a cut") {
        val h = LiveHistory()
        h.add(true, "Do not execute; " + "x".repeat(1000), 1, null)
        h.add(false, "A different answer", 3, null)
        h.add(true, "What is two plus two?", 5, null)
        check(h.users().isEmpty())
        h.cut(6, null)
        h.add(true, "What is two plus two?", 7, null)
        equal(listOf("What is two plus two?"), h.users())
    }

    test("either speaker can be blocked without blocking the other") {
        val h = LiveHistory()
        h.add(false, "x".repeat(1001), 1, null)
        h.add(false, "an incomplete suffix", 3, null)
        h.add(true, "A user question", 5, null)
        equal(listOf("A user question"), h.users())
        check(h.snapshot().none { it.first == "assistant" })
    }

    test("invalid imported roles and oversized speaker groups are omitted") {
        val h = LiveHistory()
        val result = h.snapshot(listOf(
            "user" to "x".repeat(1001), "assistant" to "An observation",
            "user" to "orphaned suffix", "system" to "An invalid role"
        ))
        check(result.any { it.first == "omitted" })
        equal(listOf("assistant" to "An observation"), result.filter { it.first != "omitted" })
        bounded(result)
    }

    test("post-boundary input survives only with the accepted interruption generation") {
        val h = LiveHistory()
        h.add(true, "Earlier question", 1, null)
        h.add(false, "Interrupted answer", 2, null)
        h.add(true, "Fresh owned input", 46, 7)
        h.cut(40, 7)
        equal(listOf("Earlier question", "Fresh owned input"), h.users())
        check(h.snapshot().none { it.first == "assistant" })
        check(h.omitted())
    }

    println("$passed deterministic tests passed")
}
