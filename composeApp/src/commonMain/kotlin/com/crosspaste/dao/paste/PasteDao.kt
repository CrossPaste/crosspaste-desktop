package com.crosspaste.dao.paste

import com.crosspaste.paste.PastePlugin
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmInstant
import org.mongodb.kbson.ObjectId

interface PasteDao {

    fun getMaxPasteId(): Long

    suspend fun createPasteData(pasteData: PasteData): ObjectId

    suspend fun markDeletePasteData(id: ObjectId)

    suspend fun deletePasteData(id: ObjectId)

    fun getSize(allOrFavorite: Boolean = false): Long

    fun getPasteResourceInfo(allOrFavorite: Boolean = false): PasteResourceInfo

    fun getSizeByTimeLessThan(time: RealmInstant): Long

    fun getMinPasteDataCreateTime(): RealmInstant?

    fun getPasteData(
        appInstanceId: String? = null,
        limit: Int,
    ): RealmResults<PasteData>

    fun getPasteData(id: ObjectId): PasteData?

    fun getPasteData(
        appInstanceId: String,
        pasteId: Long,
    ): PasteData?

    suspend fun releaseLocalPasteData(
        id: ObjectId,
        pastePlugins: List<PastePlugin>,
    )

    suspend fun releaseRemotePasteData(
        pasteData: PasteData,
        tryWritePasteboard: (PasteData, Boolean) -> Unit,
    )

    suspend fun releaseRemotePasteDataWithFile(
        id: ObjectId,
        tryWritePasteboard: (PasteData) -> Unit,
    )

    fun update(update: (MutableRealm) -> Unit)

    suspend fun suspendUpdate(update: (MutableRealm) -> Unit)

    fun getPasteDataLessThan(
        appInstanceId: String? = null,
        limit: Int,
        createTime: RealmInstant,
    ): RealmResults<PasteData>

    suspend fun markDeleteByCleanTime(
        cleanTime: RealmInstant,
        pasteType: Int? = null,
    )

    fun setFavorite(
        id: ObjectId,
        favorite: Boolean,
    )

    fun searchPasteData(
        searchTerms: List<String>,
        favorite: Boolean? = null,
        appInstanceIdQuery: (RealmQuery<PasteData>) -> RealmQuery<PasteData> = { it },
        pasteType: Int? = null,
        // sort createTime, true: desc, false: asc
        sort: Boolean = true,
        limit: Int,
    ): RealmResults<PasteData>
}
