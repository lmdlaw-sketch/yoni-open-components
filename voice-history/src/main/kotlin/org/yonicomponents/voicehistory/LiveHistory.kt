package org.yonicomponents.voicehistory

/** Bounded observed text, separate from caption/request boundaries. No completion is inferred.
 * Consecutive deltas form one unit. Losing a unit discards its speaker's remaining units in
 * that generation and blocks further text until a real cut, not merely a backchannel. */
class LiveHistory {
    private data class UnitText(val speaker: String, var text: String, val generation: Long,
        val interruptionGeneration: Long?, var firstStart: Long, var lastStart: Long)
    private data class SnapshotUnit(val speaker: String, val text: String, val generation: Long?)
    private val units = mutableListOf<UnitText>()
    private val blockedSpeakers = mutableSetOf<String>()
    private var generation = 0L
    private var omitted = false

    fun add(input: Boolean, text: String, start: Long, interruptionGeneration: Long?) {
        if (text.isEmpty()) return
        val speaker = if (input) "user" else "assistant"
        if (speaker in blockedSpeakers) return
        val last = units.lastOrNull()
        val unit = if (last != null && last.generation == generation && last.speaker == speaker &&
            last.interruptionGeneration == interruptionGeneration) last
        else UnitText(speaker, "", generation, interruptionGeneration, start, start).also(units::add)
        unit.firstStart = minOf(unit.firstStart, start)
        unit.lastStart = maxOf(unit.lastStart, start)
        val combined = unit.text + text
        if (combined.codePointCount(0, combined.length) > MAX_POINTS) discardSpeaker(unit)
        else unit.text = combined
        trim()
    }

    fun cut(offset: Long, acceptedInterruptionGeneration: Long?) {
        // The interrupted output is observed caption text, not a valid completed answer.
        // New input only survives when owned by the acknowledged cut and after its boundary.
        val removed = units.removeAll { unit -> unit.generation == generation &&
            (unit.speaker == "assistant" ||
                if (unit.interruptionGeneration != null) {
                    unit.interruptionGeneration != acceptedInterruptionGeneration || unit.firstStart <= offset
                } else unit.lastStart > offset)
        }
        if (removed) omitted = true
        generation++
        blockedSpeakers.clear()
        trim()
    }

    fun clear() { units.clear(); blockedSpeakers.clear(); generation = 0; omitted = false }

    fun snapshot(previous: List<Pair<String, String>>): List<Pair<String, String>> {
        var missing = omitted || previous.any { it.first == "omitted" }
        // Imported snapshots no longer carry cut generations. Treat each imported speaker
        // conservatively as one group rather than leaving an orphaned suffix after eviction.
        val invalidSpeakers = previous.filter { it.first != "omitted" &&
            (it.first !in setOf("user", "assistant") || it.second.codePointCount(0, it.second.length) > MAX_POINTS) }
            .map { it.first }.toSet()
        if (invalidSpeakers.isNotEmpty()) missing = true
        val data = (previous.filter { it.first != "omitted" && it.first !in invalidSpeakers && it.second.isNotEmpty() }
            .map { SnapshotUnit(it.first, it.second, null) } +
            units.map { SnapshotUnit(it.speaker, it.text, it.generation) }).toMutableList()
        while (data.size + (if (missing) 1 else 0) > MAX_UNITS ||
            data.sumOf { bytes(it.text) } + (if (missing) bytes(OMISSION) else 0) > MAX_BYTES) {
            val first = data.first()
            data.removeAll { it.speaker == first.speaker && it.generation == first.generation }
            missing = true
        }
        return (if (missing) listOf("omitted" to OMISSION) else emptyList()) + data.map { it.speaker to it.text }
    }

    private fun trim() {
        while (units.size + (if (omitted) 1 else 0) > MAX_UNITS ||
            units.sumOf { bytes(it.text) } + (if (omitted) bytes(OMISSION) else 0) > MAX_BYTES) {
            discardSpeaker(units.first())
        }
    }

    private fun discardSpeaker(unit: UnitText) {
        if (unit.generation == generation) blockedSpeakers += unit.speaker
        units.removeAll { it.speaker == unit.speaker && it.generation == unit.generation }
        omitted = true
    }

    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8).size

    private companion object {
        const val MAX_UNITS = 32
        const val MAX_POINTS = 1000
        const val MAX_BYTES = 5000
        const val OMISSION = "Earlier or incomplete observed context was omitted; do not infer its contents."
    }
}
