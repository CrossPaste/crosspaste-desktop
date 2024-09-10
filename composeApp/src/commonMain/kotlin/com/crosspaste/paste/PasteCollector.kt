package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.paste.plugin.process.PasteProcessPlugin
import com.crosspaste.paste.plugin.type.PasteTypePlugin
import com.crosspaste.realm.paste.PasteCollection
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.LoggerExtension.logSuspendExecutionTime
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass

class PasteCollector(
    itemCount: Int,
    private val appInfo: AppInfo,
    private val pasteRealm: PasteRealm,
    private val pasteProcessPlugins: List<PasteProcessPlugin>,
) {

    private val logger = KotlinLogging.logger {}

    private val preCollectors: Array<MutableMap<KClass<out PasteTypePlugin>, PasteItem>> = Array(itemCount) { mutableMapOf() }

    private val updateCollectors: Array<MutableSet<KClass<out PasteTypePlugin>>> = Array(itemCount) { mutableSetOf() }

    private val updateErrors: Array<Exception?> = Array(itemCount) { null }

    private var existError = false

    fun needPreCollectionItem(
        itemIndex: Int,
        kclass: KClass<out PasteTypePlugin>,
    ): Boolean {
        return !preCollectors[itemIndex].contains(kclass)
    }

    fun needUpdateCollectItem(
        itemIndex: Int,
        kclass: KClass<out PasteTypePlugin>,
    ): Boolean {
        return !updateCollectors[itemIndex].contains(kclass)
    }

    fun preCollectItem(
        itemIndex: Int,
        kclass: KClass<out PasteTypePlugin>,
        pasteItem: PasteItem,
    ) {
        preCollectors[itemIndex][kclass] = pasteItem
    }

    fun updateCollectItem(
        itemIndex: Int,
        kclass: KClass<out PasteTypePlugin>,
        update: (PasteItem, MutableRealm) -> Unit,
    ) {
        preCollectors[itemIndex][kclass]?.let {
            updateCollectors[itemIndex].add(kclass)
            val updatePasteItem: (MutableRealm) -> Unit = { realm ->
                update(it, realm)
            }
            pasteRealm.update(updatePasteItem)
        }
    }

    fun collectError(
        pasteId: Long,
        itemIndex: Int,
        error: Exception,
    ) {
        logger.error(error) { "Failed to collect item $itemIndex of pasteId $pasteId" }
        updateErrors[itemIndex] = error
        existError = true
    }

    suspend fun createPrePasteData(
        pasteId: Long,
        source: String?,
        remote: Boolean,
    ): ObjectId? {
        return logSuspendExecutionTime(logger, "createPrePasteData") {
            val collector = preCollectors.filter { it.isNotEmpty() }
            if (collector.isEmpty()) {
                return@logSuspendExecutionTime null
            }
            val pasteItems: List<PasteItem> = preCollectors.flatMap { it.values }

            val pasteCollection =
                PasteCollection().apply {
                    this.pasteItems = pasteItems.map { RealmAny.create(it as RealmObject) }.toRealmList()
                }

            val pasteData =
                PasteData().apply {
                    this.pasteId = pasteId
                    this.pasteCollection = pasteCollection
                    this.pasteType = PasteType.INVALID
                    this.source = source
                    this.hash = ""
                    this.appInstanceId = appInfo.appInstanceId
                    this.createTime = RealmInstant.now()
                    this.pasteState = PasteState.LOADING
                    this.remote = remote
                }
            return@logSuspendExecutionTime pasteRealm.createPasteData(pasteData)
        }
    }

    suspend fun completeCollect(id: ObjectId) {
        logSuspendExecutionTime(logger, "completeCollect") {
            if (preCollectors.isEmpty() || (existError && updateErrors.all { it != null })) {
                try {
                    pasteRealm.markDeletePasteData(id)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to mark delete paste $id" }
                }
            } else {
                try {
                    pasteRealm.releaseLocalPasteData(id, pasteProcessPlugins)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to release paste $id" }
                    // The following errors will be sent next
                    // [RLM_ERR_WRONG_TRANSACTION_STATE]: The Realm is already in a write transaction
                    // https://github.com/realm/realm-kotlin/pull/1621  wait new version release
                    try {
                        pasteRealm.markDeletePasteData(id)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to mark delete paste $id" }
                    }
                }
            }
        }
    }
}
