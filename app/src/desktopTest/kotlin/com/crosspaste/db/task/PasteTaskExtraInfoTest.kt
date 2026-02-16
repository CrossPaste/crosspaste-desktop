package com.crosspaste.db.task

import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasteTaskExtraInfoTest {

    private val json = getJsonUtils().JSON

    // --- BaseExtraInfo ---

    @Test
    fun `BaseExtraInfo serialization roundtrip`() {
        val original: PasteTaskExtraInfo = BaseExtraInfo()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded)
        assertTrue(decoded is BaseExtraInfo)
        assertTrue(decoded.executionHistories.isEmpty())
    }

    @Test
    fun `BaseExtraInfo with execution histories roundtrip`() {
        val original = BaseExtraInfo()
        original.executionHistories.add(
            ExecutionHistory(
                startTime = 1000L,
                endTime = 2000L,
                status = TaskStatus.SUCCESS,
                message = "completed",
            )
        )
        val encoded = json.encodeToString(original as PasteTaskExtraInfo)
        val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded)

        assertTrue(decoded is BaseExtraInfo)
        assertEquals(1, decoded.executionHistories.size)
        assertEquals(1000L, decoded.executionHistories[0].startTime)
        assertEquals(2000L, decoded.executionHistories[0].endTime)
        assertEquals(TaskStatus.SUCCESS, decoded.executionHistories[0].status)
        assertEquals("completed", decoded.executionHistories[0].message)
    }

    // --- SyncExtraInfo ---

    @Test
    fun `SyncExtraInfo serialization roundtrip`() {
        val original: PasteTaskExtraInfo = SyncExtraInfo(appInstanceId = "test-app-1")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded)

        assertTrue(decoded is SyncExtraInfo)
        assertEquals("test-app-1", decoded.appInstanceId)
        assertTrue(decoded.syncFails.isEmpty())
    }

    @Test
    fun `SyncExtraInfo with syncFails roundtrip`() {
        val original = SyncExtraInfo(appInstanceId = "app-1")
        original.syncFails.add("remote-1")
        original.syncFails.add("remote-2")
        original.executionHistories.add(
            ExecutionHistory(1000, 2000, TaskStatus.FAILURE, "network error")
        )

        val encoded = json.encodeToString(original as PasteTaskExtraInfo)
        val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded) as SyncExtraInfo

        assertEquals("app-1", decoded.appInstanceId)
        assertEquals(2, decoded.syncFails.size)
        assertTrue(decoded.syncFails.contains("remote-1"))
        assertTrue(decoded.syncFails.contains("remote-2"))
        assertEquals(1, decoded.executionHistories.size)
    }

    // --- PullExtraInfo ---

    @Test
    fun `PullExtraInfo serialization roundtrip`() {
        val original: PasteTaskExtraInfo = PullExtraInfo(id = 42L)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded)

        assertTrue(decoded is PullExtraInfo)
        assertEquals(42L, decoded.id)
    }

    @Test
    fun `PullExtraInfo with pullChunks roundtrip`() {
        val original = PullExtraInfo(id = 10L)
        original.pullChunks = intArrayOf(0, 1, 2, 3)

        val encoded = json.encodeToString(original as PasteTaskExtraInfo)
        val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded) as PullExtraInfo

        assertEquals(10L, decoded.id)
        assertEquals(4, decoded.pullChunks.size)
        assertEquals(0, decoded.pullChunks[0])
        assertEquals(3, decoded.pullChunks[3])
    }

    @Test
    fun `PullExtraInfo empty pullChunks roundtrip`() {
        val original = PullExtraInfo(id = 1L)
        // pullChunks defaults to empty

        val encoded = json.encodeToString(original as PasteTaskExtraInfo)
        val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded) as PullExtraInfo

        assertTrue(decoded.pullChunks.isEmpty())
    }

    // --- SwitchLanguageInfo ---

    @Test
    fun `SwitchLanguageInfo serialization roundtrip`() {
        val original: PasteTaskExtraInfo = SwitchLanguageInfo(language = "zh-CN")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded)

        assertTrue(decoded is SwitchLanguageInfo)
        assertEquals("zh-CN", decoded.language)
    }

    // --- ExecutionHistory ---

    @Test
    fun `ExecutionHistory serialization roundtrip`() {
        val original = ExecutionHistory(
            startTime = 100L,
            endTime = 200L,
            status = TaskStatus.EXECUTING,
            message = null,
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ExecutionHistory>(encoded)

        assertEquals(100L, decoded.startTime)
        assertEquals(200L, decoded.endTime)
        assertEquals(TaskStatus.EXECUTING, decoded.status)
        assertEquals(null, decoded.message)
    }

    @Test
    fun `ExecutionHistory with message roundtrip`() {
        val original = ExecutionHistory(
            startTime = 100L,
            endTime = 200L,
            status = TaskStatus.FAILURE,
            message = "Connection timeout",
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ExecutionHistory>(encoded)

        assertEquals("Connection timeout", decoded.message)
    }

    // --- Polymorphic deserialization ---

    @Test
    fun `different PasteTaskExtraInfo types can be deserialized polymorphically`() {
        val infos: List<PasteTaskExtraInfo> = listOf(
            BaseExtraInfo(),
            SyncExtraInfo(appInstanceId = "app-1"),
            PullExtraInfo(id = 1L),
            SwitchLanguageInfo(language = "en"),
        )

        for (info in infos) {
            val encoded = json.encodeToString(info)
            val decoded = json.decodeFromString<PasteTaskExtraInfo>(encoded)
            assertEquals(info::class, decoded::class)
        }
    }
}
