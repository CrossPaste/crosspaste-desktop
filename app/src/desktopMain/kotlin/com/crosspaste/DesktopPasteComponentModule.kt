package com.crosspaste

import com.crosspaste.clean.CleanScheduler
import com.crosspaste.headless.HeadlessPasteboardService
import com.crosspaste.image.GenerateImageService
import com.crosspaste.image.ImageHandler
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.DefaultPasteSyncProcessManager
import com.crosspaste.paste.DesktopCurrentPaste
import com.crosspaste.paste.DesktopGuidePasteDataService
import com.crosspaste.paste.DesktopPasteExportParamFactory
import com.crosspaste.paste.DesktopPasteImportParamFactory
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.DesktopPasteTagMenuService
import com.crosspaste.paste.DesktopSearchContentService
import com.crosspaste.paste.DesktopSourceExclusionService
import com.crosspaste.paste.DesktopTransferableConsumer
import com.crosspaste.paste.DesktopTransferableProducer
import com.crosspaste.paste.GuidePasteDataService
import com.crosspaste.paste.PasteExportParamFactory
import com.crosspaste.paste.PasteExportService
import com.crosspaste.paste.PasteImportParamFactory
import com.crosspaste.paste.PasteImportService
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.TransferableConsumer
import com.crosspaste.paste.TransferableProducer
import com.crosspaste.paste.getDesktopPasteboardService
import com.crosspaste.paste.item.UpdatePasteItemHelper
import com.crosspaste.paste.plugin.process.DistinctPlugin
import com.crosspaste.paste.plugin.process.FileToUrlPlugin
import com.crosspaste.paste.plugin.process.FilesToImagesPlugin
import com.crosspaste.paste.plugin.process.GenerateTextPlugin
import com.crosspaste.paste.plugin.process.GenerateUrlPlugin
import com.crosspaste.paste.plugin.process.RemoveFolderImagePlugin
import com.crosspaste.paste.plugin.process.RemoveHtmlImagePlugin
import com.crosspaste.paste.plugin.process.RemoveInvalidPlugin
import com.crosspaste.paste.plugin.process.SortPlugin
import com.crosspaste.paste.plugin.process.TextToColorPlugin
import com.crosspaste.paste.plugin.type.ColorTypePlugin
import com.crosspaste.paste.plugin.type.FilesTypePlugin
import com.crosspaste.paste.plugin.type.HtmlTypePlugin
import com.crosspaste.paste.plugin.type.ImageTypePlugin
import com.crosspaste.paste.plugin.type.RtfTypePlugin
import com.crosspaste.paste.plugin.type.TextTypePlugin
import com.crosspaste.paste.plugin.type.UrlTypePlugin
import com.crosspaste.rendering.OpenGraphService
import com.crosspaste.rendering.RenderingService
import com.crosspaste.sync.FilePullService
import com.crosspaste.task.CleanPasteTaskExecutor
import com.crosspaste.task.CleanTaskTaskExecutor
import com.crosspaste.task.DelayedDeletePasteTaskExecutor
import com.crosspaste.task.DeletePasteTaskExecutor
import com.crosspaste.task.DesktopTaskSubmitter
import com.crosspaste.task.OpenGraphTaskExecutor
import com.crosspaste.task.PullFileTaskExecutor
import com.crosspaste.task.PullIconTaskExecutor
import com.crosspaste.task.SwitchLanguageTaskExecutor
import com.crosspaste.task.SyncPasteTaskExecutor
import com.crosspaste.task.TaskExecutor
import com.crosspaste.task.TaskSubmitter
import okio.Path
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.awt.image.BufferedImage

