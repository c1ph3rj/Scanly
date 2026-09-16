package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.CompressPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.InspectPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.MergePdfsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RemovePdfPasswordUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.SetPdfPasswordUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.WatermarkPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.GenerateQrBitmapUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.SaveQrPngUseCase
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.launch.ScanlyLaunchAction
import `in`.c1ph3rj.scanly.navigation.ScanlyDestination
import `in`.c1ph3rj.scanly.navigation.ToolsQrDestination
import `in`.c1ph3rj.scanly.testing.InMemoryDocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryPdfToolkitRepository
import `in`.c1ph3rj.scanly.testing.InMemoryQrCodeRepository
import `in`.c1ph3rj.scanly.testing.InMemoryScanlyLibrary
import `in`.c1ph3rj.scanly.testing.InMemorySettingsRepository
import `in`.c1ph3rj.scanly.testing.requireSuccess
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsSettingsAndLaunchActionFlowTest {
    private val settings = InMemorySettingsRepository()
    private val qrDirectory = createTempDirectory("scanly-qr-flow").toFile()
    private val pdfDirectory = createTempDirectory("scanly-pdf-flow").toFile()
    private val qrRepository = InMemoryQrCodeRepository(qrDirectory)
    private val pdfRepository = InMemoryPdfToolkitRepository(pdfDirectory)
    private val documents = InMemoryDocumentRepository(InMemoryScanlyLibrary())

    private val generateQr = GenerateQrBitmapUseCase(qrRepository)
    private val saveQr = SaveQrPngUseCase(qrRepository)
    private val inspectPdf = InspectPdfUseCase(pdfRepository)
    private val mergePdfs = MergePdfsUseCase(pdfRepository)
    private val compressPdf = CompressPdfUseCase(pdfRepository)
    private val setPdfPassword = SetPdfPasswordUseCase(pdfRepository)
    private val removePdfPassword = RemovePdfPasswordUseCase(pdfRepository)
    private val watermarkPdf = WatermarkPdfUseCase(pdfRepository)

    private val completeOnboarding = CompleteOnboardingUseCase(settings)
    private val observeOnboarding = ObserveOnboardingCompletedUseCase(settings)
    private val setThemeMode = SetThemeModeUseCase(settings)
    private val observeThemeMode = ObserveThemeModeUseCase(settings)
    private val setPureBlack = SetPureBlackEnabledUseCase(settings)
    private val observePureBlack = ObservePureBlackEnabledUseCase(settings)
    private val setLiveModel = SetLiveDetectionModelUseCase(settings)
    private val observeLiveModel = ObserveLiveDetectionModelUseCase(settings)
    private val setPostModel = SetPostProcessingModelUseCase(settings)
    private val observePostModel = ObservePostProcessingModelUseCase(settings)
    private val setAutomatic = SetAutomaticModelSelectionUseCase(settings)
    private val observeAutomatic = ObserveAutomaticModelSelectionUseCase(settings)
    private val setGate = SetDocumentGateEnabledUseCase(settings)
    private val observeGate = ObserveDocumentGateEnabledUseCase(settings)

    private val suggestTitle = SuggestDocumentTitleUseCase(documents)
    private val createDocument = CreateDocumentUseCase(documents)

    @Test
    fun qrGenerate_encodesContentAndWritesNonEmptyArtifact() = runBlocking {
        val content = "https://scanly.app/docs"
        val bitmapResult = generateQr(content)
        assertEquals(content, qrRepository.lastGeneratedContent)
        assertTrue(qrRepository.lastGeneratedWidth > 0)
        val bitmapFailure = bitmapResult as ScanlyResult.Failure
        assertTrue(bitmapFailure.error.message.contains("Bitmap is unavailable"))

        val artifact = saveQr(content).requireSuccess()
        assertEquals("image/png", artifact.mimeType)
        val file = File(artifact.filePath)
        assertTrue(file.length() > 0L)
        assertTrue(file.readText().contains(content))
        assertTrue(file.readText().contains("1") || file.readText().contains("0"))
    }

    @Test
    fun pdfToolkitInspectMergeCompressPasswordAndWatermark_returnArtifacts() = runBlocking {
        val first = PdfToolSource.AppFile("/library/one.pdf", "one.pdf")
        val second = PdfToolSource.AppFile("/library/two.pdf", "two.pdf")

        val info = inspectPdf(first, password = "secret").requireSuccess()
        assertEquals("one.pdf", info.displayName)
        assertEquals(2, info.pageCount)
        assertTrue(info.isEncrypted)
        assertEquals(first, pdfRepository.lastInspectedSource)
        assertEquals("secret", pdfRepository.lastInspectedPassword)

        val merged = mergePdfs(listOf(first, second)).requireSuccess()
        assertEquals(listOf(first, second), pdfRepository.lastMergeSources)
        assertTrue(File(merged.filePath).length() > 0L)

        val compressed = compressPdf(first, PdfCompressQuality.MEDIUM).requireSuccess()
        assertEquals(PdfCompressQuality.MEDIUM, pdfRepository.lastCompressQuality)
        assertTrue(File(compressed.filePath).length() > 0L)

        val protectedPdf = setPdfPassword(first, newPassword = "lock").requireSuccess()
        assertEquals("lock", pdfRepository.lastSetPassword)
        assertTrue(File(protectedPdf.filePath).length() > 0L)

        val unlocked = removePdfPassword(first, currentPassword = "lock").requireSuccess()
        assertEquals("lock", pdfRepository.lastRemovedPassword)
        assertTrue(File(unlocked.filePath).length() > 0L)

        val options = WatermarkOptions(
            text = "DRAFT",
            layout = WatermarkLayout.REPEATED,
            pageRange = WatermarkPageRange.FIRST_PAGE,
        )
        val watermarked = watermarkPdf(first, options).requireSuccess()
        assertEquals(options, pdfRepository.lastWatermarkOptions)
        assertTrue(File(watermarked.filePath).readText().contains("DRAFT"))
    }

    @Test
    fun appearanceDetectionAndOnboardingPrefs_persistThroughUseCases() = runBlocking {
        assertFalse(observeOnboarding().first())
        completeOnboarding().requireSuccess()
        assertTrue(observeOnboarding().first())

        assertEquals(ThemeMode.SYSTEM, observeThemeMode().first())
        setThemeMode(ThemeMode.DARK).requireSuccess()
        assertEquals(ThemeMode.DARK, observeThemeMode().first())

        assertFalse(observePureBlack().first())
        setPureBlack(true).requireSuccess()
        assertTrue(observePureBlack().first())

        assertTrue(observeAutomatic().first())
        setAutomatic(false).requireSuccess()
        assertFalse(observeAutomatic().first())

        setLiveModel(DocumentCornerModel.LITE).requireSuccess()
        setPostModel(DocumentCornerModel.HIGH).requireSuccess()
        assertEquals(DocumentCornerModel.LITE, observeLiveModel().first())
        assertEquals(DocumentCornerModel.HIGH, observePostModel().first())

        assertTrue(observeGate().first())
        setGate(false).requireSuccess()
        assertFalse(observeGate().first())
    }

    @Test
    fun launchActions_mapToDocumentedRedirects() = runBlocking {
        assertEquals(
            ScanlyLaunchAction.Scan,
            ScanlyLaunchAction.fromActionAndExtra(ScanlyLaunchAction.Scan.intentAction, null),
        )
        val scanDocumentId = createDocument(suggestTitle()).requireSuccess()
        assertEquals(
            "camera/session/$scanDocumentId",
            ScanSessionDestination.route(scanDocumentId),
        )

        assertEquals(
            ScanlyLaunchAction.Import,
            ScanlyLaunchAction.fromActionAndExtra(ScanlyLaunchAction.Import.intentAction, null),
        )
        val importedId = createDocument.createImported().requireSuccess()
        assertEquals("document/$importedId", DocumentDestination.route(importedId))

        assertEquals(
            ScanlyLaunchAction.Qr,
            ScanlyLaunchAction.fromActionAndExtra(ScanlyLaunchAction.Qr.intentAction, null),
        )
        assertEquals("tools/qr?mode=scan", ToolsQrDestination.route("scan"))

        assertEquals(
            ScanlyLaunchAction.Library,
            ScanlyLaunchAction.fromActionAndExtra(ScanlyLaunchAction.Library.intentAction, null),
        )
        assertEquals("library", ScanlyDestination.Library.route)
    }
}
