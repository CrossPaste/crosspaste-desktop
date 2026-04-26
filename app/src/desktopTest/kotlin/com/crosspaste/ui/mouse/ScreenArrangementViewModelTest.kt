package com.crosspaste.ui.mouse

import com.crosspaste.mouse.IpcEvent
import com.crosspaste.mouse.LocalScreensProvider
import com.crosspaste.mouse.MouseLayoutStore
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.ScreenInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenArrangementViewModelTest {

    private fun store(initial: Map<String, Position> = emptyMap()): MouseLayoutStore {
        val backing =
            object : MouseLayoutStore.Backing {
                val flow = MutableStateFlow(initial)

                override fun snapshot() = flow.value

                @Synchronized
                override fun update(updater: (Map<String, Position>) -> Map<String, Position>) {
                    flow.value = updater(flow.value)
                }

                override fun flow(): kotlinx.coroutines.flow.Flow<Map<String, Position>> = flow
            }
        return MouseLayoutStore(backing)
    }

    @Test
    fun `learns local and remote screens from events`() =
        runTest {
            val events = MutableSharedFlow<IpcEvent>(replay = 0, extraBufferCapacity = 16)
            val vm = ScreenArrangementViewModel(events.asSharedFlow(), store())
            val job = launch { vm.observe() }
            yield()
            events.emit(
                IpcEvent.Initialized(
                    screens = listOf(ScreenInfo(0, 1920, 1080, 0, 0, 1.0, true)),
                    protocolVersion = 2,
                ),
            )
            events.emit(
                IpcEvent.PeerScreensLearned(
                    deviceId = "inst-1",
                    screens = listOf(ScreenInfo(0, 2560, 1440, 0, 0, 2.0, true)),
                ),
            )
            yield()
            assertEquals(1, vm.localScreens.value.size)
            assertEquals(
                2560,
                vm.remoteDevices.value["inst-1"]!!
                    .screens
                    .single()
                    .width,
            )
            job.cancel()
        }

    @Test
    fun `drag updates in-memory position without committing`() {
        val s = store(mapOf("inst-1" to Position(1920, 0)))
        val vm = ScreenArrangementViewModel(MutableSharedFlow(), s)
        vm.seedRemote("inst-1", "Desktop", listOf(ScreenInfo(0, 2560, 1440, 0, 0, 1.0, true)))
        vm.onDragDevice("inst-1", dx = 100, dy = 0)
        assertEquals(Position(2020, 0), vm.remoteDevices.value["inst-1"]!!.position)
        assertEquals(Position(1920, 0), s.get("inst-1")) // not committed
    }

    @Test
    fun `onDragEnd commits to store`() {
        val s = store(mapOf("inst-1" to Position(1920, 0)))
        val vm = ScreenArrangementViewModel(MutableSharedFlow(), s)
        vm.seedRemote("inst-1", "Desktop", listOf(ScreenInfo(0, 2560, 1440, 0, 0, 1.0, true)))
        vm.onDragDevice("inst-1", dx = -3840, dy = 0) // user put peer on far left
        vm.onDragEnd("inst-1")
        assertEquals(Position(-1920, 0), s.get("inst-1"))
    }

    @Test
    fun `seeds local screens from provider at construction`() {
        val provider =
            LocalScreensProvider {
                listOf(
                    ScreenInfo(0, 1920, 1080, 0, 0, 1.0, true),
                    ScreenInfo(1, 2560, 1440, 1920, 0, 2.0, false),
                )
            }
        val vm = ScreenArrangementViewModel(MutableSharedFlow(), store(), provider)
        assertEquals(2, vm.localScreens.value.size)
        assertEquals(2560, vm.localScreens.value[1].width)
    }

    @Test
    fun `daemon Initialized event overrides provider seed`() =
        runTest {
            val events = MutableSharedFlow<IpcEvent>(replay = 0, extraBufferCapacity = 16)
            val provider =
                LocalScreensProvider {
                    listOf(ScreenInfo(0, 1280, 720, 0, 0, 1.0, true))
                }
            val vm = ScreenArrangementViewModel(events.asSharedFlow(), store(), provider)
            assertEquals(
                1280,
                vm.localScreens.value
                    .single()
                    .width,
            )
            val job = launch { vm.observe() }
            yield()
            events.emit(
                IpcEvent.Initialized(
                    screens = listOf(ScreenInfo(0, 3840, 2160, 0, 0, 2.0, true)),
                    protocolVersion = 2,
                ),
            )
            yield()
            assertEquals(
                3840,
                vm.localScreens.value
                    .single()
                    .width,
            )
            job.cancel()
        }
}
