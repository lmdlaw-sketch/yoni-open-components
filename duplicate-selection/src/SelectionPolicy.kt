package org.yonicomponents.duplicateselection

import java.io.File
import java.util.Collections

/** A path has one selection state, even when several result groups contain it. */
object SelectionPolicy {
    fun suggestions(groups: List<DuplicateGroup>): Set<String> {
        val requested = exactGroups(groups).flatMap { group ->
            val keeper = keeperForGroup(group)
            group.files.map { it.path }.filter { it != keeper }
        }.toSet()
        return sanitize(groups, requested).selected
    }

    fun sanitize(groups: List<DuplicateGroup>, requested: Set<String>): SelectionResult {
        val exact = exactGroups(groups)
        val allowed = exact.flatMap { it.files }.mapTo(HashSet()) { it.path }
        val selected = requested.filterTo(sortedSetOf()) { it in allowed }

        // Removing a selection can only add a survivor, so a single ordered pass
        // suffices even for overlapping groups. NAME groups have no public keeper.
        for (group in groups.sortedWith(compareBy({ it.kind.ordinal }, { it.id }))) {
            val paths = group.files.map { it.path }.toSet()
            if (paths.isNotEmpty() && paths.all { it in selected }) {
                selected.remove(keeperForGroup(group))
            }
        }

        // Check content sets independently of presentation groups as well.
        val contentSets = groups.flatMap { it.files }.distinctBy { it.path }
            .filter { it.hash.isNotEmpty() }
            .groupBy { it.size to it.hash }
        for (files in contentSets.values) {
            if (files.size >= 2 && files.all { it.path in selected }) {
                selected.remove(chooseKeeper(files).path)
            }
        }
        return SelectionResult(
            selected = immutableSet(selected),
            corrected = immutableSet(requested - selected),
        )
    }

    /** The caller can add [path] without silently deselecting another row. */
    fun canSelect(groups: List<DuplicateGroup>, selected: Set<String>, path: String): Boolean {
        val desired = selected + path
        return sanitize(groups, desired).selected == desired
    }

    private fun exactGroups(groups: List<DuplicateGroup>): List<DuplicateGroup> = groups.filter { group ->
        if (group.kind != GroupKind.EXACT) return@filter false
        val files = group.files.distinctBy { it.path }
        val first = files.firstOrNull() ?: return@filter false
        files.size >= 2 && first.hash.isNotEmpty() && files.all { it.size == first.size && it.hash == first.hash }
    }

    private fun keeperForGroup(group: DuplicateGroup): String {
        if (group.keeperPath.isNotEmpty() && group.files.any { it.path == group.keeperPath }) {
            return group.keeperPath
        }
        return chooseKeeper(group.files).path
    }

    private fun immutableSet(paths: Collection<String>): Set<String> =
        Collections.unmodifiableSet(paths.toSortedSet())
}

internal val keeperOrder: Comparator<FileRecord> = compareBy<FileRecord>(
    { if (isInDcim(it.path)) 0 else 1 },
    { if (it.normalized.marker == Marker.NONE) 0 else 1 },
    { it.modified },
    { it.path.length },
    { it.path },
)

internal fun chooseKeeper(files: Collection<FileRecord>): FileRecord =
    requireNotNull(files.minWithOrNull(keeperOrder)) { "An empty group has no keeper" }

private fun isInDcim(path: String): Boolean =
    File(path).parentFile?.toPath()?.any { it.toString().equals("DCIM", ignoreCase = true) } == true
