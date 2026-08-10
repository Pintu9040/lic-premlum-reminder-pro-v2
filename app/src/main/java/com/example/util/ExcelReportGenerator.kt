package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelReportGenerator {
    private const val TAG = "ExcelReportGenerator"

    fun generateExcelReport(
        context: Context,
        filterPeriod: String,
        policies: List<PolicyEntity>,
        payments: List<PaymentEntity>,
        customers: List<CustomerEntity>
    ): Result<File> {
        return try {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val reportsDir = File(baseDir, "LIC Premium Reminder Pro/Reports")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(reportsDir, "LIC_Report_$timestamp.csv")

            val customerMap = customers.associateBy { it.id }
            val policyMap = policies.associateBy { it.id }

            FileWriter(file).use { writer ->
                // Title Header
                writer.append("LIC PREMIUM REMINDER PRO - FINANCIAL REPORT ($filterPeriod)\n")
                writer.append("Generated On: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}\n\n")

                // SECTION 1: PAYMENTS & COLLECTIONS SUMMARY
                writer.append("PAYMENT COLLECTIONS REPORT\n")
                writer.append("S.No,Customer Name,Mobile,Policy Number,Plan Name,Premium (Rs),Paid Amount (Rs),Outstanding (Rs),Payment Date,Payment Mode,Receipt No,Status\n")

                var totalPaid = 0.0
                var serial = 1

                payments.forEach { pay ->
                    val policy = policyMap[pay.policyId]
                    val customer = customerMap[pay.customerId] ?: customers.find { it.name.equals(pay.customerName, ignoreCase = true) }
                    val premium = policy?.premiumAmount ?: pay.paidAmount
                    val outstanding = kotlin.math.max(0.0, premium - pay.paidAmount)
                    totalPaid += pay.paidAmount

                    writer.append("$serial,")
                    writer.append("\"${pay.customerName.replace("\"", "\"\"")}\",")
                    writer.append("\"${customer?.mobile ?: ""}\",")
                    writer.append("\"${pay.policyNumber}\",")
                    writer.append("\"${policy?.planName ?: "N/A"}\",")
                    writer.append("$premium,")
                    writer.append("${pay.paidAmount},")
                    writer.append("$outstanding,")
                    writer.append("\"${pay.paymentDate}\",")
                    writer.append("\"${pay.paymentMode}\",")
                    writer.append("\"${pay.receiptNumber}\",")
                    writer.append("\"${if (outstanding <= 0) "Paid" else "Partial"}\"\n")
                    serial++
                }

                writer.append("TOTAL,,,,, ,$totalPaid,,,,\n\n")

                // SECTION 2: POLICY PORTFOLIO STATUS
                writer.append("POLICY PORTFOLIO BREAKDOWN\n")
                writer.append("S.No,Customer Name,Policy Number,Plan Name,Mode,Premium Amount (Rs),Due Date,Status\n")

                var polSerial = 1
                var totalPremiumSum = 0.0
                policies.forEach { pol ->
                    totalPremiumSum += pol.premiumAmount
                    writer.append("$polSerial,")
                    writer.append("\"${pol.customerName.replace("\"", "\"\"")}\",")
                    writer.append("\"${pol.policyNumber}\",")
                    writer.append("\"${pol.planName}\",")
                    writer.append("\"${pol.premiumMode}\",")
                    writer.append("${pol.premiumAmount},")
                    writer.append("\"${pol.dueDate}\",")
                    writer.append("\"${pol.status}\"\n")
                    polSerial++
                }

                writer.append("TOTAL PORTFOLIO PREMIUM,,,,, $totalPremiumSum,,\n")
            }

            Log.i(TAG, "Generated Excel CSV report: ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Excel report", e)
            Result.failure(e)
        }
    }

    fun openExcelFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback share intent if no viewer app is installed
            shareExcelFile(context, file)
        }
    }

    fun shareExcelFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LIC Financial Report Spreadsheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share Report Spreadsheet")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing Excel file", e)
        }
    }
}
