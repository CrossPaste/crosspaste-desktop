package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteType
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
) : PasteItem, PasteFiles {

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
    )

    override fun getAppFileType(): AppFileType {
        return AppFileType.FILE
    }

    override fun getFilePaths(userDataPathProvider: UserDataPathProvider): List<Path> {
        val basePath = basePath?.toPath() ?: userDataPathProvider.resolve(appFileType = getAppFileType())
        return relativePathList.map { relativePath ->
            userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
    }

    override fun getPasteType(): PasteType {
        return PasteType.FILE_TYPE
    }

    override fun getSearchContent(): String {
        return relativePathList.joinToString(separator = " ") { path ->
            path.toPath().name.lowercase()
        }
    }

    override fun getTitle(): String {
        return relativePathList.joinToString(separator = ", ") {
            it.toPath().name
        }
    }

    // use to adapt relative paths when relative is no storage in crossPaste
    override fun bind(pasteCoordinate: PasteCoordinate): PasteItem {
        val noStorageInCrossPaste = relativePathList.any { it.toPath().segments.size == 1 }
        if (noStorageInCrossPaste) {
            val fileUtils = getFileUtils()
            val newRelativePathList =
                relativePathList.map { relativePath ->
                    val path = relativePath.toPath()
                    val fileName = path.name
                    fileUtils.createPasteRelativePath(
                        pasteCoordinate = pasteCoordinate,
                        fileName = fileName,
                    )
                }
            return FilesPasteItem(
                identifiers = identifiers,
                count = count,
                hash = hash,
                size = size,
                basePath = basePath,
                relativePathList = newRelativePathList,
                fileInfoTreeMap = fileInfoTreeMap,
            )
        } else {
            return this
        }
    }

    override fun update(
        data: Any,
        hash: String,
    ): PasteItem {
        // TODO: Implement update
        return this
    }

    override fun clear(
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean,
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

    override fun isValid(): Boolean {
        return count > 0 &&
            hash.isNotEmpty() &&
            size > 0 &&
            fileInfoTreeMap.isNotEmpty() &&
            relativePathList.isNotEmpty() &&
            relativePathList.size == fileInfoTreeMap.size
    }

    override fun toJson(): String {
        return buildJsonObject {
            put("type", getPasteType().type)
            put("identifiers", identifiers.joinToString(","))
            put("count", count)
            put("hash", hash)
            put("size", size)
            basePath?.let { put("basePath", it) }
            put("relativePathList", jsonUtils.JSON.encodeToJsonElement(relativePathList))
            put("fileInfoTreeMap", jsonUtils.JSON.encodeToJsonElement(fileInfoTreeMap))
        }.toString()
    }
}
