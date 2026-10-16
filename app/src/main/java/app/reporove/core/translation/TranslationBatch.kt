package app.reporove.core.translation

/** Small batches reduce request overhead; every paragraph retains its own cache and validation. */
class TranslationBatch(val blocks: List<TranslationBlock>) {
    val text = blocks.mapIndexed { index, block ->
        val encoded = Regex("__RR_(\\d+)__").replace(block.text) { "__RR_P${index}_${it.groupValues[1]}__" }
        "__RR_S${index}__\n$encoded\n__RR_E${index}__"
    }.joinToString("\n\n")

    fun read(output: String): List<TranslationUpdate> = blocks.mapIndexedNotNull { index, block ->
        val start = "__RR_S${index}__"; val end = "__RR_E${index}__"
        val from = output.indexOf(start)
        if (from < 0) return@mapIndexedNotNull null
        val to = output.indexOf(end, from + start.length)
        val raw = output.substring(from + start.length, if (to < 0) output.length else to).trim()
        val decoded = Regex("__RR_P${index}_(\\d+)__").replace(raw) { TranslationBlock.token(it.groupValues[1].toInt()) }
        val complete = to >= 0 && output.indexOf(start, from + start.length) < 0 && output.indexOf(end, to + end.length) < 0 && block.valid(decoded) && !Regex("__RR_(?:[SE]\\d+|P\\d+_\\d+)__").containsMatchIn(decoded)
        TranslationUpdate(block.id, decoded, complete)
    }
    companion object {
        fun pack(candidates: List<TranslationBlock>): List<TranslationBlock> {
            val result = mutableListOf<TranslationBlock>(); var characters = 0
            for (block in candidates) {
                val size = block.text.length + 80 + block.protected.size * 8
                if (result.isNotEmpty() && (result.size >= 4 || characters + size > 1600)) break
                result += block; characters += size
            }
            return result
        }
    }
}
data class TranslationUpdate(val id: String, val text: String, val complete: Boolean = false, val failure: TranslationFailure? = null)
