package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getHtmlUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okio.Path
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId

@Serializable
@SerialName("html")
class HtmlPasteItem : RealmObject, PasteItem, PasteHtml {

    companion object {
        val fileUtils = getFileUtils()
        val htmlUtils = getHtmlUtils()
    }

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifier: String = ""

    @Transient
    var relativePath: String = ""

    override var html: String = ""

    @Index
    override var favorite: Boolean = false

    override var size: Long = 0L

    override var hash: String = ""

    @Index
    @Transient
    override var pasteState: Int = PasteState.LOADING

    override var extraInfo: String? = null

    override fun getHtmlImagePath(userDataPathProvider: UserDataPathProvider): Path {
        val basePath = userDataPathProvider.resolve(appFileType = AppFileType.HTML)
        return userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }

    override fun bind(pasteCoordinate: PasteCoordinate) {
        relativePath =
            fileUtils.createPasteRelativePath(
                pasteCoordinate = pasteCoordinate,
                fileName = "html2Image.png",
            )
    }

    override fun getIdentifierList(): List<String> {
        return listOf(identifier)
    }

    override fun getPasteType(): PasteType {
        return PasteType.HTML_TYPE
    }

    override fun getSearchContent(): String {
        return htmlUtils.getHtmlText(html).lowercase()
    }

    override fun getTitle(): String {
        return htmlUtils.getHtmlText(html)
    }

    override fun update(
        data: Any,
        hash: String,
    ) {
        (data as? String)?.let { html ->
            this.html = html
            this.hash = hash
        }
    }

    override fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean,
    ) {
        if (clearResource) {
            fileUtils.deleteFile(getHtmlImagePath(userDataPathProvider))
        }
        realm.delete(this)
    }
}
