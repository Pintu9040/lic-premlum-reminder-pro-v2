package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CustomerEntity::class,
        PolicyEntity::class,
        PaymentEntity::class,
        DocumentEntity::class,
        AgentProfileEntity::class,
        FollowUpEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun policyDao(): PolicyDao
    abstract fun paymentDao(): PaymentDao
    abstract fun documentDao(): DocumentDao
    abstract fun agentDao(): AgentDao
    abstract fun followUpDao(): FollowUpDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lic_reminder_pro_db"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database)
                    }
                }
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            val agentDao = db.agentDao()
            val customerDao = db.customerDao()
            val policyDao = db.policyDao()
            val paymentDao = db.paymentDao()
            val documentDao = db.documentDao()
            val followUpDao = db.followUpDao()

            // Agent profile
            agentDao.saveAgentProfile(
                AgentProfileEntity(
                    id = 1,
                    agentName = "Pintu Ojha",
                    agencyCode = "LIC-AGENT-89421",
                    branchCode = "08B",
                    branchName = "Bhubaneswar Branch",
                    email = "pintu.lic.agent@gmail.com",
                    mobile = "+91 98765 43210",
                    isDarkMode = false
                )
            )

            // Customers
            val c1Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Rajesh Sharma",
                    mobile = "+91 9876543210",
                    whatsapp = "+919876543210",
                    email = "rajesh.sharma@example.com",
                    address = "12, Park Street, Civil Lines, Jaipur",
                    dob = "1985-08-15",
                    anniversary = "2010-11-20",
                    aadhaar = "4589 1234 5678",
                    pan = "ABCPS1234K",
                    occupation = "Senior Business Manager",
                    notes = "Prefers morning WhatsApp reminders. Long time client."
                )
            )

            val c2Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Priya Verma",
                    mobile = "+91 9123456789",
                    whatsapp = "+919123456789",
                    email = "priya.verma@example.com",
                    address = "B-402, Sunshine Heights, Mumbai",
                    dob = "1992-03-24",
                    anniversary = "2018-05-12",
                    aadhaar = "9876 5432 1012",
                    pan = "XYZPV9876M",
                    occupation = "Software Architect",
                    notes = "Key client for Money Back & SIIP plans."
                )
            )

            val c3Id = customerDao.insertCustomer(
                CustomerEntity(
                    name = "Amitabh Gupta",
                    mobile = "+91 9988776655",
                    whatsapp = "+919988776655",
                    email = "amit.gupta@example.com",
                    address = "78, Tech Park Road, Bengaluru",
                    dob = "1978-12-05",
                    anniversary = "2004-02-14",
                    aadhaar = "6543 2109 8765",
                    pan = "DEFG1234P",
                    occupation = "Managing Director",
                    notes = "Executive client with high sum assured plans."
                )
            )

            // Today's date string helper for immediate active due dates
            val todayStr = java.time.LocalDate.now().toString()
            val upcomingStr = java.time.LocalDate.now().plusDays(5).toString()
            val overdueStr = java.time.LocalDate.now().minusDays(10).toString()

            // Policies
            val p1Id = policyDao.insertPolicy(
                PolicyEntity(
                    policyNumber = "895412036",
                    customerId = c1Id,
                    customerName = "Rajesh Sharma",
                    planName = "Jeevan Labh (936)",
                    premiumAmount = 12500.0,
                    sumAssured = 500000.0,
                    premiumMode = "Half-Yearly",
                    dueDate = todayStr,
                    maturityDate = "2041-08-15",
                    status = "Active",
                    nominee = "Sunita Sharma (Wife)"
                )
            )

            val p2Id = policyDao.insertPolicy(
                PolicyEntity(
                    policyNumber = "774125896",
                    customerId = c2Id,
                    customerName = "Priya Verma",
                    planName = "Jeevan Umang (945)",
                    premiumAmount = 8400.0,
                    sumAssured = 300000.0,
                    premiumMode = "Quarterly",
                    dueDate = upcomingStr,
                    maturityDate = "2048-03-24",
                    status = "Active",
                    nominee = "Rohan Verma (Son)"
                )
            )

            val p3Id = policyDao.insertPolicy(
                PolicyEntity(
                    policyNumber = "663214789",
                    customerId = c3Id,
                    customerName = "Amitabh Gupta",
                    planName = "Endowment Plan (914)",
                    premiumAmount = 24000.0,
                    sumAssured = 1000000.0,
                    premiumMode = "Yearly",
                    dueDate = overdueStr,
                    maturityDate = "2036-12-05",
                    status = "Lapsed",
                    nominee = "Kavita Gupta (Wife)"
                )
            )

            // Payments
            paymentDao.insertPayment(
                PaymentEntity(
                    policyId = p1Id,
                    policyNumber = "895412036",
                    customerId = c1Id,
                    customerName = "Rajesh Sharma",
                    paidAmount = 12500.0,
                    paymentDate = java.time.LocalDate.now().minusMonths(6).toString(),
                    paymentMode = "UPI",
                    receiptNumber = "REC-2026-8941",
                    notes = "Previous half-yearly instalment clear."
                )
            )

            paymentDao.insertPayment(
                PaymentEntity(
                    policyId = p2Id,
                    policyNumber = "774125896",
                    customerId = c2Id,
                    customerName = "Priya Verma",
                    paidAmount = 8400.0,
                    paymentDate = java.time.LocalDate.now().minusMonths(3).toString(),
                    paymentMode = "Cheque",
                    receiptNumber = "REC-2026-3392",
                    notes = "Chq #44012 Cleared."
                )
            )

            // Sample Documents
            documentDao.insertDocument(
                DocumentEntity(
                    customerId = c1Id,
                    customerName = "Rajesh Sharma",
                    policyId = p1Id,
                    docType = "Aadhaar Card",
                    title = "Rajesh_Aadhaar_Card.pdf",
                    fileUri = "content://vault/Rajesh_Aadhaar_Card.pdf",
                    fileSize = "1.4 MB",
                    uploadDate = "2026-01-10"
                )
            )
            documentDao.insertDocument(
                DocumentEntity(
                    customerId = c1Id,
                    customerName = "Rajesh Sharma",
                    policyId = p1Id,
                    docType = "Policy Bond",
                    title = "JeevanLabh_Bond_895412036.pdf",
                    fileUri = "content://vault/JeevanLabh_Bond_895412036.pdf",
                    fileSize = "3.2 MB",
                    uploadDate = "2026-01-12"
                )
            )
            documentDao.insertDocument(
                DocumentEntity(
                    customerId = c2Id,
                    customerName = "Priya Verma",
                    policyId = p2Id,
                    docType = "PAN Card",
                    title = "Priya_PAN_Scan.jpg",
                    fileUri = "content://vault/Priya_PAN_Scan.jpg",
                    fileSize = "850 KB",
                    uploadDate = "2026-02-01"
                )
            )

            // Sample Follow-ups
            followUpDao.insertFollowUp(
                FollowUpEntity(
                    customerId = c1Id,
                    customerName = "Rajesh Sharma",
                    customerMobile = "+91 9876543210",
                    date = todayStr,
                    time = "10:30 AM",
                    notes = "Call regarding Jeevan Labh premium collection.",
                    status = "Pending"
                )
            )
            followUpDao.insertFollowUp(
                FollowUpEntity(
                    customerId = c2Id,
                    customerName = "Priya Verma",
                    customerMobile = "+91 9123456789",
                    date = java.time.LocalDate.now().plusDays(1).toString(),
                    time = "02:00 PM",
                    notes = "Discuss Jeevan Umang maturity benefits and new investment options.",
                    status = "Pending"
                )
            )
            followUpDao.insertFollowUp(
                FollowUpEntity(
                    customerId = c3Id,
                    customerName = "Amitabh Gupta",
                    customerMobile = "+91 9988776655",
                    date = java.time.LocalDate.now().minusDays(1).toString(),
                    time = "04:30 PM",
                    notes = "Provide revival quote for lapsed Endowment plan.",
                    status = "Completed"
                )
            )
        }
    }
}
