package com.crosspaste.module.ocr

import com.crosspaste.app.AppFileType
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.DE
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.EN
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.ES
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.FA
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.FR
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.JA
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.KO
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.ZH
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.ZH_HANT
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.OCRModule
import com.crosspaste.module.DownloadState
import com.crosspaste.module.DownloadTask
import com.crosspaste.module.ModuleDownloadState
import com.crosspaste.path.AppPathProvider
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.OCR
import com.crosspaste.utils.createPlatformLock
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import org.bytedeco.leptonica.global.leptonica
import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.global.tesseract

class DesktopOCRModule(
    private val configManager: DesktopConfigManager,
    private val copywriter: GlobalCopywriter,
    private val navigateManager: NavigationManager,
    private val appPathProvider: AppPathProvider,
) : OCRModule {

    companion object Companion {
        const val MODULE_ID = "OCR"

        val fileUtils = getFileUtils()

        fun getTrainedDataName(language: String): String? =
            when (language) {
                DE -> "deu"
                EN -> "eng"
                ES -> "spa"
                FA -> "fas"
                FR -> "fra"
                KO -> "kor"
                JA -> "jpn"
                ZH -> "chi_sim"
                ZH_HANT -> "chi_tra"
                else -> null
            }

        fun getLanguageName(trainedDataName: String): String? =
            when (trainedDataName) {
                "deu" -> DE
                "eng" -> EN
                "spa" -> ES
                "fas" -> FA
                "fra" -> FR
                "kor" -> KO
                "jpn" -> JA
                "chi_sim" -> ZH
                "chi_tra" -> ZH_HANT
                else -> null
            }

        fun splitOcrLanguages(languages: String): List<String> =
            languages
                .split("+")
                .map {
                    it.trim()
                }.filter { it.isNotEmpty() }
    }

    private val logger = KotlinLogging.logger {}

    override val moduleId: String = MODULE_ID

    override val desc: String = "Optical Character Recognition Module"

    private val lock = createPlatformLock()

    private val trainedDataPath =
        run {
            val modulePath =
                appPathProvider.resolve(
                    appFileType = AppFileType.MODULE,
                )

            val path = modulePath.resolve(moduleId)
            appPathProvider.autoCreateDir(path)
            path
        }

    private var currentOrcLanguages: String? = null

    private var api: TessBaseAPI? = null

    private fun createApi(ocrLanguage: String): TessBaseAPI? {
        val innerApi = TessBaseAPI()
        return if (innerApi.Init(trainedDataPath.toString(), ocrLanguage) != 0) {
            null
        } else {
            innerApi.SetPageSegMode(6)
            innerApi
        }
    }

    private fun existLanguages(languages: List<String>): List<String> =
        languages.filter { language ->
            val trainedDataFile = trainedDataPath.resolve("$language.traineddata")
            fileUtils.existFile(trainedDataFile)
        }

    private fun getDownloadUrl(trainedDataName: String): String =
        "https://github.com/tesseract-ocr/tessdata/raw/main/${getFileName(trainedDataName)}"

    private fun getFileName(trainedDataName: String): String = "$trainedDataName.traineddata"

    private fun updateOrCreateApi(newOcrLanguages: String) {
        configManager.updateConfig("ocrLanguage", newOcrLanguages)
        if (newOcrLanguages.isEmpty()) {
            currentOrcLanguages = null
            api?.End()
            api = null
        } else if (currentOrcLanguages != newOcrLanguages) {
            currentOrcLanguages = newOcrLanguages
            api?.End()
            api = createApi(newOcrLanguages)
        }
    }

    override fun addLanguage(language: String) {
        lock.withLock {
            getTrainedDataName(language)?.let {
                val ocrLanguage = configManager.getCurrentConfig().ocrLanguage
                val languageSet = splitOcrLanguages(ocrLanguage).toSet()

                val newLanguageSet = languageSet + it
                val newOcrLanguages = newLanguageSet.joinToString("+")
                updateOrCreateApi(newOcrLanguages)
            }
        }
    }

    override fun removeLanguage(language: String) {
        lock.withLock {
            val ocrLanguageList = splitOcrLanguages(configManager.getCurrentConfig().ocrLanguage)

            val newOcrLanguages =
                ocrLanguageList
                    .filter { getTrainedDataName(language) != it }
                    .joinToString("+")
            updateOrCreateApi(newOcrLanguages)
        }
    }

    override fun extractText(path: Path): Result<String> =
        lock.withLock {
            val ocrLanguage = configManager.getCurrentConfig().ocrLanguage
            val ocrLanguageList = splitOcrLanguages(ocrLanguage)
            if (ocrLanguageList.isEmpty()) {
                navigateManager.navigate(OCR)
                return@withLock Result.failure(IllegalStateException("OCR languages are not ready"))
            } else {
                val existLanguages = existLanguages(ocrLanguageList)
                if (existLanguages.size != ocrLanguageList.size) {
                    if (existLanguages.isEmpty()) {
                        updateOrCreateApi("")
                        navigateManager.navigate(OCR)
                        return@withLock Result.failure(IllegalStateException("OCR languages are not ready"))
                    } else {
                        val newOcrLanguages = existLanguages.joinToString("+")
                        updateOrCreateApi(newOcrLanguages)
                    }
                }

                if (api == null) {
                    Result.failure(IllegalStateException("Failed to initialize Tesseract API"))
                } else {
                    extractTextFromImage(api!!, path)
                }
            }
        }

    private fun extractTextFromImage(
        api: TessBaseAPI,
        path: Path,
    ): Result<String> =
        runCatching {
            val image = leptonica.pixRead(path.toString())
            try {
                api.SetImage(image)

                api.Recognize(null)

                val text =
                    buildString {
                        val iterator = api.GetIterator()
                        val level = tesseract.RIL_SYMBOL

                        if (iterator != null) {
                            var previousChar: String? = null
                            var isNewWord: Boolean

                            do {
                                isNewWord = iterator.IsAtBeginningOf(tesseract.RIL_WORD)

                                val charPointer = iterator.GetUTF8Text(level)
                                val currentChar = charPointer?.string

                                if (currentChar != null && currentChar.isNotEmpty()) {
                                    if (isNewWord && previousChar != null && isNotEmpty()) {
                                        if (shouldAddSpace(previousChar, currentChar)) {
                                            append(" ")
                                        }
                                    }

                                    append(currentChar)
                                    previousChar = currentChar
                                }
                            } while (iterator.Next(level))

                            iterator.deallocate()
                        }
                    }
                text
            } finally {
                api.Clear()
                leptonica.pixDestroy(image)
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to extract text from $path" }
        }

    private fun shouldAddSpace(
        lastText: String,
        currentText: String,
    ): Boolean {
        if (lastText.isEmpty() || currentText.isEmpty()) return false

        val lastChar = lastText.last()
        val currentChar = currentText.first()

        val isCJK = { c: Char ->
            c in '\u4e00'..'\u9fa5' ||
                c in '\u3040'..'\u309f' ||
                c in '\u30a0'..'\u30ff' ||
                c in '\uac00'..'\ud7af'
        }

        val isChinesePunctuation = { c: Char ->
            c in "，。！？、：；（）【】《》"
        }

        return !(
            isCJK(lastChar) ||
                isCJK(currentChar) ||
                isChinesePunctuation(lastChar) ||
                isChinesePunctuation(currentChar)
        )
    }

    override fun getCurrentFilePathList(): List<Path> =
        appPathProvider
            .resolve(moduleId, AppFileType.MODULE)
            .let { modulePath ->
                fileUtils.listFiles(modulePath)
            }

    override fun getFilePath(taskId: String): Path? =
        getTrainedDataName(taskId)?.let { trainedDataName ->
            appPathProvider
                .resolve(moduleId, AppFileType.MODULE)
                .resolve("$trainedDataName.traineddata")
        }

    override fun getModuleInitDownloadState(): ModuleDownloadState {
        val languages = copywriter.getAllLanguages()
        val ocrPath =
            appPathProvider
                .resolve(moduleId, AppFileType.MODULE)
        val allTrainedDataPathList =
            languages
                .mapNotNull { language -> getTrainedDataName(language.abridge) }
                .map { name -> ocrPath.resolve("$name.traineddata") }

        val existTrainedDataPathList = getCurrentFilePathList()

        val fileStates =
            buildMap {
                allTrainedDataPathList.forEach { path ->
                    getLanguageName(path.name.removeSuffix(".traineddata"))?.let { languageName ->
                        put(
                            languageName,
                            if (path in existTrainedDataPathList) {
                                DownloadState.Completed(languageName, path)
                            } else {
                                DownloadState.Idle(languageName)
                            },
                        )
                    }
                }
            }

        return ModuleDownloadState(
            moduleId = moduleId,
            totalFiles = allTrainedDataPathList.size,
            completedFiles = existTrainedDataPathList.size,
            fileStates = fileStates,
        )
    }

    override fun createDownloadTask(id: String): DownloadTask? =
        getTrainedDataName(id)?.let { trainedDataName ->
            val url = getDownloadUrl(trainedDataName)
            val fileName = getFileName(trainedDataName)
            DownloadTask(
                id = id,
                url = url,
                fileName = fileName,
                savePath = appPathProvider.resolve(moduleId, AppFileType.MODULE),
                moduleId = moduleId,
            )
        }
}
