package io.github.mangi.eta.data.repository

import io.github.mangi.eta.data.model.memory.MemoryCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StructuredMemoryRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun createRepo(): StructuredMemoryRepository {
        val file = File(temporaryFolder.newFolder(), "test_memories.json")
        return StructuredMemoryRepository(file)
    }

    @Test
    fun testSaveAndQueryCards() {
        val repo = createRepo()
        val card1 = repo.saveCard(
            title = "工作目录",
            content = "统一保存在 EtaWorker 下",
            space = "开发",
            tags = listOf("规范", "路径"),
            importance = 5,
        )
        val card2 = repo.saveCard(
            title = "音色需求",
            content = "不支持通用音色包直接导入",
            space = "偏好",
            tags = listOf("音频"),
            importance = 2,
        )

        assertEquals("工作目录", card1.title)
        assertEquals("开发", card1.space)
        assertEquals(5, card1.importance)

        val queryResult = repo.queryCards(space = "开发")
        assertEquals(1, queryResult.size)
        assertEquals(card1.id, queryResult[0].id)

        val allCards = repo.listCards()
        assertEquals(2, allCards.size)
        assertEquals(card1.id, allCards[0].id)
    }

    @Test
    fun testSameTitleInSameSpaceOverwrites() {
        val repo = createRepo()
        val card1 = repo.saveCard(
            title = "编译规则",
            content = "初版规则",
            space = "开发",
            tags = listOf("规则"),
            importance = 3,
        )
        val card2 = repo.saveCard(
            title = "编译规则",
            content = "更新后的规则：严禁本地重型编译",
            space = "开发",
            tags = listOf("规则", "性能"),
            importance = 5,
        )

        assertEquals(card1.id, card2.id)
        assertEquals("更新后的规则：严禁本地重型编译", card2.content)
        assertEquals(5, card2.importance)
        assertTrue(card2.tags.contains("性能"))

        val list = repo.listCards("开发")
        assertEquals(1, list.size)
    }

    @Test
    fun testCompactPromptFormatting() {
        val repo = createRepo()
        repo.saveCard(
            title = "工作目录规范",
            content = "统一保存在 Download/EtaWorker\n第二行换行测试",
            space = "开发",
            tags = listOf("规范", "路径"),
            importance = 5,
        )

        val prompt = repo.toCompactPrompt(space = "开发", limit = 1)
        assertTrue(prompt.startsWith("- [开发|规范,路径] 工作目录规范:"))
        assertFalse(prompt.contains("\n"))
    }

    @Test
    fun testImportFromOperitJson() {
        val repo = createRepo()
        val operitJson = """{
            "memories": [
                {
                    "uuid": "941b24eb-b5a0-44d7-8e01-2fc5c456daa5",
                    "title": "用户基本信息",
                    "content": "用户是一名工程师，偏好专业严谨。",
                    "folderPath": "用户信息",
                    "importance": 0.5,
                    "tagNames": ["工程师", "偏好"],
                    "createdAt": 1785572235639,
                    "updatedAt": 1785572235639
                },
                {
                    "uuid": "1cf58cbd-a425-4824-99f5-2c811143e2c2",
                    "title": "CloneTTS 音色需求",
                    "content": "不支持第三方通用音色包直接导入。",
                    "folderPath": "偏好",
                    "importance": 0.9,
                    "tagNames": ["CloneTTS"],
                    "createdAt": 1785671658917,
                    "updatedAt": 1786700392738
                }
            ]
        }"""

        val count = repo.importFromOperitJson(operitJson)
        assertEquals(2, count)

        val userInfoCards = repo.queryCards(space = "用户信息")
        assertEquals(1, userInfoCards.size)
        val card = userInfoCards[0]
        assertEquals("用户基本信息", card.title)
        assertEquals(3, card.importance)
        assertTrue(card.tags.contains("工程师"))

        val ttsCards = repo.queryCards(space = "偏好")
        assertEquals(1, ttsCards.size)
        assertEquals(5, ttsCards[0].importance)
    }
}
