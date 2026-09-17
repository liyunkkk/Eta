# 任务：实现参考 Operit AI 设计的结构化记忆卡片系统

## 1. 目标与背景
参考 Operit AI 的记忆系统设计（结构化卡片、分类空间、标签、重要度、紧凑注入、兼容 Operit 备份），在 Eta 中实现一套轻量、纯 Kotlin、无 NDK/向量库依赖的结构化记忆系统。

## 2. 数据模型 (io.github.mangi.eta.data.model.memory.MemoryCard)
- `id`: String (UUID)
- `title`: String (标题)
- `content`: String (提炼内容)
- `space`: String (分类空间，默认 "general"，支持 "工作", "生活", "开发", "用户信息" 等)
- `tags`: List<String> (标签列表)
- `importance`: Int (重要度 1 到 5，默认 3)
- `createdAt`: Long (创建时间戳)
- `updatedAt`: Long (更新时间戳)
- 支持转为 JSONObject 及从 JSONObject 反序列化。

## 3. 仓储层 (io.github.mangi.eta.data.repository.StructuredMemoryRepository)
- 持久化：基于文件系统，例如 `context.filesDir/structured_memories.json`，使用 AtomicFile 或线程互斥保证并发安全与原子读写。
- 核心功能：
  1. `saveCard(title: String, content: String, space: String = "general", tags: List<String> = emptyList(), importance: Int = 3): MemoryCard`
     - 若同 space 下已存在完全相同 title 的卡片，则就地更新 content, tags, importance, updatedAt；否则新建。
  2. `deleteCard(id: String): Boolean`
  3. `getCard(id: String): MemoryCard?`
  4. `listCards(space: String? = null): List<MemoryCard>`
  5. `queryCards(space: String? = null, tags: List<String>? = null, keyword: String? = null, limit: Int = 10): List<MemoryCard>`
     - 组合过滤：space 匹配、tags 包含任一/全部、keyword 在 title 或 content 中模糊匹配；
     - 排序：按 importance 降序，再按 updatedAt 降序。
  6. `toCompactPrompt(space: String? = null, limit: Int = 5): String`
     - 生成紧凑 Markdown 格式文本供上下文注入，例如：
       - [工作|规范] 工作目录规范: 统一使用 /workspace/EtaWorker，严禁本地重型编译
  7. `importFromOperitJson(jsonString: String): Int`
     - 解析 Operit 的 `memory_backup.json` 格式，提取 `memories` 数组；
     - 字段映射：
       - title -> title
       - content -> content
       - folderPath -> space (若为空则为 "general")
       - tagNames -> tags
       - importance (0.0~1.0 浮点数按比例映射到 1~5 整数：round(val * 4) + 1)
       - createdAt -> createdAt
       - updatedAt -> updatedAt
     - 返回成功导入的卡片数量。

## 4. Agent 工具注册 (io.github.mangi.eta.agent.model.AgentMemoryToolCatalog)
- 新增或扩展工具：
  - `memory_card_save`: 参数 (title: string, content: string, space?: string, tags?: string[], importance?: integer)
  - `memory_card_query`: 参数 (space?: string, tags?: string[], keyword?: string, limit?: integer)
  - `memory_card_delete`: 参数 (id: string)
- 在 Agent 工具调度逻辑中接入对应的执行逻辑。

## 5. 单元测试 (app/src/test/kotlin/...)
- 编写完整的单元测试覆盖：
  - 卡片增删改查与同名覆盖；
  - 空间与标签组合检索；
  - 紧凑 Prompt 生成；
  - Operit JSON 导入与数据校验。

## 6. 注意事项
- 严禁在手机本地执行 `./gradlew assembleRelease` 或 `./gradlew build` 等重型编译命令！
