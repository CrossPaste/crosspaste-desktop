package com.clipevery.clip.item

import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import java.nio.file.Path
import java.nio.file.Paths

class FilesClipItem: RealmObject, ClipAppearItem, ClipFiles {

    var identifiers: RealmList<String> = realmListOf()

    var fileList: RealmList<String> = realmListOf()

    var md5: String = ""

    override fun getFilePaths(): List<Path> {
        TODO("Not yet implemented")
    }

    override fun getIdentifiers(): List<String> {
        return identifiers.toList()
    }

    override fun getClipType(): Int {
        return ClipType.FILES
    }

    override fun getSearchContent(): String? {
        return fileList.map { path ->
            Paths.get(path).fileName
        }.joinToString(separator = " ")
    }

    override fun getMd5(): String {
        return md5
    }

    override fun update(data: Any, md5: String) {}

    override fun clear() {}
}
