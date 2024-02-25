package com.clipevery.clip

import com.clipevery.app.AppInfo
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipType
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
    private val clipPlugins: List<ClipPlugin>) {

    private val logger = KotlinLogging.logger {}

    private val preCollectors: Array<MutableMap<KClass<out ClipItemService>, ClipAppearItem>> = Array(itemCount) { mutableMapOf() }

    private val updateCollectors: Array<MutableSet<KClass<out ClipItemService>>> = Array(itemCount) { mutableSetOf() }

    private var existError = false

    fun needPreCollectionItem(itemIndex: Int, kclass: KClass<out ClipItemService>): Boolean {
        return !preCollectors[itemIndex].contains(kclass)
    }

    fun needUpdateCollectItem(itemIndex: Int, kclass: KClass<out ClipItemService>): Boolean {
        return !updateCollectors[itemIndex].contains(kclass)
    }

    fun preCollectItem(itemIndex: Int, kclass: KClass<out ClipItemService>, clipItem: ClipAppearItem) {
        preCollectors[itemIndex][kclass] = clipItem
    }

    fun updateCollectItem(itemIndex: Int, kclass: KClass<out ClipItemService>, update: (ClipAppearItem, MutableRealm) -> Unit) {
        preCollectors[itemIndex][kclass]?.let{
            val updateClipItem: (MutableRealm) -> Unit = { realm ->
                update(it, realm)
            }
            clipDao.updateClipItem(updateClipItem)
        }
    }

    fun collectError(clipId: Int, itemIndex: Int, error: Exception) {
        logger.error(error) { "Failed to collect item $itemIndex of clip $clipId" }
        existError = true
    }

    fun createPreClipData(clipId: Int): ObjectId? {
        val collector = preCollectors.filter { it.isNotEmpty() }
        if (collector.isEmpty()) {
            return null
        }
        val clipAppearItems: List<ClipAppearItem> = preCollectors.flatMap { it.values }

        val clipContent = ClipContent(clipAppearItems.map { RealmAny.create(it as RealmObject) }.toRealmList())

        val clipData = ClipData().apply {
            this.clipId = clipId
            this.clipContent = clipContent
            this.clipType =  ClipType.INVALID
            this.md5 = ""
            this.appInstanceId = appInfo.appInstanceId
            this.createTime = RealmInstant.now()
            this.preCreate = true
        }
        return clipDao.createClipData(clipData)
    }

    fun completeCollect(id: ObjectId) {
        if (existError || preCollectors.isEmpty()) {
            try {
                clipDao.deleteClipData(id)
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete clip $id" }
            }
        } else {
            try {
              clipDao.releaseClipData(id, clipPlugins)
            } catch (e: Exception) {
                logger.error(e) { "Failed to release clip $id" }
            }
        }
    }
}