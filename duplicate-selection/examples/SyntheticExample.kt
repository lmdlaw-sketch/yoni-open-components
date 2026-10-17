package org.yonicomponents.duplicateselection

import java.io.File

fun runSyntheticExample() {
    fun record(path: String, hash: String) = FileRecord(
        path = path, size = 100, modified = 10, hash = hash,
        normalized = NameRules.normalize(File(path).name), kind = FileKind.DOCUMENT,
    )
    val firstKeeper = record("/synthetic/DCIM/first.pdf", "a".repeat(64))
    val secondKeeper = record("/synthetic/DCIM/second.pdf", "b".repeat(64))
    val firstCopy = record("/synthetic/Inbox/report (1).pdf", "a".repeat(64))
    val secondCopy = record("/synthetic/Inbox/report (2).pdf", "b".repeat(64))
    val exact = listOf(
        DuplicateGroup("exact-a", GroupKind.EXACT, listOf(firstKeeper, firstCopy), firstKeeper.path),
        DuplicateGroup("exact-b", GroupKind.EXACT, listOf(secondKeeper, secondCopy), secondKeeper.path),
    )
    val names = DuplicateGroup("names", GroupKind.NAME, listOf(firstCopy, secondCopy), "")
    val requested = setOf(firstCopy.path, secondCopy.path)
    val result = SelectionPolicy.sanitize(exact + names, requested)
    println("Synthetic overlap: requested=$requested")
    println("Synthetic overlap: selected=${result.selected}, corrected=${result.corrected}")
    println("Name-only selection: ${SelectionPolicy.suggestions(listOf(names))}")
}
