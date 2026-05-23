package com.crosspaste.task

import com.crosspaste.app.AppControl
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.task.PasteTaskExtraInfo
import com.crosspaste.db.task.SyncExtraInfo
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopTaskBuilderTest {

    private fun newBuilder(): Triple<DesktopTaskBuilder, TaskDao, AppControl> {
        val appControl = mockk<AppControl>(relaxed = true)
        every { appControl.isFileSizeSyncEnabled(any()) } returns true
        val configManager = mockk<DesktopConfigManager>(relaxed = true)
        val taskDao = mockk<TaskDao>(relaxed = true)
        every { taskDao.createTaskBlock(any(), any(), any()) } returns 42L
        val builder = DesktopTaskBuilder(appControl, configManager, taskDao)
        return Triple(builder, taskDao, appControl)
    }

    @Test
    fun `addSyncTask defaults forcePush to false in SyncExtraInfo`() {
        val (builder, taskDao, _) = newBuilder()
        val captured = slot<PasteTaskExtraInfo>()

        builder.addSyncTask(
            id = 1L,
            fileSize = 0L,
            appInstanceId = "local-app",
            targetAppInstanceIds = setOf("remote-1"),
        )

        verify {
            taskDao.createTaskBlock(
                pasteDataId = 1L,
                taskType = TaskType.SYNC_PASTE_TASK,
                extraInfo = capture(captured),
            )
        }

        val syncExtra = captured.captured as SyncExtraInfo
        assertEquals("local-app", syncExtra.appInstanceId)
        assertEquals(setOf("remote-1"), syncExtra.targetAppInstanceIds)
        assertFalse(syncExtra.forcePush, "default forcePush must be false")
    }

    @Test
    fun `addSyncTask propagates forcePush=true to SyncExtraInfo`() {
        val (builder, taskDao, _) = newBuilder()
        val captured = slot<PasteTaskExtraInfo>()

        builder.addSyncTask(
            id = 2L,
            fileSize = 1024L,
            appInstanceId = "local-app",
            targetAppInstanceIds = setOf("remote-1"),
            forcePush = true,
        )

        verify {
            taskDao.createTaskBlock(
                pasteDataId = 2L,
                taskType = TaskType.SYNC_PASTE_TASK,
                extraInfo = capture(captured),
            )
        }

        val syncExtra = captured.captured as SyncExtraInfo
        assertTrue(syncExtra.forcePush, "forcePush=true must reach SyncExtraInfo")
    }

    @Test
    fun `addSyncTask skips task when file size disallowed`() {
        val appControl = mockk<AppControl>(relaxed = true)
        every { appControl.isFileSizeSyncEnabled(any()) } returns false
        val taskDao = mockk<TaskDao>(relaxed = true)
        val builder =
            DesktopTaskBuilder(
                appControl,
                mockk<DesktopConfigManager>(relaxed = true),
                taskDao,
            )

        builder.addSyncTask(
            id = 3L,
            fileSize = 999_999_999L,
            appInstanceId = "local-app",
            forcePush = true,
        )

        verify(exactly = 0) {
            taskDao.createTaskBlock(any(), eq(TaskType.SYNC_PASTE_TASK), any())
        }
        assertTrue(builder.getTasks().isEmpty())
    }
}
