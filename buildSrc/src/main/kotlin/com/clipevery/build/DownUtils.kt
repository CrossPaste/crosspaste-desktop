package com.clipevery.build

//import de.undercouch.gradle.tasks.download.Download

//object DownUtils {
//
//    fun download(
//        name: String,
//        properties: Properties,
//        resourceDir: Directory,
//    ) {
//        if (resourceDir.dir(name).asFileTree.isEmpty) {
//            val chromeHeadlessShellUrl = properties.getProperty(name)!!
//            download.run {
//                src { chromeHeadlessShellUrl }
//                dest { resourceDir }
//                overwrite(true)
//                tempAndMove(true)
//            }
//            copy {
//                from(zipTree(resourceDir.file("$name.zip")))
//                into(resourceDir)
//            }
//            delete(resourceDir.file("$name.zip"))
//        }
//    }
//}