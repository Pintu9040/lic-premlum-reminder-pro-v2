package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.AgentProfileEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.ui.payment.PolicyPaymentSummary
import com.example.ui.payment.calculatePaymentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class ReportType(val title: String, val fileNamePrefix: String) {
    CUSTOMER_PROFILE("Customer Profile", "Customer_Profile"),
    POLICY_DETAILS("Policy Details", "Policy_Details"),
    PREMIUM_RECEIPT("Premium Receipt", "Premium_Receipt"),
    PAYMENT_RECEIPT("Payment Receipt", "Payment_Receipt"),
    DUE_TODAY("Due Today Report", "Due_Today"),
    TOMORROW_DUE("Tomorrow Due Report", "Tomorrow_Due"),
    UPCOMING_DUE("Upcoming Due Report", "Upcoming_Due"),
    OVERDUE("Overdue Report", "Overdue_Report"),
    MONTHLY_COLLECTION("Monthly Collection Report", "Monthly_Collection"),
    OUTSTANDING_PREMIUM("Outstanding Premium Report", "Outstanding_Premium"),
    COMPLETE_PORTFOLIO("Complete Portfolio Report", "Complete_Portfolio"),
    CUSTOMER_PAYMENT_HISTORY("Customer Payment History", "Customer_Payment_History")
}

data class PdfReportData(
    val reportType: ReportType,
    val agentProfile: AgentProfileEntity? = null,
    val customer: CustomerEntity? = null,
    val policy: PolicyEntity? = null,
    val payment: PaymentEntity? = null,
    val customerList: List<CustomerEntity> = emptyList(),
    val policyList: List<PolicyEntity> = emptyList(),
    val paymentList: List<PaymentEntity> = emptyList(),
    val totalAmount: Double = 0.0,
    val filterPeriod: String = "Current Period"
)

object PdfReportGenerator {
    private const val TAG = "PdfReportGenerator"
    private const val PAGE_WIDTH = 595 // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 36 // 0.5 inch margin

    // Colors
    private val COLOR_ROYAL_BLUE = Color.parseColor("#0B2F64")
    private val COLOR_NAVY_DARK = Color.parseColor("#071E3D")
    private val COLOR_GOLD = Color.parseColor("#D97706")
    private val COLOR_TEXT_DARK = Color.parseColor("#0F172A")
    private val COLOR_TEXT_MUTED = Color.parseColor("#475569")
    private val COLOR_ALT_ROW = Color.parseColor("#F1F5F9")
    private val COLOR_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_WHITE = Color.WHITE
    private val COLOR_SUCCESS = Color.parseColor("#15803D")
    private val COLOR_DANGER = Color.parseColor("#B91C1C")

