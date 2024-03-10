package com.clipevery.presist

import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipData

class ClipDataFilePersistIterable(private val clipData: ClipData): Iterable<OneFilePersist> {

    override fun iterator(): Iterator<OneFilePersist> {
        val clipFiles = clipData.getClipAppearItems().filter { it is ClipFiles }.map { it as ClipFiles }
        return ClipDataFilePersistIterator(clipFiles)
    }
}

class ClipDataFilePersistIterator(clipAppearItems: List<ClipFiles>): Iterator<OneFilePersist> {

    val iterator = clipAppearItems.flatMap { clipFiles -> clipFiles.getClipFiles() }
        .listIterator()

    override fun hasNext(): Boolean {
        return iterator.hasNext()
    }

    override fun next(): OneFilePersist {
        val clipFile = iterator.next()
        return DesktopOneFilePersist(clipFile.getFilePath())
    }
}