package com.clipevery.clip

import com.clipevery.app.AppInfo
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipType
import com.clipevery.dao.clip.sortClipAppearItems
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KClass

class ClipCollector(
    itemCount: Int,
    private val appInfo: AppInfo,
    private val clipDao: ClipDao,
    private val singleClipPlugins: List<SingleClipPlugin>,
    private val multiClipPlugins: List<MultiClipPlugin>
) {

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

        val clipContents: RealmList<ClipContent> = realmListOf()

        var firstAppearItem: RealmAny? = null

        var clipType: Int = ClipType.INVALID

        var clipSearchContent: String? = null

        var md5 = ""

        for ((index, collector) in collectors.withIndex()) {
            var currentCollector: Map<KClass<out ClipItemService>, ClipAppearItem> = collector

            if (currentCollector.isEmpty()) {
                continue
            }

            for (singleClipPlugin in singleClipPlugins) {
                currentCollector = singleClipPlugin.pluginProcess(currentCollector)
            }
            assert(currentCollector.isNotEmpty())

            var currentItems: List<ClipAppearItem> = currentCollector.values.toList()

            if (index == 0) {
                for (multiClipPlugin in multiClipPlugins) {
                    currentItems = multiClipPlugin.pluginProcess(currentItems)
                }
            }

            val clipItems = sortClipAppearItems(currentItems)

            val list: RealmList<RealmAny?> = clipItems.map { RealmAny.create(it as RealmObject) }.toRealmList()

            if (index == 0) {
                firstAppearItem = list[0]
                clipType = clipItems[0].getClipType()
                clipSearchContent = clipItems[0].getSearchContent()
                md5 = clipItems[0].md5
            }

            val clipContent = ClipContent(list)

            clipContents.add(clipContent)
        }

        firstAppearItem?.let {
            val clipData = ClipData().apply {
                this.clipId = clipId
                this.clipAppearContent = firstAppearItem
                this.clipContents = clipContents
                this.clipType =  clipType
                this.clipSearchContent = clipSearchContent
                this.md5 = md5
                this.appInstanceId = appInfo.appInstanceId
                this.preCreate = false
            }

            clipDao.createClipData(clipData)
        }


    }
}