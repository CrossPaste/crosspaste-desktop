package com.clipevery.utils

import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test

class FileUtilsTest {

    @Test
    fun testCreateTempFile() {
        val tempDirectory = Files.createTempDirectory("clipevery")
        val path = Paths.get("/Users/geekcat/Downloads/1708999157933.jpg")
        val temPath = tempDirectory.resolve("1.jpg")
        Files.createSymbolicLink(temPath, path)
        val desktop = Desktop.getDesktop()
        val imageFile = temPath.toFile()
        println(imageFile.absolutePath)
        if (imageFile.exists()) desktop.open(imageFile)
    }
}