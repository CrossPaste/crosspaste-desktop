package com.clipevery.clip.item

import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.os.windows.html.HTMLCodec
import com.clipevery.path.DesktopPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.utils.DesktopFileUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.awt.datatransfer.DataFlavor
import java.nio.file.Path

@Serializable
@SerialName("html")
class HtmlClipItem : RealmObject, ClipAppearItem, ClipHtml {

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifier: String = ""

    @Transient
    var relativePath: String = ""

    override var html: String = ""

    override var extraInfo: String? = null

    override fun getHtmlImagePath(): Path {
        val basePath = DesktopPathProvider.resolve(appFileType = AppFileType.HTML)
        return DesktopPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }

    override fun init(
        appInstanceId: String,
        clipId: Long,
    ) {
        relativePath =
            DesktopFileUtils.createClipRelativePath(
                appInstanceId = appInstanceId,
                clipId = clipId,
                fileName = "html2Image.png",
            )
    }

    override var md5: String = ""

    override fun getIdentifierList(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.HTML
    }

    override fun getSearchContent(): String {
        return html
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
        clearResource: Boolean,
    ) {
        if (clearResource) {
            DesktopOneFilePersist(getHtmlImagePath()).delete()
        }
        realm.delete(this)
    }

    override fun fillDataFlavor(map: MutableMap<DataFlavor, Any>) {
        var currentHtml = this.html
        if (currentPlatform().isWindows()) {
            currentHtml = String(HTMLCodec.convertToHTMLFormat(currentHtml.replace("\n", "\r\n").toByteArray()))
        }
        map[DataFlavor.selectionHtmlFlavor] = currentHtml
        map[DataFlavor.fragmentHtmlFlavor] = currentHtml
        map[DataFlavor.allHtmlFlavor] = currentHtml
    }
}
