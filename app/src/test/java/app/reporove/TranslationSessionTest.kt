package app.reporove

import app.reporove.core.translation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationSessionTest {
    @Test fun slowRequestDoesNotBlockTheOtherSlotOrCacheAndScrollReprioritizes() = runTest {
        val blocks = (0..3).map { TranslationBlock("$it", "Documentation ".repeat(140), emptyList()) }
        var viewport = listOf("0", "1", "2"); val slow = CompletableDeferred<Unit>()
        val started = mutableListOf<String>(); val complete = mutableSetOf<String>()
        val session = TranslationSession(blocks, cached = { if (it.id == "2") "已缓存" else null }, save = { _, _ -> },
            translate = { batch -> flow { val block = batch.single(); started += block.id; if (block.id == "0") slow.await(); emit(TranslationUpdate(block.id, "翻译完成", true)) } },
            update = { if (it.complete) complete += it.id }, active = { true }, clock = { testScheduler.currentTime })
        val job = launch { session.run({ viewport }, { emptyList() }) }
        advanceTimeBy(300); runCurrent()
        assertTrue("Cached result must display while slow request is running", "2" in complete)
        assertTrue("1" in complete); assertFalse("0" in complete)
        viewport = listOf("3"); advanceTimeBy(300); runCurrent()
        assertTrue("New viewport fills the vacant slot", "3" in started && "3" in complete)
        slow.complete(Unit); advanceTimeBy(100); runCurrent(); assertTrue("0" in complete)
        job.cancelAndJoin()
    }
    @Test fun failedParagraphDoesNotPauseOthersAndOffscreenRetryIsEnqueuedDirectly() = runTest {
        val blocks = (0..2).map { TranslationBlock("$it", "Documentation ".repeat(140), emptyList()) }
        var viewport = listOf("0", "1"); var retries = emptyList<String>()
        var attempts = 0; val complete = mutableSetOf<String>()
        val session = TranslationSession(blocks, cached = { null }, save = { _, _ -> },
            translate = { batch -> flow {
                val block = batch.single()
                if (block.id == "0" && attempts++ == 0) throw TranslationFailure("bad paragraph", TranslationFailureKind.REQUEST)
                emit(TranslationUpdate(block.id, "完成", true))
            } }, update = { if (it.complete) complete += it.id }, active = { true }, clock = { testScheduler.currentTime })
        val job = launch { session.run({ viewport }, { retries.also { retries = emptyList() } }) }
        advanceTimeBy(500); runCurrent(); assertTrue("1" in complete); assertFalse("0" in complete)
        viewport = listOf("2"); retries = listOf("0")
        advanceTimeBy(1800); runCurrent()
        assertEquals(setOf("0", "1", "2"), complete); assertEquals(2, attempts)
        job.cancelAndJoin()
    }
    @Test fun completedPrefixSurvivesLaterStreamFailureAndNextSessionReadsItWithoutNetwork() = runTest {
        val blocks = listOf(TranslationBlock("a", "First paragraph", emptyList()), TranslationBlock("b", "Second paragraph", emptyList()))
        val disk = mutableMapOf<String, String>(); val output = mutableListOf<TranslationUpdate>()
        val job = launch { TranslationSession(blocks, cached = { disk[it.id] }, save = { block, text -> disk[block.id] = text },
            translate = { flow { emit(TranslationUpdate("a", "第一段", true)); throw TranslationFailure("bad tail", TranslationFailureKind.REQUEST) } },
            update = { output += it }, active = { true }, clock = { testScheduler.currentTime }).run({ listOf("a", "b") }, { emptyList() }) }
        advanceTimeBy(500); runCurrent(); assertEquals("第一段", disk["a"]); assertFalse(disk.containsKey("b"))
        job.cancelAndJoin()
        var calls = 0; output.clear()
        val next = launch { TranslationSession(blocks, cached = { disk[it.id] }, save = { _, _ -> }, translate = { flow { calls++ } },
            update = { output += it }, active = { true }, clock = { testScheduler.currentTime }).run({ listOf("a") }, { emptyList() }) }
        advanceTimeBy(300); runCurrent(); assertEquals(0, calls); assertTrue(output.single().complete)
        next.cancelAndJoin()
    }
}
