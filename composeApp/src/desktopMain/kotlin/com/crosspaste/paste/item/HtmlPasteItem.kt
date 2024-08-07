package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteState
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.DesktopOneFilePersist
import com.crosspaste.utils.DesktopFileUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okio.Path
import org.jsoup.Jsoup
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId

@Serializable
@SerialName("html")
class HtmlPasteItem : RealmObject, PasteItem, PasteHtml {

    companion object {}

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

    override var md5: String = ""

    @Index
    @Transient
    override var pasteState: Int = PasteState.LOADING

    override var extraInfo: String? = null

    override fun getHtmlImagePath(userDataPathProvider: UserDataPathProvider): Path {
        val basePath = userDataPathProvider.resolve(appFileType = AppFileType.HTML)
        return userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }

    override fun init(
        appInstanceId: String,
        pasteId: Long,
    ) {
        relativePath =
            DesktopFileUtils.createPasteRelativePath(
                appInstanceId = appInstanceId,
                pasteId = pasteId,
                fileName = "html2Image.png",
            )
    }

    override fun getIdentifierList(): List<String> {
        return listOf(identifier)
    }

    override fun getPasteType(): Int {
        return PasteType.HTML
    }

    override fun getSearchContent(): String {
        return Jsoup.parse(html).text().lowercase()
    }

    override fun update(
        data: Any,
        md5: String,
    ) {
        (data as? String)?.let { html ->
            this.html = html
            this.md5 = md5
        }
    }

    override fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean,
    ) {
        if (clearResource) {
            DesktopOneFilePersist(getHtmlImagePath(userDataPathProvider)).delete()
        }
        realm.delete(this)
    }
}
