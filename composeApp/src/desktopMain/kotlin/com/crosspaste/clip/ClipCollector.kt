package com.crosspaste.clip

import com.crosspaste.app.AppInfo
import com.crosspaste.dao.clip.ClipCollection
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.dao.clip.ClipState
import com.crosspaste.dao.clip.ClipType
import com.crosspaste.utils.LoggerExtension.logSuspendExecutionTime
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass

class ClipCollector(
    itemCount: Int,
    private val appInfo: AppInfo,
    private val clipDao: ClipDao,
    private val clipPlugins: List<ClipPlugin>,
) {

    private val logger = KotlinLogging.logger {}

    private val preCollectors: Array<MutableMap<KClass<out ClipItemService>, ClipItem>> = Array(itemCount) { mutableMapOf() }

    private val updateCollectors: Array<MutableSet<KClass<out ClipItemService>>> = Array(itemCount) { mutableSetOf() }

    private val updateErrors: Array<Exception?> = Array(itemCount) { null }

    private var existError = false

    fun needPreCollectionItem(
        itemIndex: Int,
        kclass: KClass<out ClipItemService>,
    ): Boolean {
        return !preCollectors[itemIndex].contains(kclass)
    }

    fun needUpdateCollectItem(
        itemIndex: Int,
        kclass: KClass<out ClipItemService>,
    ): Boolean {
        return !updateCollectors[itemIndex].contains(kclass)
    }

    fun preCollectItem(
        itemIndex: Int,
        kclass: KClass<out ClipItemService>,
        clipItem: ClipItem,
    ) {
        preCollectors[itemIndex][kclass] = clipItem
    }

    fun updateCollectItem(
        itemIndex: Int,
        kclass: KClass<out ClipItemService>,
        update: (ClipItem, MutableRealm) -> Unit,
    ) {
        preCollectors[itemIndex][kclass]?.let {
            updateCollectors[itemIndex].add(kclass)
            val updateClipItem: (MutableRealm) -> Unit = { realm ->
                update(it, realm)
            }
            clipDao.update(updateClipItem)
        }
    }

    fun collectError(
        clipId: Long,
        itemIndex: Int,
        error: Exception,
    ) {
        logger.error(error) { "Failed to collect item $itemIndex of clip $clipId" }
        updateErrors[itemIndex] = error
        existError = true
    }

    suspend fun createPreClipData(
        clipId: Long,
        source: String?,
        remote: Boolean,
    ): ObjectId? {
        return logSuspendExecutionTime(logger, "createPreClipData") {
            val collector = preCollectors.filter { it.isNotEmpty() }
            if (collector.isEmpty()) {
                return@logSuspendExecutionTime null
            }
            val clipItems: List<ClipItem> = preCollectors.flatMap { it.values }

            val clipCollection =
                ClipCollection().apply {
                    this.clipItems = clipItems.map { RealmAny.create(it as RealmObject) }.toRealmList()
                }

            val clipData =
                ClipData().apply {
                    this.clipId = clipId
                    this.clipCollection = clipCollection
                    this.clipType = ClipType.INVALID
                    this.source = source
                    this.md5 = ""
                    this.appInstanceId = appInfo.appInstanceId
                    this.createTime = RealmInstant.now()
                    this.clipState = ClipState.LOADING
                    this.remote = remote
                }
            return@logSuspendExecutionTime clipDao.createClipData(clipData)
        }
    }

    suspend fun completeCollect(id: ObjectId) {
        logSuspendExecutionTime(logger, "completeCollect") {
            if (preCollectors.isEmpty() || (existError && updateErrors.all { it != null })) {
                try {
                    clipDao.markDeleteClipData(id)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to mark delete clip $id" }
                }
            } else {
                try {
                    clipDao.releaseLocalClipData(id, clipPlugins)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to release clip $id" }
                    // The following errors will be sent next
                    // [RLM_ERR_WRONG_TRANSACTION_STATE]: The Realm is already in a write transaction
                    // https://github.com/realm/realm-kotlin/pull/1621  wait new version release
                    try {
                        clipDao.markDeleteClipData(id)
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to mark delete clip $id" }
                    }
                }
            }
        }
    }
}
