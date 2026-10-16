package app.reporove

import app.reporove.core.network.AppJson
import app.reporove.core.reader.*
import app.reporove.core.translation.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class TranslationTest {
    @Test fun longParagraphChunksPreservePlaceholdersAndUnicodeAndTranslateNonLatinDocuments() {
        val text = "Start " + "x".repeat(59) + "__RR_12__" + "終わり。こんにちは世界。".repeat(20)
        val parts = TranslationText.parts(text, 64)
        assertTrue(parts.all { it.length <= 64 })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
        assertEquals(1, parts.count { "__RR_12__" in it })
        assertEquals(2, TranslationDocument.prepare("<p>これは日本語の説明です。</p><p>Описание проекта для чтения.</p>").blocks.size)
        assertTrue(runCatching { TranslationConfig(key = "invalid\u0000key").validated() }.isFailure)
    }
    @Test fun semanticParagraphKeepsEmphasisAndProtectsCodeAndLinkAsOneUnit() {
        val result = TranslationDocument.prepare("<p>Install <strong>the package</strong> with <code>npm install</code>, then read <a id='guide-link' href='https://example.com/docs'>the guide</a>.</p><pre><code>// English comment must not translate</code></pre>")
        assertEquals(1, result.blocks.size)
        val block = result.blocks.single()
        assertEquals("Install the package with __RR_0__, then read __RR_1__the guide__RR_2__.", block.text)
        assertEquals(3, block.protected.size)
        assertFalse(block.protected.any { it.contains("id=") })
        val rendered = Jsoup.parseBodyFragment(block.render("使用 __RR_0__ 安装，然后阅读 __RR_1__指南__RR_2__。"))
        assertEquals("npm install", rendered.selectFirst("code")?.text())
        assertEquals("https://example.com/docs", rendered.selectFirst("a")?.attr("href"))
        assertEquals("指南", rendered.selectFirst("a")?.text())
        assertFalse(block.valid("__RR_2__指南__RR_1__ __RR_0__"))
        assertTrue(result.html.contains("// English comment must not translate"))
    }
    @Test fun nestedListsAndTableCellsRemainInTheirOriginalStructureWithoutDuplicates() {
        val result = TranslationDocument.prepare("<ul><li>Parent information<ul><li>Nested information</li></ul></li><li><p>Paragraph information</p></li></ul><table><tr><td rowspan='2'><div class='table-cell'>Merged explanation</div></td><td><div class='table-cell'>12345</div></td></tr><tr><td><div class='table-cell'><p>Cell paragraph</p></div></td></tr></table>")
        assertEquals(listOf("Parent information", "Nested information", "Paragraph information", "Merged explanation", "Cell paragraph"), result.blocks.map { it.text })
        val doc = Jsoup.parse(result.html)
        assertEquals(2, doc.select("table tr").size)
        assertEquals("2", doc.selectFirst("td[rowspan]")?.attr("rowspan"))
        assertEquals(2, doc.select("ul").size)
        assertEquals(5, doc.select("[data-rr-source]").size)
    }
    @Test fun alreadyChineseNumbersBadgesAndCommandOnlyBlocksDoNotRequestTranslation() {
        assertTrue(TranslationDocument.prepare("<p>已经是中文的项目介绍，支持 Android。</p><p>2026.10.16</p><p><code>npm run build</code></p><p><a href='https://example.com'><img alt='Build passing'></a></p>").blocks.isEmpty())
    }
    @Test fun outputCannotInjectHtmlOrDropProtectedFragments() {
        val block = TranslationBlock("rr-0", "Use __RR_0__", listOf("<code>safe()</code>"))
        assertFalse(block.valid("漏掉代码")); assertFalse(block.valid("__RR_0__ __RR_0__")); assertFalse(block.valid("__RR_0__ __RR_99__"))
        val rendered = Jsoup.parseBodyFragment(block.render("<script>alert(1)</script> __RR_0__"))
        assertTrue(rendered.select("script").isEmpty()); assertEquals("safe()", rendered.selectFirst("code")?.text())
    }
    @Test fun tableSpansSurviveSanitizingButUnboundedAndInvalidSpansAreRejected() {
        val palette = ReaderPalette("#111111", "#555555", "#ffffff", "#dddddd", "#eeeeee", "#0000ff", "#008800", "#ff0000", "#800080", "#aa7700", false)
        val html = ReaderDocument.prepare("<table><tr><th colspan='2' style='text-align:right' onclick='alert(1)'>Feature</th></tr><tr><td rowspan='2'>Description</td><td colspan='999999' rowspan='bad'>Other</td></tr></table>", palette, 1f, null, "main", "README.md").html
        val doc = Jsoup.parse(html)
        assertEquals("2", doc.selectFirst("th")?.attr("colspan")); assertEquals("right", doc.selectFirst("th")?.attr("align"))
        assertEquals("2", doc.selectFirst("td")?.attr("rowspan")); assertFalse(doc.select("td").last()!!.hasAttr("colspan")); assertTrue(doc.select("[onclick]").isEmpty())
    }
    @Test fun settingsRejectInsecureUrlsAndSecretsNeverAppearInDebugDescription() {
        val valid = TranslationConfig(key = "test-secret-key").validated()
        assertFalse(valid.toString().contains(valid.key))
        listOf("http://example.com/v1", "https://user:pass@example.com/v1", "https://example.com/v1?key=secret", "https://example.com/v1#secret").forEach { endpoint -> assertTrue(runCatching { valid.copy(endpoint = endpoint).validated() }.isFailure) }
    }
    @Test fun nativeTranslationUsesSingleUserMessageAndSeparateTranslationOptions() {
        val request = TranslationClient().request(TranslationConfig(key = "test-key"), "Read __RR_0__the guide__RR_1__ and __RR_2__.", "a/b/README.md")
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val body = AppJson.parseToJsonElement(buffer.readUtf8()).jsonObject
        assertEquals(1, body["messages"]!!.jsonArray.size)
        assertEquals("user", body["messages"]!!.jsonArray.single().jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Chinese", body["translation_options"]!!.jsonObject["target_lang"]!!.jsonPrimitive.content)
        val terms = body["translation_options"]!!.jsonObject["terms"]!!.jsonArray
        assertEquals(setOf("source", "target"), terms.first().jsonObject.keys)
        assertEquals(listOf("__RR_0__", "__RR_1__", "__RR_2__"), terms.map { it.jsonObject["source"]!!.jsonPrimitive.content })
        assertEquals("Bearer test-key", request.header("Authorization"))
        assertNull(request.header("Cookie"))
    }
    @Test fun streamedDeltasCombineAndTruncatedResponsesFail() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"世界\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"半段\"}}]}\n\n"))
            val config = TranslationConfig(endpoint = server.url("/chat/completions").toString(), key = "fixture-key")
            assertEquals("你好世界", TranslationClient().translate(config, "hello world", "fixture").toList().last())
            assertTrue(runCatching { TranslationClient().translate(config, "hello world", "fixture").toList() }.exceptionOrNull() is TranslationFailure)
        }
    }
    @Test fun realSseFailureAfterACompleteParagraphStillDeliversThatParagraph() = runBlocking {
        MockWebServer().use { server ->
            val delta = buildJsonObject { putJsonArray("choices") { add(buildJsonObject { putJsonObject("delta") { put("content", "__RR_S0__第一段__RR_E0__\n__RR_S1__第二段未完") } }) } }
            val error = buildJsonObject { putJsonObject("error") { put("code", "invalid_parameter"); put("message", "Field required") } }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: $delta\n\ndata: $error\n\n"))
            val blocks = listOf(TranslationBlock("a", "First paragraph", emptyList()), TranslationBlock("b", "Second paragraph", emptyList()))
            val updates = mutableListOf<TranslationUpdate>()
            val config = TranslationConfig(endpoint = server.url("/chat/completions").toString(), key = "fixture-key")
            val failure = runCatching { TranslationClient().translateBlocks(config, blocks, "fixture").collect { updates += it } }.exceptionOrNull()
            assertTrue(failure is TranslationFailure)
            assertTrue(updates.any { it.id == "a" && it.complete && it.text == "第一段" })
            assertFalse(updates.any { it.id == "b" && it.complete })
        }
    }
    @Test fun sseParameterErrorsAreClassifiedWithoutRetryOrProviderDetails() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"error\":{\"code\":\"internal_server_error\",\"message\":\"2 validation errors: terms.0.source Field required secret-provider-detail\"}}\n\n"))
            val config = TranslationConfig(endpoint = server.url("/chat/completions").toString(), key = "fixture-key")
            val failure = runCatching { TranslationClient().translate(config, "Read __RR_0__", "fixture").toList() }.exceptionOrNull() as TranslationFailure
            assertEquals(TranslationFailureKind.REQUEST, failure.kind)
            assertFalse(failure.retryable); assertFalse(failure.pause)
            assertFalse(failure.message.orEmpty().contains("secret-provider-detail"))
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun paragraphBatchRemapsTokensAndCachesOnlyCompleteBoundaries() {
        val first = TranslationBlock("a", "Use __RR_0__", listOf("<code>npm run</code>"))
        val second = TranslationBlock("b", "Read __RR_0__the guide__RR_1__", listOf("<a href='https://example.com/old'>", "</a>"), listOf(0 to 1))
        val batch = TranslationBatch(listOf(first, second))
        assertTrue(batch.text.contains("__RR_P0_0__")); assertTrue(batch.text.contains("__RR_P1_0__"))
        val updates = batch.read("__RR_S0__使用 __RR_P0_0____RR_E0__\n__RR_S1__阅读 __RR_P1_0__指南__RR_P1_1__")
        assertTrue(updates[0].complete); assertFalse(updates[1].complete)
        assertTrue(first.valid(updates[0].text))
        val final = batch.read("__RR_S0__使用 __RR_P0_0____RR_E0__\n__RR_S1__阅读 __RR_P1_0__指南__RR_P1_1____RR_E1__")
        assertFalse(batch.read("__RR_S0__使用 __RR_P0_0__ __RR_S1__错位__RR_E0__").first().complete)
        val request = TranslationClient().request(TranslationConfig(key = "fixture-key"), batch.text, "fixture")
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        val terms = AppJson.parseToJsonElement(buffer.readUtf8()).jsonObject["translation_options"]!!.jsonObject["terms"]!!.jsonArray.map { it.jsonObject["source"]!!.jsonPrimitive.content }
        assertTrue(terms.containsAll(listOf("__RR_S0__", "__RR_E0__", "__RR_P0_0__")))
        assertTrue(final.all { it.complete }); assertTrue(second.render(final[1].text).contains("https://example.com/old"))
        assertEquals(first.cacheSignature(), first.copy(protected = listOf("<code class='other'>npm run</code>")).cacheSignature())
        assertEquals(second.cacheSignature(), second.copy(protected = listOf("<a href='https://example.com/new'>", "</a>")).cacheSignature())
        assertNotEquals(first.cacheSignature(), first.copy(protected = listOf("<code>npm install</code>")).cacheSignature())
    }
    @Test fun redirectNeverSendsTranslationKeyToAnotherHostAndRateLimitIsNotRetried() = runBlocking {
        MockWebServer().use { origin -> MockWebServer().use { destination ->
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/collect")))
            origin.enqueue(MockResponse().setResponseCode(429))
            val config = TranslationConfig(endpoint = origin.url("/chat/completions").toString(), key = "fixture-key")
            assertTrue((runCatching { TranslationClient().translate(config, "paragraph", "fixture").toList() }.exceptionOrNull() as TranslationFailure).pause)
            assertNull(destination.takeRequest(100, TimeUnit.MILLISECONDS))
            assertTrue((runCatching { TranslationClient().translate(config, "paragraph", "fixture").toList() }.exceptionOrNull() as TranslationFailure).pause)
            assertEquals(2, origin.requestCount)
        } }
    }
}
