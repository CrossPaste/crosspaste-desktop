package com.crosspaste.paste

import com.crosspaste.paste.PasteWriteScope.ALL
import com.crosspaste.paste.PasteWriteScope.PRIMARY_CATEGORY
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import com.crosspaste.utils.getJsonUtils
import kotlinx.coroutines.test.runTest
import java.awt.datatransfer.DataFlavor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins down what [DesktopTransferableProducer] decides on its own — which items
 * of a paste reach a plugin, in what order, and under which flag — independent
 * of what any real plugin puts on the transferable.
 */
class DesktopTransferableProducerTest {

    // Guard against PasteItem/JsonUtils circular class initialization
    private val jsonUtils = getJsonUtils()

    /** One call the producer made into a plugin: the item and the scope it was given. */
    private data class Call(
        val type: PasteType,
        val scope: PasteWriteScope,
    )

    /**
     * Records how the producer calls it and leaves two flavors behind: one of its
     * own, and [DataFlavor.stringFlavor] shared with every other plugin so the
     * test can see whose value survives when several items write the same key.
     */
    private class RecordingPlugin(
        private val pasteType: PasteType,
        mime: String,
        private val calls: MutableList<Call>,
    ) : PasteTypePlugin {
        val ownFlavor = DataFlavor("$mime; class=java.lang.String").toPasteDataFlavor()

        override fun getPasteType(): PasteType = pasteType

        override fun getIdentifiers(): List<String> = listOf("test")

        override fun createPrePasteItem(
            itemIndex: Int,
            identifier: String,
            pasteTransferable: PasteTransferable,
            pasteCollector: PasteCollector,
        ) = Unit

        override suspend fun doLoadRepresentation(
            transferData: Any,
            pasteId: Long,
            itemIndex: Int,
            identity: String,
            dataFlavor: PasteDataFlavor,
            dataFlavorMap: Map<String, List<PasteDataFlavor>>,
            pasteTransferable: PasteTransferable,
            pasteCollector: PasteCollector,
        ) = Unit

        override suspend fun buildTransferable(
            pasteItem: PasteItem,
            scope: PasteWriteScope,
            map: MutableMap<PasteDataFlavor, Any>,
        ) {
            calls += Call(pasteItem.getPasteType(), scope)
            map[ownFlavor] = pasteItem.hash
            map[SHARED_FLAVOR.toPasteDataFlavor()] = pasteItem.hash
        }
    }

    private val calls = mutableListOf<Call>()
    private val textPlugin = RecordingPlugin(PasteType.TEXT_TYPE, "application/x-test-text", calls)
    private val urlPlugin = RecordingPlugin(PasteType.URL_TYPE, "application/x-test-url", calls)
    private val filesPlugin = RecordingPlugin(PasteType.FILE_TYPE, "application/x-test-files", calls)

    private val producer = DesktopTransferableProducer(listOf(textPlugin, urlPlugin, filesPlugin))

    private val text = createTextPasteItem(text = "hello")
    private val url = createUrlPasteItem(url = "https://example.com")
    private val files =
        FilesPasteItem(
            identifiers = listOf("files"),
            count = 1,
            hash = "files-hash",
            size = 1,
            fileInfoTreeMap = emptyMap(),
            relativePathList = listOf("a.txt"),
        )

    // ---- PRIMARY_CATEGORY: the primary item's category only ----

    @Test
    fun `primary category writes every item of the primary item's category, primary item last`() =
        runTest {
            val transferable = producer.produce(pasteData(text, url), localOnly = false, scope = PRIMARY_CATEGORY)

            assertNotNull(transferable)
            assertEquals(
                listOf(Call(PasteType.URL_TYPE, PRIMARY_CATEGORY), Call(PasteType.TEXT_TYPE, PRIMARY_CATEGORY)),
                calls,
            )
            // Both plugins wrote SHARED_FLAVOR; the primary item, written last, wins.
            assertEquals(text.hash, transferable.getTransferData(SHARED_FLAVOR))
            assertTrue(transferable.isDataFlavorSupported(urlPlugin.ownFlavor.dataFlavor()))
            assertTrue(transferable.isDataFlavorSupported(textPlugin.ownFlavor.dataFlavor()))
        }

