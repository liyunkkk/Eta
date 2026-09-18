package io.github.mangi.eta.data.db

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务附件编解码契约测试。
 *
 * 这里固化的是一条真实的回归红线：早期实现里 [TaskAttachmentCodec.encode] 用字符串
 * 拼接产出**扁平**数组 `["image","data:...","image/png"]`，而 [TaskAttachmentCodec.decode]
 * 按 `optJSONArray(index)` 期望**嵌套**数组 `[["image","data:...","image/png"]]`。
 * 两者错位时不会报错，只会静默把每个附件丢掉——表现成"排队时带了图，执行时图没了"。
 * 因此下面既测往返一致性，也单独钉死线上 JSON 结构本身。
 */
class TaskAttachmentCodecTest {

    @Test
    fun emptyListEncodesToEmptyArrayLiteral() {
        assertEquals("[]", TaskAttachmentCodec.encode(emptyList()))
    }

    @Test
    fun encodedPayloadIsNestedArrayNotFlatArray() {
        val raw = TaskAttachmentCodec.encode(listOf(TaskAttachment.image("data:image/png;base64,AA", "image/png")))

        // 结构必须与外层数组的元素类型一致：每个元素自身是数组。
        val outer = JSONArray(raw)
        assertEquals(1, outer.length())
        val inner = outer.optJSONArray(0)
        assertTrue("附件必须编码为嵌套数组，否则 decode 会静默丢弃", inner != null)
        assertEquals("image", inner?.optString(0))
        assertEquals("data:image/png;base64,AA", inner?.optString(1))
        assertEquals("image/png", inner?.optString(2))
    }

    @Test
    fun imageAttachmentRoundTrip() {
        val attachment = TaskAttachment.image("data:image/jpeg;base64,/9j/4AAQ", "image/jpeg")
        val decoded = TaskAttachmentCodec.decode(TaskAttachmentCodec.encode(listOf(attachment)))
        assertEquals(listOf(attachment), decoded)
    }

    @Test
    fun fileAttachmentRoundTripKeepsKindAndDefaultMime() {
        val attachment = TaskAttachment.file("/workspace/project/app/src/main.kt")
        val decoded = TaskAttachmentCodec.decode(TaskAttachmentCodec.encode(listOf(attachment)))
        assertEquals(listOf(attachment), decoded)
        assertEquals(TaskAttachment.KIND_FILE, decoded.single().kind)
        assertEquals("", decoded.single().mime)
    }

    @Test
    fun mixedAttachmentsPreserveOrder() {
        val attachments = listOf(
            TaskAttachment.image("data:image/png;base64,AA", "image/png"),
            TaskAttachment.file("/workspace/a.md"),
            TaskAttachment(TaskAttachment.KIND_FILE, "/workspace/b.json", "application/json"),
        )
        val decoded = TaskAttachmentCodec.decode(TaskAttachmentCodec.encode(attachments))
        assertEquals(attachments, decoded)
        assertEquals(listOf("image", "file", "file"), decoded.map { it.kind })
    }

    @Test
    fun roundTripSurvivesCharactersThatBreakNaiveStringConcatenation() {
        // 逗号、方括号、引号、反斜杠、换行与中文都会击穿手写拼接的编码器。
        val values = listOf(
            "data:image/png;base64,AAA,BBB",
            "data:image/png;base64,AA[BB]CC",
            "路径/含 空格/与\"引号\".txt",
            "C:\\Users\\test\\file.txt",
            "多行\n值\r\n第二行",
            "emoji🙂与中文",
        )
        values.forEach { value ->
            val attachment = TaskAttachment(TaskAttachment.KIND_FILE, value, "text/plain")
            val decoded = TaskAttachmentCodec.decode(TaskAttachmentCodec.encode(listOf(attachment)))
            assertEquals("值 $value 未能在往返中保真", listOf(attachment), decoded)
        }
    }

    @Test
    fun decodeTreatsBlankAndNullAsEmpty() {
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode(null))
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode(""))
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode("   "))
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode("[]"))
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode(" [ ] "))
    }

    @Test
    fun decodeDegradesToEmptyOnMalformedJson() {
        // 队列数据可能被历史版本或人工改动污染，解析失败必须退化为空而非抛异常，
        // 否则一条脏记录会永久卡死整个任务队列。
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode("{"))
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode("not json at all"))
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode("[\"image\"]"))
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode("{\"kind\":\"image\"}"))
    }

    @Test
    fun decodeRejectsLegacyFlatShapeToPinCurrentContract() {
        // 显式钉死旧格式不再被接受：若将来有人把 encode 改回扁平数组，
        // 这个测试会立刻失败，而不是等到用户发现附件静默消失。
        val legacyFlat = JSONArray()
            .put("image")
            .put("data:image/png;base64,AA")
            .put("image/png")
            .toString()
        assertEquals(emptyList<TaskAttachment>(), TaskAttachmentCodec.decode(legacyFlat))
    }

    @Test
    fun decodeSkipsInvalidEntriesButKeepsValidOnes() {
        val raw = JSONArray()
            .put(JSONArray().put("image").put("data:image/png;base64,AA").put("image/png"))
            .put(JSONArray().put("").put("value-without-kind").put(""))
            .put(JSONArray().put("file").put("").put(""))
            .put(JSONObject().put("kind", "image"))
            .put(JSONArray().put("file").put("/workspace/ok.txt"))
            .toString()

        val decoded = TaskAttachmentCodec.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals(listOf("image", "file"), decoded.map { it.kind })
        assertEquals("/workspace/ok.txt", decoded.last().value)
    }

    @Test
    fun decodeTrimsKindSoWhitespaceDoesNotLeakIntoKind() {
        val raw = JSONArray()
            .put(JSONArray().put(" image ").put("data:image/png;base64,AA").put("image/png"))
            .toString()
        assertEquals(TaskAttachment.KIND_IMAGE, TaskAttachmentCodec.decode(raw).single().kind)
    }

    @Test
    fun factoriesMapToExpectedKinds() {
        assertEquals(TaskAttachment.KIND_IMAGE, TaskAttachment.image("data:image/png;base64,AA").kind)
        assertEquals(TaskAttachment.KIND_FILE, TaskAttachment.file("/workspace/a.txt").kind)
    }
}
