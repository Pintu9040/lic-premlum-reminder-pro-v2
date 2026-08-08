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
    COMPLETE_PORTFOLIO("Complete Portfolio Report", "Complete_Portfolio")
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
            val pdfDocument = PdfDocument()
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            var yPos = drawHeader(canvas, data, pageNum)

            when (data.reportType) {
                ReportType.CUSTOMER_PROFILE -> {
                    yPos = drawCustomerProfileContent(canvas, data)
                }
                ReportType.POLICY_DETAILS -> {
                    yPos = drawPolicyDetailsContent(canvas, data)
                }
                ReportType.PREMIUM_RECEIPT, ReportType.PAYMENT_RECEIPT -> {
                    yPos = drawReceiptContent(canvas, data)
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
                ReportType.COMPLETE_PORTFOLIO -> {
                    yPos = drawPortfolioReportContent(canvas, data)
                }
            }

            drawFooter(canvas, data, pageNum)
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

            Log.i(TAG, "Successfully generated PDF report: ${pdfFile.absolutePath}")
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

    private fun drawHeader(canvas: Canvas, data: PdfReportData, pageNum: Int): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Banner Background
        paint.color = COLOR_ROYAL_BLUE
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 110f, paint)

        // Gold Decorative Strip
        paint.color = COLOR_GOLD
        canvas.drawRect(0f, 110f, PAGE_WIDTH.toFloat(), 114f, paint)

        // Header Title
        paint.color = COLOR_WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("LIC PREMIUM REMINDER PRO", MARGIN.toFloat(), 35f, paint)

        // Report Title
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = COLOR_GOLD
        canvas.drawText(data.reportType.title.uppercase(Locale.getDefault()), MARGIN.toFloat(), 55f, paint)

        // Agent Profile Info in Header (Right aligned or formatted)
        val agent = data.agentProfile
        val agentName = agent?.agentName ?: "LIC Advisor"
        val agencyCode = agent?.agencyCode ?: "LIC-089421"
        val branchInfo = "${agent?.branchName ?: "Branch"} (${agent?.branchCode ?: "08B"})"
        val contactInfo = "Ph: ${agent?.mobile ?: ""} | ${agent?.email ?: ""}"

        paint.textSize = 9f
        paint.color = COLOR_WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.RIGHT

        val rightX = (PAGE_WIDTH - MARGIN).toFloat()
        canvas.drawText(agentName, rightX, 30f, paint)
        canvas.drawText("Agency Code: $agencyCode", rightX, 45f, paint)
        canvas.drawText(branchInfo, rightX, 60f, paint)
        canvas.drawText(contactInfo, rightX, 75f, paint)

        paint.textAlign = Paint.Align.LEFT

        // Generated Timestamp Line
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val generatedText = "Report Generated: ${sdf.format(Date())}"
        paint.textSize = 8f
        paint.color = COLOR_WHITE
        canvas.drawText(generatedText, MARGIN.toFloat(), 98f, paint)

        return 130f
    }

    private fun drawFooter(canvas: Canvas, data: PdfReportData, pageNum: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val footerY = (PAGE_HEIGHT - 35).toFloat()

        // Divider
        paint.color = COLOR_BORDER
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(MARGIN.toFloat(), footerY - 10, (PAGE_WIDTH - MARGIN).toFloat(), footerY - 10, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 8f
        paint.color = COLOR_TEXT_MUTED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        canvas.drawText("LIC Premium Reminder Pro — Official Agent Report", MARGIN.toFloat(), footerY, paint)

        val agentName = data.agentProfile?.agentName ?: "LIC Agent"
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Authorized Agent Signature: $agentName | Page $pageNum", (PAGE_WIDTH - MARGIN).toFloat(), footerY, paint)
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

    private fun drawReceiptContent(canvas: Canvas, data: PdfReportData): Float {
        var y = 140f
        val payment = data.payment
        val policy = data.policy
        val customer = data.customer
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val receiptNo = payment?.receiptNumber ?: "RCP-${System.currentTimeMillis() % 1000000}"
        val paidAmount = payment?.paidAmount ?: (policy?.premiumAmount ?: 0.0)
        val lateFee = payment?.lateFee ?: 0.0
        val totalPaid = paidAmount + lateFee
        val customerName = payment?.customerName ?: customer?.name ?: policy?.customerName ?: "Valued Customer"
        val policyNo = payment?.policyNumber ?: policy?.policyNumber ?: "N/A"
        val planName = policy?.planName ?: "LIC Policy"
        val payDate = payment?.paymentDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val payMode = payment?.paymentMode ?: "UPI / Online"

        // Outer Receipt Card
        val cardWidth = (PAGE_WIDTH - (MARGIN * 2)).toFloat()
        val cardHeight = 320f
        paint.color = COLOR_WHITE
        paint.style = Paint.Style.FILL
        val cardRect = RectF(MARGIN.toFloat(), y, MARGIN + cardWidth, y + cardHeight)
        canvas.drawRoundRect(cardRect, 10f, 10f, paint)

        // Outer border
        paint.color = COLOR_ROYAL_BLUE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(cardRect, 10f, 10f, paint)

        // Header band of receipt
        paint.style = Paint.Style.FILL
        paint.color = COLOR_ROYAL_BLUE
        canvas.drawRect(MARGIN.toFloat(), y, MARGIN + cardWidth, y + 40f, paint)

        paint.color = COLOR_WHITE
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("OFFICIAL PREMIUM PAYMENT RECEIPT", MARGIN + 16f, y + 25f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RECEIPT #: $receiptNo", MARGIN + cardWidth - 16f, y + 25f, paint)
        paint.textAlign = Paint.Align.LEFT

        var lineY = y + 65f
        paint.color = COLOR_TEXT_DARK
        paint.textSize = 10f

        val col1 = MARGIN + 20f
        val col2 = MARGIN + 280f

        drawDetailPair(canvas, paint, "Customer Name:", customerName, col1, lineY)
        drawDetailPair(canvas, paint, "Payment Date:", payDate, col2, lineY)
        lineY += 22f

        drawDetailPair(canvas, paint, "Policy Number:", policyNo, col1, lineY)
        drawDetailPair(canvas, paint, "Payment Mode:", payMode, col2, lineY)
        lineY += 22f

        drawDetailPair(canvas, paint, "Plan Name:", planName, col1, lineY)
        drawDetailPair(canvas, paint, "Next Due Date:", policy?.dueDate ?: "As per schedule", col2, lineY)
        lineY += 26f

        // Table Box for Financial Breakdown
        paint.color = COLOR_ALT_ROW
        paint.style = Paint.Style.FILL
        val tableRect = RectF(MARGIN + 16f, lineY, MARGIN + cardWidth - 16f, lineY + 110f)
        canvas.drawRoundRect(tableRect, 6f, 6f, paint)

        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(tableRect, 6f, 6f, paint)

        paint.style = Paint.Style.FILL
        var tableY = lineY + 22f

        drawAmountRow(canvas, paint, "Base Premium Amount", "₹${String.format("%,.2f", paidAmount)}", tableRect.left + 12f, tableRect.right - 12f, tableY, false)
        tableY += 20f
        drawAmountRow(canvas, paint, "Late Fee / Interest", "₹${String.format("%,.2f", lateFee)}", tableRect.left + 12f, tableRect.right - 12f, tableY, false)
        tableY += 20f
        drawAmountRow(canvas, paint, "Outstanding Balance", "₹0.00", tableRect.left + 12f, tableRect.right - 12f, tableY, false)
        tableY += 24f

        // Total Row Divider
        paint.color = COLOR_BORDER
        paint.strokeWidth = 1f
        canvas.drawLine(tableRect.left + 10f, tableY - 12f, tableRect.right - 10f, tableY - 12f, paint)

        drawAmountRow(canvas, paint, "TOTAL PAID AMOUNT", "₹${String.format("%,.2f", totalPaid)}", tableRect.left + 12f, tableRect.right - 12f, tableY, true)

        lineY = y + cardHeight - 30f
        paint.color = COLOR_SUCCESS
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Status: PAYMENT RECEIVED & CONFIRMED", MARGIN + 20f, lineY, paint)

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
}