fun desktopPasteComponentModule(headless: Boolean): Module =
    module {
        single<CleanScheduler> { CleanScheduler(get(), get(), get()) }
        single<CurrentPaste> { DesktopCurrentPaste(lazy { get() }) }
        single<RenderingService<String>>(named("urlRendering")) {
            OpenGraphService(get(), get<ImageHandler<BufferedImage>>(), get(), get(), get())
        }
        single<DesktopPasteMenuService> {
            DesktopPasteMenuService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
        }
        single<DesktopPasteTagMenuService> {
            DesktopPasteTagMenuService(get(), get())
        }
        single<FilePullService> { FilePullService(get(), get(), get(), get()) }
        single<PasteReleaseService> {
            PasteReleaseService(
                commonConfigManager = get(),
                currentPaste = get(),
                database = get(),
                pasteDao = get(),
                pasteProcessPlugins =
                    listOf(
                        RemoveInvalidPlugin,
                        DistinctPlugin(get()),
                        GenerateTextPlugin,
                        GenerateUrlPlugin,
                        TextToColorPlugin,
                        FilesToImagesPlugin(get()),
                        FileToUrlPlugin(get()),
                        RemoveFolderImagePlugin(get()),
                        RemoveHtmlImagePlugin(get()),
                        SortPlugin,
                    ),
                searchContentService = get(),
                taskSubmitter = get(),
                userDataPathProvider = get(),
            )
        }
        single<GenerateImageService> { GenerateImageService() }
        single<GuidePasteDataService> { DesktopGuidePasteDataService(get(), get(), get(), get(), get()) }
        single<DesktopSourceExclusionService> { DesktopSourceExclusionService(get()) }
        single<PasteboardService> {
            if (headless) {
                HeadlessPasteboardService(get(), get())
            } else {
                getDesktopPasteboardService(
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
        }
        single<PasteExportParamFactory<Path>> { DesktopPasteExportParamFactory() }
        single<PasteExportService> { PasteExportService(get(), get(), get()) }
        single<PasteImportParamFactory<Path>> { DesktopPasteImportParamFactory() }
        single<PasteImportService> { PasteImportService(get(), get(), get(), get()) }
        single<PasteSyncProcessManager<Long>> { DefaultPasteSyncProcessManager() }
        single<SearchContentService> { DesktopSearchContentService() }
        single<TaskExecutor> {
            TaskExecutor(
                listOf(
                    CleanPasteTaskExecutor(get(), get()),
                    CleanTaskTaskExecutor(get()),
                    DelayedDeletePasteTaskExecutor(get()),
                    DeletePasteTaskExecutor(get()),
                    OpenGraphTaskExecutor(
                        lazy { get<RenderingService<String>>(named("urlRendering")) },
                        get(),
                    ),
                    PullFileTaskExecutor(get(), get(), get(), get(), get(), get()),
                    PullIconTaskExecutor(get(), get(), get(), get()),
                    SwitchLanguageTaskExecutor(get(), get()),
                    SyncPasteTaskExecutor(get(), get(), get(), get(), get(), get()),
                ),
                get(),
            )
        }
        single<TaskSubmitter> { DesktopTaskSubmitter(get(), get(), get(), lazy { get() }) }
        single<TransferableConsumer> {
            DesktopTransferableConsumer(
                get(),
                get(),
                get(),
                listOf(
                    get<ColorTypePlugin>(),
                    get<FilesTypePlugin>(),
                    get<HtmlTypePlugin>(),
                    get<RtfTypePlugin>(),
                    get<ImageTypePlugin>(),
                    get<TextTypePlugin>(),
                    get<UrlTypePlugin>(),
                ),
            )
        }
        single<TransferableProducer> {
            DesktopTransferableProducer(
                listOf(
                    get<FilesTypePlugin>(),
                    get<HtmlTypePlugin>(),
                    get<RtfTypePlugin>(),
                    get<ImageTypePlugin>(),
                    get<TextTypePlugin>(),
                    get<UrlTypePlugin>(),
                ),
            )
        }
        single<UpdatePasteItemHelper> {
            UpdatePasteItemHelper(get(), get())
        }
    }
