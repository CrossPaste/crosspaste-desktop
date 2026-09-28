package com.crosspaste.paste

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

    /** One call the producer made into a plugin: the item and the mixedCategory flag. */
    private data class Call(
        val type: PasteType,
        val mixedCategory: Boolean,
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
            mixedCategory: Boolean,
            map: MutableMap<PasteDataFlavor, Any>,
        ) {
            calls += Call(pasteItem.getPasteType(), mixedCategory)
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

    // ---- primary: the item's own category only ----

    @Test
    fun `primary writes every item of the primary item's category, primary item last`() =
        runTest {
            val transferable = producer.produce(pasteData(text, url), localOnly = false, primary = true)

            assertNotNull(transferable)
            assertEquals(
                listOf(Call(PasteType.URL_TYPE, false), Call(PasteType.TEXT_TYPE, false)),
                calls,
            )
            // Both plugins wrote SHARED_FLAVOR; the primary item, written last, wins.
            assertEquals(text.hash, transferable.getTransferData(SHARED_FLAVOR))
            assertTrue(transferable.isDataFlavorSupported(urlPlugin.ownFlavor.dataFlavor()))
            assertTrue(transferable.isDataFlavorSupported(textPlugin.ownFlavor.dataFlavor()))
        }

    @Test
    fun `primary drops non-file items when the primary item is a file`() =
        runTest {
            val transferable = producer.produce(pasteData(files, text), localOnly = false, primary = true)

            assertNotNull(transferable)
            assertEquals(listOf(Call(PasteType.FILE_TYPE, false)), calls)
            assertFalse(transferable.isDataFlavorSupported(textPlugin.ownFlavor.dataFlavor()))
        }

    @Test
    fun `primary drops file items when the primary item is not a file`() =
        runTest {
            val transferable = producer.produce(pasteData(text, files), localOnly = false, primary = true)

            assertNotNull(transferable)
            assertEquals(listOf(Call(PasteType.TEXT_TYPE, false)), calls)
            assertFalse(transferable.isDataFlavorSupported(filesPlugin.ownFlavor.dataFlavor()))
        }

    // ---- not primary: everything, flagged as mixed ----

    @Test
    fun `non-primary writes every item as mixed category, primary item last`() =
        runTest {
            val transferable = producer.produce(pasteData(files, text), localOnly = false, primary = false)

            assertNotNull(transferable)
            assertEquals(
                listOf(Call(PasteType.TEXT_TYPE, true), Call(PasteType.FILE_TYPE, true)),
                calls,
            )
            assertEquals(files.hash, transferable.getTransferData(SHARED_FLAVOR))
        }

    // ---- local-only marker ----

    @Test
    fun `local only adds the marker flavor, otherwise it is absent`() =
        runTest {
            val local = producer.produce(pasteData(text), localOnly = true, primary = true)
            val shared = producer.produce(pasteData(text), localOnly = false, primary = true)

            assertEquals(true, assertNotNull(local).getTransferData(LocalOnlyFlavor))
            assertFalse(assertNotNull(shared).isDataFlavorSupported(LocalOnlyFlavor))
        }

    // ---- items nobody can write ----

    @Test
    fun `an item without a plugin is skipped, and nothing written means null`() =
        runTest {
            val textOnlyProducer = DesktopTransferableProducer(listOf(textPlugin))

            assertNull(textOnlyProducer.produce(pasteData(files), localOnly = false, primary = true))
            assertNull(textOnlyProducer.produce(pasteData(files), localOnly = true, primary = true))

            val transferable = textOnlyProducer.produce(pasteData(files, text), localOnly = false, primary = false)
            assertNotNull(transferable)
            assertEquals(listOf(Call(PasteType.TEXT_TYPE, true)), calls)
        }

    @Test
    fun `a paste without items is null`() =
        runTest {
            assertNull(producer.produce(pasteData(null), localOnly = false, primary = true))
        }

    // ---- single item ----

    @Test
    fun `a single item is never written as mixed category`() =
        runTest {
            val transferable = producer.produce(url, localOnly = true)

            assertNotNull(transferable)
            assertEquals(listOf(Call(PasteType.URL_TYPE, false)), calls)
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