    suspend fun generatePdfReport(context: Context, data: PdfReportData): Result<File> = withContext(Dispatchers.IO) {
        try {
            val settings = com.example.data.local.AppSettingsManager.getSettings(context, data.agentProfile)
            val isReceipt = data.reportType == ReportType.PREMIUM_RECEIPT || data.reportType == ReportType.PAYMENT_RECEIPT

            val (pageWidth, pageHeight, margin) = if (isReceipt) {
                when (settings.selectedReceiptSize) {
                    "A5" -> Triple(420, 595, 24)
                    "Thermal 3-inch", "Thermal" -> Triple(226, 420, 10)
                    else -> Triple(595, 842, 36)
                }
            } else {
                Triple(PAGE_WIDTH, PAGE_HEIGHT, MARGIN)
            }

            val pdfDocument = PdfDocument()
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            var yPos = drawHeader(canvas, data, pageNum, settings, pageWidth, margin)

            when (data.reportType) {
                ReportType.CUSTOMER_PROFILE -> {
                    yPos = drawCustomerProfileContent(canvas, data)
                }
                ReportType.POLICY_DETAILS -> {
                    yPos = drawPolicyDetailsContent(canvas, data)
                }
                ReportType.PREMIUM_RECEIPT, ReportType.PAYMENT_RECEIPT -> {
                    yPos = drawReceiptContent(canvas, data, settings, pageWidth, pageHeight, margin)
                }
                ReportType.DUE_TODAY,
                ReportType.TOMORROW_DUE,
                ReportType.UPCOMING_DUE,
                ReportType.OVERDUE,
                ReportType.OUTSTANDING_PREMIUM -> {
                    yPos = drawDueOrOverdueReportContent(canvas, data)
                }
                ReportType.MONTHLY_COLLECTION -> {
                    yPos = drawMonthlyCollectionContent(canvas, data)
                }
                ReportType.COMPLETE_PORTFOLIO,
                ReportType.CUSTOMER_PAYMENT_HISTORY -> {
                    yPos = drawPortfolioReportContent(canvas, data)
                }
            }

            drawFooter(canvas, data, pageNum, pageWidth, pageHeight, margin)
            pdfDocument.finishPage(page)

            // Ensure directory exists
            val reportsDir = getReportsDirectory(context)
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val pdfFile = File(reportsDir, "LIC_${data.reportType.fileNamePrefix}_$timeStamp.pdf")

            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            Log.i(TAG, "Successfully generated PDF report (${settings.selectedReceiptSize}): ${pdfFile.absolutePath}")
            Result.success(pdfFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate PDF report: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    private fun getReportsDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        return File(baseDir, "LIC Premium Reminder Pro/Reports")
    }

    private fun drawHeader(
        canvas: Canvas,
        data: PdfReportData,
        pageNum: Int,
        settings: com.example.data.local.AppSettingsData? = null,
        pageWidth: Int = PAGE_WIDTH,
        margin: Int = MARGIN
    ): Float {
        val isReceipt = data.reportType == ReportType.PREMIUM_RECEIPT || data.reportType == ReportType.PAYMENT_RECEIPT

        if (isReceipt && settings?.isReceiptHeaderEnabled == false) {
            return margin.toFloat() + 10f
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Banner Background
        paint.color = COLOR_ROYAL_BLUE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 110f, paint)

        // Gold Decorative Strip
        paint.color = COLOR_GOLD
        canvas.drawRect(0f, 110f, pageWidth.toFloat(), 114f, paint)

        // Header Title
        paint.color = COLOR_WHITE
        paint.textSize = if (pageWidth < 300) 12f else 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val headerTitle = if (isReceipt && settings != null && settings.receiptHeaderTitle.isNotBlank()) {
            settings.receiptHeaderTitle.uppercase(Locale.getDefault())
        } else {
            "LIC PREMIUM REMINDER PRO"
        }
        canvas.drawText(headerTitle, margin.toFloat(), 35f, paint)

        // Report Title
        paint.textSize = if (pageWidth < 300) 10f else 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = COLOR_GOLD
        canvas.drawText(data.reportType.title.uppercase(Locale.getDefault()), margin.toFloat(), 55f, paint)

        if (pageWidth >= 400) {
            // Agent Profile Info in Header (Right aligned)
            val agent = data.agentProfile
            val agentName = agent?.agentName ?: settings?.agentName ?: "LIC Advisor"
            val agencyCode = agent?.agencyCode ?: settings?.agencyCode ?: "LIC-089421"
            val branchInfo = "${agent?.branchName ?: settings?.branchName ?: "Branch"} (${agent?.branchCode ?: settings?.branchCode ?: "08B"})"
            val contactInfo = "Ph: ${agent?.mobile ?: settings?.mobileNumber ?: ""}"

            paint.textSize = 9f
            paint.color = COLOR_WHITE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.RIGHT

            val rightX = (pageWidth - margin).toFloat()
            canvas.drawText(agentName, rightX, 30f, paint)
            canvas.drawText("Agency Code: $agencyCode", rightX, 45f, paint)
            canvas.drawText(branchInfo, rightX, 60f, paint)
            canvas.drawText(contactInfo, rightX, 75f, paint)

            paint.textAlign = Paint.Align.LEFT
        }

        // Generated Timestamp Line
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val generatedText = "Report Generated: ${sdf.format(Date())}"
        paint.textSize = 8f
        paint.color = COLOR_WHITE
        canvas.drawText(generatedText, margin.toFloat(), 98f, paint)

        return 130f
    }

    private fun drawFooter(
        canvas: Canvas,
        data: PdfReportData,
        pageNum: Int,
        pageWidth: Int = PAGE_WIDTH,
        pageHeight: Int = PAGE_HEIGHT,
        margin: Int = MARGIN
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val footerY = (pageHeight - 25).toFloat()

        // Divider
        paint.color = COLOR_BORDER
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(margin.toFloat(), footerY - 10, (pageWidth - margin).toFloat(), footerY - 10, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 8f
        paint.color = COLOR_TEXT_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        canvas.drawText("LIC Premium Reminder Pro", margin.toFloat(), footerY, paint)

        val agentName = data.agentProfile?.agentName ?: "LIC Agent"
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Page $pageNum", (pageWidth - margin).toFloat(), footerY, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawCustomerProfileContent(canvas: Canvas, data: PdfReportData): Float {
        var y = 140f
        val customer = data.customer ?: return y
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Customer Info Box
        paint.color = COLOR_ALT_ROW
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y + 130f)
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        // Customer Box Header
        paint.style = Paint.Style.FILL
        paint.color = COLOR_ROYAL_BLUE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CUSTOMER PROFILE DETAILS", MARGIN + 12f, y + 24f, paint)

        paint.color = COLOR_TEXT_DARK
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val col1X = MARGIN + 12f
        val col2X = MARGIN + 280f
        var detailY = y + 46f

        drawDetailPair(canvas, paint, "Customer Name:", customer.name, col1X, detailY)
        drawDetailPair(canvas, paint, "Mobile Number:", customer.mobile, col2X, detailY)
        detailY += 18f

        drawDetailPair(canvas, paint, "WhatsApp:", customer.whatsapp.ifEmpty { customer.mobile }, col1X, detailY)
        drawDetailPair(canvas, paint, "Email Address:", customer.email.ifEmpty { "N/A" }, col2X, detailY)
        detailY += 18f

        drawDetailPair(canvas, paint, "Date of Birth:", customer.dob.ifEmpty { "N/A" }, col1X, detailY)
        drawDetailPair(canvas, paint, "Anniversary:", customer.anniversary.ifEmpty { "N/A" }, col2X, detailY)
        detailY += 18f

        drawDetailPair(canvas, paint, "Aadhaar No:", customer.aadhaar.ifEmpty { "N/A" }, col1X, detailY)
        drawDetailPair(canvas, paint, "PAN No:", customer.pan.ifEmpty { "N/A" }, col2X, detailY)
        detailY += 18f

        drawDetailPair(canvas, paint, "Address:", customer.address.ifEmpty { "N/A" }, col1X, detailY)

        y += 150f

        // Policies Section Header
        paint.color = COLOR_ROYAL_BLUE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ASSOCIATED POLICIES (${data.policyList.size})", MARGIN.toFloat(), y, paint)
        y += 15f

        // Table Header
        y = drawPolicyTableHeader(canvas, y)

        // Table Rows
        data.policyList.forEachIndexed { index, policy ->
            y = drawPolicyTableRow(canvas, policy, index + 1, y)
        }

        return y
    }

    private fun drawPolicyDetailsContent(canvas: Canvas, data: PdfReportData): Float {
        var y = 140f
        val policy = data.policy ?: return y
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Card Box
        paint.color = COLOR_ALT_ROW
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y + 260f)
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        paint.style = Paint.Style.FILL
        paint.color = COLOR_ROYAL_BLUE
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("POLICY SPECIFICATIONS & DETAILS", MARGIN + 14f, y + 28f, paint)

        val col1X = MARGIN + 14f
        val col2X = MARGIN + 280f
        var lineY = y + 54f

        drawDetailPair(canvas, paint, "Policy Number:", policy.policyNumber, col1X, lineY)
        drawDetailPair(canvas, paint, "Customer Name:", policy.customerName, col2X, lineY)
        lineY += 20f

        drawDetailPair(canvas, paint, "Plan Name:", policy.planName, col1X, lineY)
        drawDetailPair(canvas, paint, "Policy Status:", policy.status, col2X, lineY)
        lineY += 20f

        drawDetailPair(canvas, paint, "Sum Assured:", "₹${String.format("%,.0f", policy.sumAssured)}", col1X, lineY)
        drawDetailPair(canvas, paint, "Premium Amount:", "₹${String.format("%,.0f", policy.premiumAmount)}", col2X, lineY)
        lineY += 20f

        drawDetailPair(canvas, paint, "Payment Mode:", policy.premiumMode, col1X, lineY)
        drawDetailPair(canvas, paint, "Due Date:", policy.dueDate, col2X, lineY)
        lineY += 20f

        drawDetailPair(canvas, paint, "Issue Date:", policy.issueDate.ifEmpty { "N/A" }, col1X, lineY)
        drawDetailPair(canvas, paint, "Maturity Date:", policy.maturityDate.ifEmpty { "N/A" }, col2X, lineY)
        lineY += 20f

        drawDetailPair(canvas, paint, "Policy Term:", "${policy.policyTerm} Years", col1X, lineY)
        drawDetailPair(canvas, paint, "Premium Paying Term:", "${policy.premiumPayingTerm} Years", col2X, lineY)
        lineY += 20f

        drawDetailPair(canvas, paint, "Nominee:", policy.nominee.ifEmpty { "N/A" }, col1X, lineY)
        drawDetailPair(canvas, paint, "Grace Period:", "${policy.gracePeriodDays} Days", col2X, lineY)

        y += 280f

        // Payment History Header if available
        if (data.paymentList.isNotEmpty()) {
            paint.color = COLOR_ROYAL_BLUE
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("PAYMENT HISTORY", MARGIN.toFloat(), y, paint)
            y += 15f

            y = drawPaymentTableHeader(canvas, y)
            data.paymentList.forEachIndexed { index, payment ->
                y = drawPaymentTableRow(canvas, payment, index + 1, y)
            }
        }

        return y
    }

    private fun drawReceiptContent(
        canvas: Canvas,
        data: PdfReportData,
        settings: com.example.data.local.AppSettingsData? = null,
        pageWidth: Int = PAGE_WIDTH,
        pageHeight: Int = PAGE_HEIGHT,
        margin: Int = MARGIN
    ): Float {
        var y = if (settings?.isReceiptHeaderEnabled == false) margin + 10f else 140f
        val payment = data.payment
        val policy = data.policy
        val customer = data.customer
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Setting 6: Auto Receipt Number vs Manual
        val receiptNo = if (settings?.isAutoReceiptNumber != false) {
            val prefix = settings?.receiptPrefix?.ifBlank { "LIC-" } ?: "LIC-"
            "${prefix}${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}-${(payment?.id ?: 101) + 8900}"
        } else {
            payment?.receiptNumber.takeIf { !it.isNullOrBlank() } ?: "RCP-MANUAL-001"
        }

        val paidAmount = payment?.paidAmount ?: (policy?.premiumAmount ?: 0.0)
        val lateFee = payment?.lateFee ?: 0.0
        val totalPaid = paidAmount + lateFee
        val customerName = payment?.customerName ?: customer?.name ?: policy?.customerName ?: "Valued Customer"
        val policyNo = payment?.policyNumber ?: policy?.policyNumber ?: "N/A"
        val planName = policy?.planName ?: "LIC Policy"
        val payDate = payment?.paymentDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val payMode = payment?.paymentMode ?: "UPI / Online"

        // Outer Receipt Card
        val cardWidth = (pageWidth - (margin * 2)).toFloat()
        val isThermal = pageWidth < 300
        val cardHeight = if (isThermal) 360f else 340f

        paint.color = COLOR_WHITE
        paint.style = Paint.Style.FILL
        val cardRect = RectF(margin.toFloat(), y, margin + cardWidth, y + cardHeight)
        canvas.drawRoundRect(cardRect, 10f, 10f, paint)

        // Outer border
        paint.color = COLOR_ROYAL_BLUE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(cardRect, 10f, 10f, paint)

        // Setting 2 & 3: Header Band & Custom Title
        if (settings?.isReceiptHeaderEnabled != false) {
            paint.style = Paint.Style.FILL
            paint.color = COLOR_ROYAL_BLUE
            canvas.drawRect(margin.toFloat(), y, margin + cardWidth, y + 40f, paint)

            paint.color = COLOR_WHITE
            paint.textSize = if (isThermal) 10f else 13f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val customTitle = settings?.receiptHeaderTitle?.takeIf { it.isNotBlank() } ?: "OFFICIAL PREMIUM PAYMENT RECEIPT"
            canvas.drawText(customTitle, margin + 12f, y + 25f, paint)

            if (!isThermal) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("RCP #: $receiptNo", margin + cardWidth - 12f, y + 25f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
        }

        var lineY = y + (if (settings?.isReceiptHeaderEnabled != false) 60f else 25f)
        paint.color = COLOR_TEXT_DARK
        paint.textSize = if (isThermal) 8.5f else 10f

        if (isThermal) {
            paint.color = COLOR_ROYAL_BLUE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("RECEIPT #: $receiptNo", margin + 12f, lineY, paint)
            lineY += 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            drawDetailPair(canvas, paint, "Customer:", customerName, margin + 12f, lineY)
            lineY += 18f
            drawDetailPair(canvas, paint, "Policy No:", policyNo, margin + 12f, lineY)
            lineY += 18f
            drawDetailPair(canvas, paint, "Date:", payDate, margin + 12f, lineY)
            lineY += 18f
            drawDetailPair(canvas, paint, "Plan:", planName, margin + 12f, lineY)
            lineY += 22f
        } else {
            val col1 = margin + 16f
            val col2 = margin + (cardWidth / 2f) + 10f

            drawDetailPair(canvas, paint, "Customer Name:", customerName, col1, lineY)
            drawDetailPair(canvas, paint, "Payment Date:", payDate, col2, lineY)
            lineY += 22f

            drawDetailPair(canvas, paint, "Policy Number:", policyNo, col1, lineY)
            drawDetailPair(canvas, paint, "Payment Mode:", payMode, col2, lineY)
            lineY += 22f

            drawDetailPair(canvas, paint, "Plan Name:", planName, col1, lineY)
            drawDetailPair(canvas, paint, "Next Due Date:", policy?.dueDate ?: "As per schedule", col2, lineY)
            lineY += 26f
        }

        // Table Box for Financial Breakdown
        paint.color = COLOR_ALT_ROW
        paint.style = Paint.Style.FILL
        val tableHeight = if (isThermal) 80f else 95f
        val tableRect = RectF(margin + 12f, lineY, margin + cardWidth - 12f, lineY + tableHeight)
        canvas.drawRoundRect(tableRect, 6f, 6f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(tableRect, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        var tableY = lineY + 20f

        drawAmountRow(canvas, paint, "Base Premium", "₹${String.format("%,.2f", paidAmount)}", tableRect.left + 10f, tableRect.right - 10f, tableY, false)
        tableY += 18f
        drawAmountRow(canvas, paint, "Late Fee", "₹${String.format("%,.2f", lateFee)}", tableRect.left + 10f, tableRect.right - 10f, tableY, false)
        tableY += 20f

        paint.color = COLOR_BORDER
        paint.strokeWidth = 1f
        canvas.drawLine(tableRect.left + 8f, tableY - 10f, tableRect.right - 8f, tableY - 10f, paint)

        drawAmountRow(canvas, paint, "TOTAL PAID", "₹${String.format("%,.2f", totalPaid)}", tableRect.left + 10f, tableRect.right - 10f, tableY, true)

        lineY = tableRect.bottom + 18f

        // Status Line
        paint.color = COLOR_SUCCESS
        paint.textSize = if (isThermal) 9f else 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Status: PAYMENT CONFIRMED", margin + 16f, lineY, paint)
        lineY += 22f

        // Setting 4: Agent Digital Signature
        if (settings?.isAgentSignatureOnReceipt != false) {
            paint.color = COLOR_ROYAL_BLUE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val agentLabel = "Digitally Signed by ${settings?.agentName ?: data.agentProfile?.agentName ?: "Authorized Agent"}"
            canvas.drawText("✓ $agentLabel", margin + 16f, lineY, paint)
            lineY += 14f
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("Agency Code: ${settings?.agencyCode ?: data.agentProfile?.agencyCode ?: "LIC-089421"} • System Verified", margin + 16f, lineY, paint)
        }

        // Setting 5: QR Verification Code
        if (settings?.isQrCodeOnReceipt != false) {
            val qrContent = "VERIFIED RECEIPT: $receiptNo | POLICY $policyNo | AMT ₹$totalPaid"
            val qrBitmap = com.example.util.QrCodeGenerator.generateQrBitmap(qrContent, size = 90)
            if (qrBitmap != null) {
                val qrSize = if (isThermal) 50f else 60f
                val qrX = margin + cardWidth - qrSize - 16f
                val qrY = cardRect.bottom - qrSize - 16f
                canvas.drawBitmap(qrBitmap, null, RectF(qrX, qrY, qrX + qrSize, qrY + qrSize), null)
            }
        }

        return y + cardHeight + 20f
    }

    private fun drawDueOrOverdueReportContent(canvas: Canvas, data: PdfReportData): Float {
        var y = 140f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Summary Cards
        val cardWidth = 250f
        val cardHeight = 55f

        // Card 1: Count
        paint.color = COLOR_ALT_ROW
        paint.style = Paint.Style.FILL
        var rect = RectF(MARGIN.toFloat(), y, MARGIN + cardWidth, y + cardHeight)
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = COLOR_TEXT_MUTED
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL POLICIES LISTED", MARGIN + 12f, y + 20f, paint)

        paint.color = COLOR_ROYAL_BLUE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${data.policyList.size}", MARGIN + 12f, y + 44f, paint)

        // Card 2: Total Premium
        val card2X = MARGIN + 260f
        paint.color = COLOR_ALT_ROW
        paint.style = Paint.Style.FILL
        rect = RectF(card2X, y, card2X + cardWidth, y + cardHeight)
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = COLOR_TEXT_MUTED
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL PREMIUM OUTSTANDING", card2X + 12f, y + 20f, paint)

        val totalAmount = if (data.totalAmount > 0) data.totalAmount else data.policyList.sumOf { it.premiumAmount }
        paint.color = COLOR_DANGER
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("₹${String.format("%,.0f", totalAmount)}", card2X + 12f, y + 44f, paint)

        y += 75f

        // Table Header
        y = drawPolicyTableHeader(canvas, y)

        // Table Rows
        if (data.policyList.isEmpty()) {
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("No policies found for this report criteria.", MARGIN + 12f, y + 20f, paint)
            y += 30f
        } else {
            data.policyList.forEachIndexed { index, policy ->
                y = drawPolicyTableRow(canvas, policy, index + 1, y)
            }
        }

        return y
    }

    private fun drawMonthlyCollectionContent(canvas: Canvas, data: PdfReportData): Float {
        var y = 140f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Summary Card
        paint.color = COLOR_ROYAL_BLUE
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y + 60f)
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        paint.color = COLOR_WHITE
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MONTHLY COLLECTION SUMMARY (${data.filterPeriod})", MARGIN + 16f, y + 24f, paint)

        val totalColl = if (data.totalAmount > 0) data.totalAmount else data.paymentList.sumOf { it.paidAmount + it.lateFee }
        paint.textSize = 16f
        paint.color = COLOR_GOLD
        canvas.drawText("Total Collected: ₹${String.format("%,.2f", totalColl)}   |   Transactions: ${data.paymentList.size}", MARGIN + 16f, y + 48f, paint)

        y += 80f

        y = drawPaymentTableHeader(canvas, y)

        if (data.paymentList.isEmpty()) {
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("No collections logged for the selected period.", MARGIN + 12f, y + 20f, paint)
            y += 30f
        } else {
            data.paymentList.forEachIndexed { index, payment ->
                y = drawPaymentTableRow(canvas, payment, index + 1, y)
            }
        }

        return y
    }

    private fun drawPortfolioReportContent(canvas: Canvas, data: PdfReportData): Float {
        var y = 140f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Portfolio Stats Grid
        val boxWidth = 165f
        val boxHeight = 50f

        val totalCust = if (data.customerList.isNotEmpty()) data.customerList.size else data.policyList.map { it.customerId }.distinct().size
        val totalPolicies = data.policyList.size
        val totalSum = data.policyList.sumOf { it.sumAssured }
        val totalAnnPremium = data.policyList.sumOf { it.premiumAmount }

        drawStatBox(canvas, paint, "TOTAL CUSTOMERS", "$totalCust", MARGIN.toFloat(), y, boxWidth, boxHeight, COLOR_ROYAL_BLUE)
        drawStatBox(canvas, paint, "TOTAL POLICIES", "$totalPolicies", MARGIN + 180f, y, boxWidth, boxHeight, COLOR_ROYAL_BLUE)
        drawStatBox(canvas, paint, "TOTAL SUM ASSURED", "₹${String.format("%,.0f", totalSum)}", MARGIN + 360f, y, boxWidth, boxHeight, COLOR_SUCCESS)

        y += 65f

        // Table Header
        y = drawPolicyTableHeader(canvas, y)

        data.policyList.forEachIndexed { index, policy ->
            y = drawPolicyTableRow(canvas, policy, index + 1, y)
        }

        return y
    }

    private fun drawStatBox(
        canvas: Canvas,
        paint: Paint,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        valueColor: Int
    ) {
        paint.color = COLOR_ALT_ROW
        paint.style = Paint.Style.FILL
        val rect = RectF(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        paint.color = COLOR_TEXT_MUTED
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(label, x + 10f, y + 18f, paint)

        paint.color = valueColor
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value, x + 10f, y + 38f, paint)
    }

    private fun drawPolicyTableHeader(canvas: Canvas, startY: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val h = 24f

        paint.color = COLOR_ROYAL_BLUE
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN.toFloat(), startY, (PAGE_WIDTH - MARGIN).toFloat(), startY + h)
        canvas.drawRect(rect, paint)

        paint.color = COLOR_WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val rowY = startY + 16f
        canvas.drawText("#", MARGIN + 6f, rowY, paint)
        canvas.drawText("CUSTOMER NAME", MARGIN + 28f, rowY, paint)
        canvas.drawText("POLICY NO", MARGIN + 160f, rowY, paint)
        canvas.drawText("PLAN", MARGIN + 260f, rowY, paint)
        canvas.drawText("PREMIUM (₹)", MARGIN + 370f, rowY, paint)
        canvas.drawText("DUE DATE", MARGIN + 450f, rowY, paint)

        return startY + h
    }

    private fun drawPolicyTableRow(canvas: Canvas, policy: PolicyEntity, index: Int, startY: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val h = 20f

        // Row background alternating
        if (index % 2 == 0) {
            paint.color = COLOR_ALT_ROW
            paint.style = Paint.Style.FILL
            canvas.drawRect(MARGIN.toFloat(), startY, (PAGE_WIDTH - MARGIN).toFloat(), startY + h, paint)
        }

        paint.color = COLOR_TEXT_DARK
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val rowY = startY + 14f
        canvas.drawText("$index", MARGIN + 6f, rowY, paint)
        canvas.drawText(truncateText(policy.customerName, 22), MARGIN + 28f, rowY, paint)
        canvas.drawText(policy.policyNumber, MARGIN + 160f, rowY, paint)
        canvas.drawText(truncateText(policy.planName, 18), MARGIN + 260f, rowY, paint)
        canvas.drawText(String.format("%,.0f", policy.premiumAmount), MARGIN + 370f, rowY, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (policy.status == "Overdue") COLOR_DANGER else COLOR_ROYAL_BLUE
        canvas.drawText(policy.dueDate, MARGIN + 450f, rowY, paint)

        return startY + h
    }

    private fun drawPaymentTableHeader(canvas: Canvas, startY: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val h = 24f

        paint.color = COLOR_ROYAL_BLUE
        paint.style = Paint.Style.FILL
        val rect = RectF(MARGIN.toFloat(), startY, (PAGE_WIDTH - MARGIN).toFloat(), startY + h)
        canvas.drawRect(rect, paint)

        paint.color = COLOR_WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val rowY = startY + 16f
        canvas.drawText("#", MARGIN + 6f, rowY, paint)
        canvas.drawText("DATE", MARGIN + 28f, rowY, paint)
        canvas.drawText("RECEIPT NO", MARGIN + 100f, rowY, paint)
        canvas.drawText("CUSTOMER NAME", MARGIN + 200f, rowY, paint)
        canvas.drawText("POLICY NO", MARGIN + 340f, rowY, paint)
        canvas.drawText("MODE", MARGIN + 430f, rowY, paint)
        canvas.drawText("PAID (₹)", MARGIN + 480f, rowY, paint)

        return startY + h
    }

    private fun drawPaymentTableRow(canvas: Canvas, payment: PaymentEntity, index: Int, startY: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val h = 20f

        if (index % 2 == 0) {
            paint.color = COLOR_ALT_ROW
            paint.style = Paint.Style.FILL
            canvas.drawRect(MARGIN.toFloat(), startY, (PAGE_WIDTH - MARGIN).toFloat(), startY + h, paint)
        }

        paint.color = COLOR_TEXT_DARK
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val rowY = startY + 14f
        canvas.drawText("$index", MARGIN + 6f, rowY, paint)
        canvas.drawText(payment.paymentDate, MARGIN + 28f, rowY, paint)
        canvas.drawText(payment.receiptNumber, MARGIN + 100f, rowY, paint)
        canvas.drawText(truncateText(payment.customerName, 20), MARGIN + 200f, rowY, paint)
        canvas.drawText(payment.policyNumber, MARGIN + 340f, rowY, paint)
        canvas.drawText(payment.paymentMode, MARGIN + 430f, rowY, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = COLOR_SUCCESS
        val total = payment.paidAmount + payment.lateFee
        canvas.drawText(String.format("%,.0f", total), MARGIN + 480f, rowY, paint)

        return startY + h
    }

    private fun drawDetailPair(canvas: Canvas, paint: Paint, label: String, value: String, x: Float, y: Float) {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = COLOR_TEXT_MUTED
        canvas.drawText(label, x, y, paint)

        val labelWidth = paint.measureText(label)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = COLOR_TEXT_DARK
        canvas.drawText(" $value", x + labelWidth, y, paint)
    }

    private fun drawAmountRow(canvas: Canvas, paint: Paint, label: String, amount: String, leftX: Float, rightX: Float, y: Float, isBold: Boolean) {
        paint.textSize = if (isBold) 11f else 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
        paint.color = if (isBold) COLOR_ROYAL_BLUE else COLOR_TEXT_DARK

        canvas.drawText(label, leftX, y, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(amount, rightX, y, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) text.substring(0, maxLength - 2) + ".." else text
    }

    /**
     * Shares generated PDF file via Android Share Intent.
     */
    fun sharePdf(context: Context, pdfFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, pdfFile.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing PDF file: ${e.localizedMessage}", e)
            Toast.makeText(context, "Failed to share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches PrintManager or PDF View Intent for printing.
     */
    fun printPdf(context: Context, pdfFile: File) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val printAdapter: PrintDocumentAdapter = object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: android.os.Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val info = android.print.PrintDocumentInfo.Builder(pdfFile.name)
                            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build()
                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out android.print.PageRange>?,
                        destination: android.os.ParcelFileDescriptor?,
                        cancellationSignal: android.os.CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        try {
                            pdfFile.inputStream().use { input ->
                                FileOutputStream(destination?.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.localizedMessage)
                        }
                    }
                }

                printManager.print(pdfFile.name, printAdapter, PrintAttributes.Builder().build())
            } else {
                openPdf(context, pdfFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error printing PDF file: ${e.localizedMessage}", e)
            openPdf(context, pdfFile)
        }
    }

    /**
     * Opens PDF with installed PDF viewer.
     */
    fun openPdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "No PDF viewer app found: ${e.localizedMessage}", e)
            Toast.makeText(context, "No application found to view PDF", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates a complete Payment History PDF for a single customer & policy.
     */
    suspend fun generateCustomerPaymentHistoryPdf(
        context: Context,
        summary: PolicyPaymentSummary,
        agentProfile: AgentProfileEntity?
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 842 // A4 Landscape width in points
            val pageHeight = 595 // A4 Landscape height in points
            val margin = 36f

            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Header Banner Drawing Function
            fun drawHeaderBanner(c: Canvas, pNum: Int): Float {
                // Royal Blue Header
                paint.color = COLOR_ROYAL_BLUE
                paint.style = Paint.Style.FILL
                c.drawRect(0f, 0f, pageWidth.toFloat(), 75f, paint)

                // Gold Strip
                paint.color = COLOR_GOLD
                c.drawRect(0f, 75f, pageWidth.toFloat(), 78f, paint)

                // Title
                paint.color = COLOR_WHITE
                paint.textSize = 16f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                c.drawText("LIC PREMIUM REMINDER PRO", margin, 30f, paint)

                paint.textSize = 12f
                paint.color = COLOR_GOLD
                c.drawText("LIC PREMIUM PAYMENT HISTORY", margin, 52f, paint)

                // Agent Info on Right
                val agentName = agentProfile?.agentName ?: "LIC Agent"
                val agencyCode = agentProfile?.agencyCode ?: "LIC-089421"
                val branch = "${agentProfile?.branchName ?: "Branch"} (${agentProfile?.branchCode ?: "08B"})"

                paint.textSize = 8.5f
                paint.color = COLOR_WHITE
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.RIGHT

                val rightX = pageWidth - margin
                c.drawText("Issued By: $agentName", rightX, 24f, paint)
                c.drawText("Agency Code: $agencyCode", rightX, 38f, paint)
                c.drawText("Branch: $branch", rightX, 52f, paint)
                val mob = agentProfile?.mobile ?: ""
                if (mob.isNotBlank()) {
                    c.drawText("Ph: $mob", rightX, 66f, paint)
                }

                paint.textAlign = Paint.Align.LEFT
                return 90f
            }

            fun drawFooterLine(c: Canvas, pNum: Int) {
                val footerY = pageHeight - 25f
                paint.color = COLOR_BORDER
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                c.drawLine(margin, footerY - 10f, pageWidth - margin, footerY - 10f, paint)

                paint.style = Paint.Style.FILL
                paint.textSize = 8f
                paint.color = COLOR_TEXT_MUTED
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                c.drawText("Generated by LIC Premium Reminder Pro", margin, footerY, paint)

                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val dateStr = "Generated Date: ${sdf.format(Date())} | Page $pNum"
                paint.textAlign = Paint.Align.RIGHT
                c.drawText(dateStr, pageWidth - margin, footerY, paint)
                paint.textAlign = Paint.Align.LEFT
            }

            var y = drawHeaderBanner(canvas, pageNum)

            // Draw Customer & Policy Info Box
            paint.color = COLOR_ALT_ROW
            paint.style = Paint.Style.FILL
            val infoRect = RectF(margin, y, pageWidth - margin, y + 65f)
            canvas.drawRoundRect(infoRect, 6f, 6f, paint)

            paint.color = COLOR_BORDER
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(infoRect, 6f, 6f, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 9.5f
            paint.color = COLOR_TEXT_DARK

            val planNameStr = summary.policy?.planName ?: "LIC Policy"
            val mobileStr = summary.customerMobile.ifEmpty { summary.customer?.mobile ?: summary.customer?.whatsapp ?: "N/A" }

            val col1 = margin + 14f
            val col2 = margin + 270f

            drawDetailPair(canvas, paint, "Customer Name:", summary.customerName, col1, y + 22f)
            drawDetailPair(canvas, paint, "Mobile:", mobileStr, col2, y + 22f)
            drawDetailPair(canvas, paint, "Policy No:", summary.policyNumber, col1, y + 46f)
            drawDetailPair(canvas, paint, "Plan Name:", planNameStr, col2, y + 46f)

            y += 75f

            // Draw Summary Box
            paint.color = COLOR_ROYAL_BLUE
            paint.style = Paint.Style.FILL
            val summaryRect = RectF(margin, y, pageWidth - margin, y + 45f)
            canvas.drawRoundRect(summaryRect, 6f, 6f, paint)

            paint.color = COLOR_WHITE
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val boxColWidth = (pageWidth - margin * 2) / 4f
            val boxY = y + 28f

            fun drawBoxStat(label: String, valStr: String, colIdx: Int, valueColor: Int = COLOR_WHITE) {
                val startX = margin + (colIdx * boxColWidth) + 12f
                paint.color = Color.parseColor("#94A3B8")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(label, startX, boxY - 10f, paint)

                paint.color = valueColor
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(valStr, startX, boxY + 6f, paint)
            }

            val statusValColor = when (summary.status.label) {
                "Paid" -> COLOR_GOLD
                "Partial" -> Color.parseColor("#F59E0B")
                "Overpaid" -> COLOR_SUCCESS
                else -> COLOR_WHITE
            }

            drawBoxStat("TOTAL DUE", "₹${String.format("%,.0f", summary.totalDue)}", 0)
            drawBoxStat("TOTAL PAID", "₹${String.format("%,.0f", summary.totalPaid)}", 1, COLOR_GOLD)
            drawBoxStat("TOTAL BALANCE", "₹${String.format("%,.0f", summary.balance)}", 2, if (summary.balance > 0) Color.parseColor("#F87171") else COLOR_WHITE)
            drawBoxStat("ADVANCE / EXCESS", "₹${String.format("%,.0f", summary.advance)}", 3, Color.parseColor("#A855F7"))
            drawBoxStat("STATUS", summary.status.label.uppercase(Locale.getDefault()), 4, statusValColor)

            y += 58f

            // Table Header setup - 8 columns
            val colWidths = floatArrayOf(35f, 75f, 85f, 85f, 70f, 85f, 60f, 65f)
            val colX = FloatArray(8)
            var curX = margin
            for (i in 0..7) {
                colX[i] = curX
                curX += colWidths[i]
            }

            fun drawTableHeader(c: Canvas, startY: Float): Float {
                paint.color = COLOR_ROYAL_BLUE
                paint.style = Paint.Style.FILL
                val rect = RectF(margin, startY, pageWidth - margin, startY + 22f)
                c.drawRect(rect, paint)

                paint.color = COLOR_WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                val rowY = startY + 15f
                c.drawText("S.No", colX[0] + 4f, rowY, paint)
                c.drawText("Date", colX[1] + 4f, rowY, paint)
                c.drawText("Due Amt", colX[2] + 4f, rowY, paint)
                c.drawText("Paid Amt", colX[3] + 4f, rowY, paint)
                c.drawText("Balance", colX[4] + 4f, rowY, paint)
                c.drawText("Advance/Excess", colX[5] + 4f, rowY, paint)
                c.drawText("Mode", colX[6] + 4f, rowY, paint)
                c.drawText("Status", colX[7] + 4f, rowY, paint)

                return startY + 22f
            }

            y = drawTableHeader(canvas, y)

            // Draw Payments
            val paymentsSorted = summary.payments.sortedBy { it.paymentDate }
            var cumulativePaid = 0.0

            if (paymentsSorted.isEmpty()) {
                paint.color = COLOR_TEXT_MUTED
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No payments recorded yet for this policy.", margin + 12f, y + 20f, paint)
                y += 35f
            } else {
                paymentsSorted.forEachIndexed { index, payment ->
                    // Multi-page page break check
                    if (y > pageHeight - 90f) {
                        drawFooterLine(canvas, pageNum)
                        pdfDocument.finishPage(page)

                        pageNum++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas

                        y = drawHeaderBanner(canvas, pageNum)
                        y = drawTableHeader(canvas, y)
                    }

                    cumulativePaid += payment.paidAmount
                    val rowCalc = calculatePaymentStatus(summary.totalDue, cumulativePaid)

                    val rowH = 20f
                    if (index % 2 == 1) {
                        paint.color = COLOR_ALT_ROW
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(margin, y, pageWidth - margin, y + rowH, paint)
                    }

                    paint.color = COLOR_TEXT_DARK
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                    val rowY = y + 14f
                    canvas.drawText("${index + 1}", colX[0] + 4f, rowY, paint)
                    canvas.drawText(payment.paymentDate, colX[1] + 4f, rowY, paint)
                    canvas.drawText("₹${String.format("%,.0f", summary.totalDue)}", colX[2] + 4f, rowY, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.color = COLOR_SUCCESS
                    canvas.drawText("₹${String.format("%,.0f", payment.paidAmount)}", colX[3] + 4f, rowY, paint)

                    paint.color = if (rowCalc.balance > 0) COLOR_DANGER else COLOR_TEXT_DARK
                    canvas.drawText("₹${String.format("%,.0f", rowCalc.balance)}", colX[4] + 4f, rowY, paint)

                    paint.color = if (rowCalc.advance > 0) Color.parseColor("#A855F7") else COLOR_TEXT_DARK
                    canvas.drawText("₹${String.format("%,.0f", rowCalc.advance)}", colX[5] + 4f, rowY, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.color = COLOR_TEXT_DARK
                    canvas.drawText(payment.paymentMode, colX[6] + 4f, rowY, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.color = when (rowCalc.status.label) {
                        "Paid" -> COLOR_SUCCESS
                        "Partial" -> Color.parseColor("#D97706")
                        "Overpaid" -> COLOR_SUCCESS
                        else -> COLOR_DANGER
                    }
                    canvas.drawText(rowCalc.status.label, colX[7] + 4f, rowY, paint)

                    y += rowH
                }
            }

            y += 15f

            // Bottom Summary Box
            if (y > pageHeight - 110f) {
                drawFooterLine(canvas, pageNum)
                pdfDocument.finishPage(page)

                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                y = drawHeaderBanner(canvas, pageNum)
            }

            paint.color = COLOR_ALT_ROW
            paint.style = Paint.Style.FILL
            val bottomRect = RectF(margin, y, pageWidth - margin, y + 45f)
            canvas.drawRoundRect(bottomRect, 6f, 6f, paint)

            paint.color = COLOR_BORDER
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(bottomRect, 6f, 6f, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 9.5f
            paint.color = COLOR_TEXT_DARK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val bLineY = y + 26f
            val bCol1 = margin + 16f
            val bCol2 = margin + 220f
            val bCol3 = margin + 420f
            val bCol4 = margin + 620f

            canvas.drawText("Total Number of Payments: ${paymentsSorted.size}", bCol1, bLineY, paint)
            canvas.drawText("Total Paid: ₹${String.format("%,.0f", summary.totalPaid)}", bCol2, bLineY, paint)
            canvas.drawText("Final Balance: ₹${String.format("%,.0f", summary.balance)}", bCol3, bLineY, paint)
            canvas.drawText("Final Status: ${summary.status.label}", bCol4, bLineY, paint)

            drawFooterLine(canvas, pageNum)
            pdfDocument.finishPage(page)

            val reportsDir = getReportsDirectory(context)
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanCustName = summary.customerName.replace(Regex("[^a-zA-Z0-9]"), "_")
            val pdfFile = File(reportsDir, "LIC_Payment_History_${cleanCustName}_$timeStamp.pdf")

            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            Log.i(TAG, "Successfully generated Payment History PDF: ${pdfFile.absolutePath}")
            Result.success(pdfFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Payment History PDF: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}
