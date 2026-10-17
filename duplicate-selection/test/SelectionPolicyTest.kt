package org.yonicomponents.duplicateselection

import java.io.File
import org.yonicomponents.duplicateselection.testing.assertEquals
import org.yonicomponents.duplicateselection.testing.assertFalse
import org.yonicomponents.duplicateselection.testing.assertThrows
import org.yonicomponents.duplicateselection.testing.assertTrue

class SelectionPolicyTest {
    fun exactSuggestionsRetainTheRecommendedKeeper() {
        val keeper = record("/storage/DCIM/photo.jpg", modified = 20)
        val other = record("/storage/Downloads/photo (1).jpg", modified = 1)
        assertEquals(setOf(other.path), SelectionPolicy.suggestions(listOf(exact("e", keeper, other))))
    }

    fun cannotCheckAllFilesEvenWhenAddingTheRecommendedKeeperLast() {
        val first = record("/storage/a.pdf")
        val second = record("/storage/b.pdf")
        val third = record("/storage/c.pdf")
        val groups = listOf(exact("e", first, second, third))
        assertTrue(SelectionPolicy.canSelect(groups, setOf(third.path), second.path))
        assertFalse(SelectionPolicy.canSelect(groups, setOf(second.path, third.path), first.path))
        val sanitized = SelectionPolicy.sanitize(groups, setOf(first.path, second.path, third.path))
        assertEquals(setOf(second.path, third.path), sanitized.selected)
        assertEquals(setOf(first.path), sanitized.corrected)
    }

    fun nameGroupsAreReportOnlyForUniqueContent() {
        val first = record("/storage/contract.pdf", hash = "a".repeat(64))
        val second = record("/storage/contract (1).pdf", hash = "b".repeat(64))
        val groups = listOf(name("n", first, second))
        assertTrue(SelectionPolicy.suggestions(groups).isEmpty())
        assertFalse(SelectionPolicy.canSelect(groups, emptySet(), first.path))
        val sanitized = SelectionPolicy.sanitize(groups, setOf(first.path, second.path))
        assertTrue(sanitized.selected.isEmpty())
        assertEquals(setOf(first.path, second.path), sanitized.corrected)
    }

    fun aFileMayBeSelectedFromANameGroupOnlyWhenAlsoAnExactDuplicate() {
        val keeper = record("/storage/DCIM/photo.jpg")
        val duplicate = record("/storage/Download/photo (1).jpg")
        val unique = record("/storage/Download/photo (2).jpg", hash = "b".repeat(64))
        val groups = listOf(exact("e", keeper, duplicate), name("n", keeper, duplicate, unique))
        assertEquals(setOf(duplicate.path), SelectionPolicy.suggestions(groups))
        assertTrue(SelectionPolicy.canSelect(groups, emptySet(), duplicate.path))
        assertFalse(SelectionPolicy.canSelect(groups, emptySet(), unique.path))
        val sanitized = SelectionPolicy.sanitize(groups, setOf(duplicate.path, unique.path))
        assertEquals(setOf(duplicate.path), sanitized.selected)
        assertEquals(setOf(unique.path), sanitized.corrected)
    }

    fun crossGroupSafetyProtectsANameGroupWithoutAKeeperPath() {
        val firstKeeper = record("/storage/DCIM/first.pdf", hash = "a".repeat(64))
        val secondKeeper = record("/storage/DCIM/second.pdf", hash = "b".repeat(64))
        val firstCopy = record("/storage/report (1).pdf", hash = "a".repeat(64), modified = 1)
        val secondCopy = record("/storage/report (2).pdf", hash = "b".repeat(64), modified = 2)
        val groups = listOf(
            exact("e1", firstKeeper, firstCopy),
            exact("e2", secondKeeper, secondCopy),
            name("n", secondCopy, firstCopy),
        )
        val desired = setOf(firstCopy.path, secondCopy.path)
        val sanitized = SelectionPolicy.sanitize(groups, desired)
        assertEquals(setOf(secondCopy.path), sanitized.selected)
        assertEquals(setOf(firstCopy.path), sanitized.corrected)
        assertEquals(sanitized.selected, SelectionPolicy.suggestions(groups))
        assertEquals(sanitized, SelectionPolicy.sanitize(groups.asReversed(), desired))
        assertFalse(SelectionPolicy.canSelect(groups, setOf(secondCopy.path), firstCopy.path))
    }

    fun aRepeatedPathIsNotTwoIndependentSurvivors() {
        val file = record("/storage/report.pdf")
        val groups = listOf(exact("e", file, file))
        assertTrue(SelectionPolicy.suggestions(groups).isEmpty())
        assertTrue(SelectionPolicy.sanitize(groups, setOf(file.path)).selected.isEmpty())
        assertFalse(SelectionPolicy.canSelect(groups, emptySet(), file.path))
    }

    fun emptyAndInvalidHashGroupsDoNotAuthorizeRemoval() {
        val first = record("/storage/a.pdf", hash = "")
        val second = record("/storage/b.pdf", hash = "")
        assertTrue(SelectionPolicy.suggestions(listOf(exact("empty", first, second))).isEmpty())
        val different = second.copy(hash = "b".repeat(64))
        assertTrue(SelectionPolicy.sanitize(listOf(exact("different", first, different)), setOf(first.path)).selected.isEmpty())
        assertTrue(SelectionPolicy.sanitize(emptyList(), setOf("/outside.pdf")).selected.isEmpty())
    }

    fun suggestedKeeperOrderIsDcimThenMarkerThenTimeThenLengthThenPath() {
        val dcimCopy = record("/storage/DCIM/picture (1).jpg", modified = 500)
        val otherOriginal = record("/storage/Download/picture.jpg", modified = 1)
        assertEquals(dcimCopy, chooseKeeper(listOf(otherOriginal, dcimCopy)))
        val misleading = record("/storage/DCIM-old/picture.jpg", modified = 0)
        assertEquals(dcimCopy, chooseKeeper(listOf(misleading, dcimCopy)))

        val marked = record("/storage/report (1).pdf", modified = 0)
        val unmarked = record("/storage/report.pdf", modified = 100)
        assertEquals(unmarked, chooseKeeper(listOf(marked, unmarked)))
        val oldest = record("/storage/very/long/name/old.pdf", modified = 1)
        assertEquals(oldest, chooseKeeper(listOf(unmarked, oldest)))
        val short = record("/s/a.pdf", modified = 1)
        assertEquals(short, chooseKeeper(listOf(oldest, short)))
        val sameLength = record("/s/b.pdf", modified = 1)
        assertEquals(short, chooseKeeper(listOf(sameLength, short)))
    }

    fun selectionSnapshotsCannotBeMutatedByCallers() {
        val first = record("/storage/a.pdf")
        val second = record("/storage/b.pdf")
        val result = SelectionPolicy.sanitize(listOf(exact("e", first, second)), setOf(first.path, second.path))
        assertThrows(UnsupportedOperationException::class.java) { (result.selected as MutableSet<String>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.corrected as MutableSet<String>).clear() }
    }

    private fun record(path: String, hash: String = "a".repeat(64), modified: Long = 10): FileRecord =
        FileRecord(path, 100, modified, hash, NameRules.normalize(File(path).name), FileKind.DOCUMENT)

    private fun exact(id: String, vararg files: FileRecord): DuplicateGroup =
        DuplicateGroup(id, GroupKind.EXACT, files.toList(), chooseKeeper(files.toList()).path)

    private fun name(id: String, vararg files: FileRecord): DuplicateGroup =
        DuplicateGroup(id, GroupKind.NAME, files.toList(), "")
}