    @Test
    fun `primary category drops non-file items when the primary item is a file`() =
        runTest {
            val transferable = producer.produce(pasteData(files, text), localOnly = false, scope = PRIMARY_CATEGORY)

            assertNotNull(transferable)
            assertEquals(listOf(Call(PasteType.FILE_TYPE, PRIMARY_CATEGORY)), calls)
            assertFalse(transferable.isDataFlavorSupported(textPlugin.ownFlavor.dataFlavor()))
        }

    @Test
    fun `primary category drops file items when the primary item is not a file`() =
        runTest {
            val transferable = producer.produce(pasteData(text, files), localOnly = false, scope = PRIMARY_CATEGORY)

            assertNotNull(transferable)
            assertEquals(listOf(Call(PasteType.TEXT_TYPE, PRIMARY_CATEGORY)), calls)
            assertFalse(transferable.isDataFlavorSupported(filesPlugin.ownFlavor.dataFlavor()))
        }

    // ---- ALL: everything ----

    @Test
    fun `all writes every item, primary item last`() =
        runTest {
            val transferable = producer.produce(pasteData(files, text), localOnly = false, scope = ALL)

            assertNotNull(transferable)
            assertEquals(
                listOf(Call(PasteType.TEXT_TYPE, ALL), Call(PasteType.FILE_TYPE, ALL)),
                calls,
            )
            assertEquals(files.hash, transferable.getTransferData(SHARED_FLAVOR))
        }

    // ---- local-only marker ----

    @Test
    fun `local only adds the marker flavor, otherwise it is absent`() =
        runTest {
            val local = producer.produce(pasteData(text), localOnly = true, scope = PRIMARY_CATEGORY)
            val shared = producer.produce(pasteData(text), localOnly = false, scope = PRIMARY_CATEGORY)

            assertEquals(true, assertNotNull(local).getTransferData(LocalOnlyFlavor))
            assertFalse(assertNotNull(shared).isDataFlavorSupported(LocalOnlyFlavor))
        }

    // ---- items nobody can write ----

    @Test
    fun `an item without a plugin is skipped, and nothing written means null`() =
        runTest {
            val textOnlyProducer = DesktopTransferableProducer(listOf(textPlugin))

            assertNull(textOnlyProducer.produce(pasteData(files), localOnly = false, scope = PRIMARY_CATEGORY))
            assertNull(textOnlyProducer.produce(pasteData(files), localOnly = true, scope = PRIMARY_CATEGORY))

            val transferable = textOnlyProducer.produce(pasteData(files, text), localOnly = false, scope = ALL)
            assertNotNull(transferable)
            assertEquals(listOf(Call(PasteType.TEXT_TYPE, ALL)), calls)
        }

    @Test
    fun `a paste without items is null`() =
        runTest {
            assertNull(producer.produce(pasteData(null), localOnly = false, scope = PRIMARY_CATEGORY))
        }

    // ---- single item ----

    @Test
    fun `a single item is written in the primary category scope`() =
        runTest {
            val transferable = producer.produce(url, localOnly = true)

            assertNotNull(transferable)
            assertEquals(listOf(Call(PasteType.URL_TYPE, PRIMARY_CATEGORY)), calls)
            assertEquals(true, transferable.getTransferData(LocalOnlyFlavor))
        }

    @Test
    fun `a single item without a plugin is null`() =
        runTest {
            assertNull(DesktopTransferableProducer(listOf(textPlugin)).produce(files, localOnly = false))
        }

    // ---- helpers ----

    private fun pasteData(
        appear: PasteItem?,
        vararg others: PasteItem,
    ): PasteData =
        PasteData(
            id = 1,
            appInstanceId = "test-instance",
            pasteAppearItem = appear,
            pasteCollection = PasteCollection(others.toList()),
            pasteType = (appear ?: others.firstOrNull())?.getPasteType()?.type ?: PasteType.TEXT_TYPE.type,
            source = "Test",
            size = appear?.size ?: 0,
            hash = appear?.hash ?: "",
            createTime = 0,
            pasteState = PasteState.LOADED,
        )

    private fun PasteDataFlavor.dataFlavor(): DataFlavor = (this as DesktopPasteDataFlavor).dataFlavor

    private companion object {
        val SHARED_FLAVOR: DataFlavor = DataFlavor.stringFlavor
    }
}
