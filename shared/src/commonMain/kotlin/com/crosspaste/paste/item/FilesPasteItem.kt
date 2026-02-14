package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.PasteItem.Companion.getExtraInfoFromJson
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okio.Path
import okio.Path.Companion.toPath

@Serializable
@SerialName("files")
data class FilesPasteItem(
    override val identifiers: List<String>,
    override var count: Long,
    override val hash: String,
    override val size: Long,
    @Transient
    override val basePath: String? = null,
    override val fileInfoTreeMap: Map<String, FileInfoTree>,
    override val relativePathList: List<String>,
    override val extraInfo: JsonObject? = null,
) : PasteItem,
    PasteFiles {

    companion object {
        val fileUtils = getFileUtils()
        val jsonUtils = getJsonUtils()
    }

    constructor(jsonObject: JsonObject) : this(
        jsonObject = jsonObject,
        fileInfoTreeMap =
            jsonUtils.JSON.decodeFromJsonElement<Map<String, FileInfoTree>>(
                jsonObject["fileInfoTreeMap"]!!.jsonObject,
            ),
    )

    private constructor(
        jsonObject: JsonObject,
        fileInfoTreeMap: Map<String, FileInfoTree>,
    ) : this(
        identifiers = jsonObject["identifiers"]!!.jsonPrimitive.content.split(","),
        count = fileInfoTreeMap.values.sumOf { it.getCount() },
        hash = jsonObject["hash"]!!.jsonPrimitive.content,
        size = jsonObject["size"]!!.jsonPrimitive.long,
        basePath = jsonObject["basePath"]?.jsonPrimitive?.content,
        relativePathList =
            jsonObject["relativePathList"]!!.jsonArray.map {
                it.jsonPrimitive.content
            },
        fileInfoTreeMap = fileInfoTreeMap,
        extraInfo = getExtraInfoFromJson(jsonObject),
    )

    override fun getAppFileType(): AppFileType = AppFileType.FILE

    override fun getFilePaths(userDataPathProvider: UserDataPathProvider): List<Path> {
        val basePath = basePath?.toPath() ?: userDataPathProvider.resolve(appFileType = getAppFileType())
        return relativePathList.map { relativePath ->
            userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
    }

    override fun getPasteType(): PasteType = PasteType.FILE_TYPE

    override fun getSearchContent(): String =
        fileInfoTreeMap.keys.joinToString(separator = " ") {
            it.lowercase()
        }

    override fun getSummary(): String =
        relativePathList.joinToString(separator = ", ") {
            it.toPath().name
        }

    // use to adapt relative paths when relative is no storage in crossPaste
    override fun bind(
        pasteCoordinate: PasteCoordinate,
        syncToDownload: Boolean,
    ): PasteItem {
        val (newBasePath, newRelativePathList) = bindFilePaths(pasteCoordinate, syncToDownload)
        return FilesPasteItem(
            identifiers = identifiers,
            count = count,
            hash = hash,
            size = size,
            basePath = newBasePath,
            relativePathList = newRelativePathList,
            fileInfoTreeMap = fileInfoTreeMap,
            extraInfo = extraInfo,
        )
    }

    override fun applyRenameMap(renameMap: Map<String, String>): FilesPasteItem {
        val (newRelativePathList, newFileInfoTreeMap) = computeRenamedFileData(renameMap)
        return copy(relativePathList = newRelativePathList, fileInfoTreeMap = newFileInfoTreeMap)
    }

    override fun copy(extraInfo: JsonObject?): FilesPasteItem =
        createFilesPasteItem(
            identifiers = identifiers,
            basePath = basePath,
            relativePathList = relativePathList,
            fileInfoTreeMap = fileInfoTreeMap,
            extraInfo = extraInfo,
        )

    override fun clear(
        clearResource: Boolean,
        pasteCoordinate: PasteCoordinate,
        userDataPathProvider: UserDataPathProvider,
    ) {
        if (clearResource) {
            // Non-reference types need to clean up copied files
            if (basePath == null) {
                for (path in getFilePaths(userDataPathProvider)) {
                    fileUtils.deleteFile(path)
                }
            }
        }
    }

    override fun isValid(): Boolean =
        count > 0 &&
            hash.isNotEmpty() &&
            size > 0 &&
            fileInfoTreeMap.isNotEmpty() &&
            relativePathList.isNotEmpty() &&
            relativePathList.size == fileInfoTreeMap.size &&
            hasExistingFiles()

    override fun toJson(): String =
        buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("count", count)
            put("hash", hash)
            put("size", size)
            basePath?.let { put("basePath", it) }
            put("relativePathList", jsonUtils.JSON.encodeToJsonElement(relativePathList))
            put("fileInfoTreeMap", jsonUtils.JSON.encodeToJsonElement(fileInfoTreeMap))
            extraInfo?.let { put("extraInfo", it) }
        }.toString()
}
