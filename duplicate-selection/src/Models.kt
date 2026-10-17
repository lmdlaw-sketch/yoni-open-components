package org.yonicomponents.duplicateselection

import java.io.File

enum class Marker { NONE, WEAK, STRONG }
data class NormalizedName(val stem: String, val extension: String, val marker: Marker) {
    val key: String get() = "$stem.$extension"
}
enum class FileKind { ALL, IMAGE, VIDEO, AUDIO, DOCUMENT }
data class FileRecord(
    val path: String,
    val size: Long,
    val modified: Long,
    val hash: String,
    val normalized: NormalizedName,
    val kind: FileKind,
) { val name: String get() = File(path).name; val parent: String get() = File(path).parent.orEmpty() }
enum class GroupKind { EXACT, NAME }
data class DuplicateGroup(val id: String, val kind: GroupKind, val files: List<FileRecord>, val keeperPath: String) {
    val reclaimable: Long get() = if (kind == GroupKind.EXACT) files.filter { it.path != keeperPath }.sumOf { it.size } else 0L
}

data class SelectionResult(val selected: Set<String>, val corrected: Set<String> = emptySet())
