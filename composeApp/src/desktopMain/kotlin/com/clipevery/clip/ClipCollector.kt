package com.clipevery.clip

import com.clipevery.app.AppInfo
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KClass

class ClipCollector(
    itemCount: Int,
    private val appInfo: AppInfo,
    private val clipDao: ClipDao,
    private val clipPlugins: List<ClipPlugin>) {

    val logger = KotlinLogging.logger {}

    private val collectors: Array<MutableMap<KClass<out ClipItemService>, ClipAppearItem>> = Array(itemCount) { mutableMapOf() }

    fun needCollectionItem(itemIndex: Int, kclass: KClass<out ClipItemService>): Boolean {
        return !collectors[itemIndex].contains(kclass)
    }

    fun collectItem(itemIndex: Int, kclass: KClass<out ClipItemService>, clipItem: ClipAppearItem) {
        collectors[itemIndex][kclass] = clipItem
    }

    fun collectError(clipId: Int, itemIndex: Int, error: Exception) {
        logger.error(error) { "Failed to collect item $itemIndex of clip $clipId" }
    }

    fun completeCollect(clipId: Int) {
        // if there are no collectors, return
        if (collectors.isEmpty()) {
            return
        }

        var clipAppearItems: List<ClipAppearItem> = collectors.flatMap { it.values }

        for (clipPlugin in clipPlugins) {
            clipAppearItems = clipPlugin.pluginProcess(clipAppearItems)
        }

        val firstItem: ClipAppearItem = clipAppearItems.first()

        val remainingItems: List<ClipAppearItem> = clipAppearItems.drop(1)

        assert(clipAppearItems.isNotEmpty())

        val clipAppearContent: RealmAny = RealmAny.create(firstItem as RealmObject)

        val clipContent = ClipContent(remainingItems.map { RealmAny.create(it as RealmObject) }.toRealmList())

        val clipData = ClipData().apply {
            this.clipId = clipId
            this.clipAppearContent = clipAppearContent
            this.clipContent = clipContent
            this.clipType =  firstItem.getClipType()
            this.clipSearchContent = firstItem.getSearchContent()
            this.md5 = firstItem.md5
            this.appInstanceId = appInfo.appInstanceId
            this.preCreate = false
        }

        clipDao.createClipData(clipData)
    }
}