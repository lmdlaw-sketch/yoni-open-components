package org.yonicomponents.voicehistory

/** This program does not listen, connect to a service, or execute a reminder. */
fun main() {
    val history = LiveHistory()
    history.add(true, "Do not create a reminder; " + "x".repeat(1000), 1, null)
    history.add(false, "Okay?", 3, null)
    history.add(true, "remind me tomorrow", 5, null)
    println("After overflow and a backchannel: ${history.snapshot(emptyList())}")

    history.add(true, "What is two plus two?", 7, null)
    println("Unrelated later input is also omitted: ${history.snapshot(emptyList())}")

    history.cut(8, null)
    history.add(true, "What is two plus two?", 9, null)
    println("After an acknowledged cut: ${history.snapshot(emptyList())}")
}
