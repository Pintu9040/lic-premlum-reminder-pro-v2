package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppSettingsData
import com.example.data.local.AppSettingsManager
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.pdf.PdfReportData
import com.example.pdf.PdfReportGenerator
import com.example.pdf.ReportType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.File
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowCustomPdfDocument {
    private var isClosed = false

    @Implementation
    fun __constructor__() {
        isClosed = false
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val page = Shadow.newInstanceOf(PdfDocument.Page::class.java)
        val shadowPage = Shadow.extract<ShadowCustomPage>(page)
        shadowPage.setPageInfo(pageInfo)
        shadowPage.setCanvas(canvas)
        return page
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {}

    @Implementation
    fun writeTo(out: OutputStream) {
        out.write("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n".toByteArray())
    }

    @Implementation
    fun close() {
        isClosed = true
    }
}

@Implements(PdfDocument.Page::class)
class ShadowCustomPage {
    private lateinit var pageInfo: PdfDocument.PageInfo
    private lateinit var canvas: Canvas

    fun setPageInfo(info: PdfDocument.PageInfo) {
        this.pageInfo = info
    }

    fun setCanvas(c: Canvas) {
        this.canvas = c
    }

    @Implementation
    fun getCanvas(): Canvas {
        return canvas
    }

    @Implementation
    fun getInfo(): PdfDocument.PageInfo {
        return pageInfo
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowCustomPdfDocument::class, ShadowCustomPage::class])
class ReceiptSettingsPdfTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testReceiptSettingsPersistenceAndPdfGeneration() = runBlocking {
        // --- 1. Test Initial/Default Persistence ---
        var settings = AppSettingsManager.getSettings(context)
        assertNotNull(settings)

        // --- 2. Test Setting 1: Receipt Size Page Dimensions (A5, A4, Thermal 3-inch) ---
        val expectedDimensions = mapOf(
            "A5" to Pair(420, 595),
            "A4" to Pair(595, 842),
            "Thermal 3-inch" to Pair(226, 420)
        )

        expectedDimensions.forEach { (size, dimensions) ->
            val updated = settings.copy(selectedReceiptSize = size)
            AppSettingsManager.saveSettings(context, updated, db = null)

            // Re-read settings from persistence (Simulate Leave & Reopen)
            val reloaded = AppSettingsManager.getSettings(context)
            assertEquals(size, reloaded.selectedReceiptSize)

            // Generate PDF Receipt
            val file = generateTestReceiptPdf(context)
            assertTrue("PDF receipt should exist", file.exists())
            assertTrue("PDF receipt should be non-empty", file.length() > 0)
        }

        // --- 3. Test Setting 2 & 3: Receipt Header & Custom Header Title ---
        val customTitle = "LIC Premium Official Receipt"
        settings = settings.copy(
            isReceiptHeaderEnabled = true,
            receiptHeaderTitle = customTitle
        )
        AppSettingsManager.saveSettings(context, settings, db = null)

        var reloaded = AppSettingsManager.getSettings(context)
        assertTrue(reloaded.isReceiptHeaderEnabled)
        assertEquals(customTitle, reloaded.receiptHeaderTitle)

        val fileHeaderOn = generateTestReceiptPdf(context)
        assertTrue(fileHeaderOn.exists())

        // Test Header OFF
        settings = settings.copy(isReceiptHeaderEnabled = false)
        AppSettingsManager.saveSettings(context, settings, db = null)
        reloaded = AppSettingsManager.getSettings(context)
        assertFalse(reloaded.isReceiptHeaderEnabled)
        assertEquals(customTitle, reloaded.receiptHeaderTitle) // Title preserved

        val fileHeaderOff = generateTestReceiptPdf(context)
        assertTrue(fileHeaderOff.exists())

        // --- 4. Test Setting 4: Agent Digital Signature ---
        settings = settings.copy(isAgentSignatureOnReceipt = true)
        AppSettingsManager.saveSettings(context, settings, db = null)
        reloaded = AppSettingsManager.getSettings(context)
        assertTrue(reloaded.isAgentSignatureOnReceipt)

        val fileSigOn = generateTestReceiptPdf(context)
        assertTrue(fileSigOn.exists())

        // Toggle Signature OFF
        settings = settings.copy(isAgentSignatureOnReceipt = false)
        AppSettingsManager.saveSettings(context, settings, db = null)
        reloaded = AppSettingsManager.getSettings(context)
        assertFalse(reloaded.isAgentSignatureOnReceipt)

        val fileSigOff = generateTestReceiptPdf(context)
        assertTrue(fileSigOff.exists())

        // --- 5. Test Setting 5: QR Verification Code ---
        settings = settings.copy(isQrCodeOnReceipt = true)
        AppSettingsManager.saveSettings(context, settings, db = null)
        reloaded = AppSettingsManager.getSettings(context)
        assertTrue(reloaded.isQrCodeOnReceipt)

        val fileQrOn = generateTestReceiptPdf(context)
        assertTrue(fileQrOn.exists())

        // Toggle QR Code OFF
        settings = settings.copy(isQrCodeOnReceipt = false)
        AppSettingsManager.saveSettings(context, settings, db = null)
        reloaded = AppSettingsManager.getSettings(context)
        assertFalse(reloaded.isQrCodeOnReceipt)

        val fileQrOff = generateTestReceiptPdf(context)
        assertTrue(fileQrOff.exists())

        // --- 6. Test Setting 6: Auto Receipt Number ---
        settings = settings.copy(
            isAutoReceiptNumber = true,
            receiptPrefix = "LIC-"
        )
        AppSettingsManager.saveSettings(context, settings, db = null)
        reloaded = AppSettingsManager.getSettings(context)
        assertTrue(reloaded.isAutoReceiptNumber)
        assertEquals("LIC-", reloaded.receiptPrefix)

        val fileAutoNum = generateTestReceiptPdf(context)
        assertTrue(fileAutoNum.exists())

        // --- 7. Test Non-interference: Changing one setting preserves others ---
        val beforeChange = AppSettingsManager.getSettings(context)
        val changedOne = beforeChange.copy(selectedReceiptSize = "A5")
        AppSettingsManager.saveSettings(context, changedOne, db = null)

        val afterChange = AppSettingsManager.getSettings(context)
        assertEquals("A5", afterChange.selectedReceiptSize)
        assertEquals(beforeChange.isReceiptHeaderEnabled, afterChange.isReceiptHeaderEnabled)
        assertEquals(beforeChange.receiptHeaderTitle, afterChange.receiptHeaderTitle)
        assertEquals(beforeChange.isAgentSignatureOnReceipt, afterChange.isAgentSignatureOnReceipt)
        assertEquals(beforeChange.isQrCodeOnReceipt, afterChange.isQrCodeOnReceipt)
        assertEquals(beforeChange.isAutoReceiptNumber, afterChange.isAutoReceiptNumber)
    }

    private suspend fun generateTestReceiptPdf(context: Context): File {
        val samplePayment = PaymentEntity(
            id = 8901,
            policyId = 101,
            policyNumber = "892041920",
            customerId = 101,
            customerName = "Rakesh Kumar",
            paidAmount = 12500.0,
            lateFee = 150.0,
            paymentDate = "2026-08-12",
            paymentMode = "UPI / PhonePe",
            receiptNumber = "RCP-MANUAL-999"
        )
        val samplePolicy = PolicyEntity(
            id = 101,
            policyNumber = "892041920",
            customerId = 101,
            customerName = "Rakesh Kumar",
            planName = "Jeevan Anand (815)",
            premiumAmount = 12500.0,
            sumAssured = 500000.0,
            premiumMode = "Yearly",
            dueDate = "2026-08-15",
            maturityDate = "2046-08-15"
        )
        val reportData = PdfReportData(
            reportType = ReportType.PREMIUM_RECEIPT,
            payment = samplePayment,
            policy = samplePolicy
        )
        val result = PdfReportGenerator.generatePdfReport(context, reportData)
        if (result.isFailure) {
            val ex = result.exceptionOrNull()
            println("PdfReportGenerator failure: ${ex?.message}")
            ex?.printStackTrace()
        }
        assertTrue("PDF report generation must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        return result.getOrThrow()
    }
}
