package com.example.data.repository

import android.util.Log
import com.example.data.local.*
import com.example.data.remote.FirebaseSyncManager
import com.example.util.PaymentAllocationEngine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

data class DashboardStats(
    val totalCustomers: Int = 0,
    val totalPolicies: Int = 0,
    val dueTodayCount: Int = 0,
    val dueTodayAmount: Double = 0.0,
    val dueThisMonthCount: Int = 0,
    val dueThisMonthAmount: Double = 0.0,
    val premiumCollectedTotal: Double = 0.0,
    val outstandingAmount: Double = 0.0
)

class LicRepository(
    private val db: AppDatabase,
    private val syncManager: FirebaseSyncManager
) {
    private val customerDao = db.customerDao()
    private val policyDao = db.policyDao()
    private val paymentDao = db.paymentDao()
    private val documentDao = db.documentDao()
    private val agentDao = db.agentDao()

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun getCurrentUid(): String {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank()) {
                uid
            } else {
                "default_agent"
            }
        } catch (e: Throwable) {
            "default_agent"
        }
    }

    private fun getFirestore(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    // --- Customers Firestore Flow & Operations ---
    val allCustomers: Flow<List<CustomerEntity>> = channelFlow {
        val roomJob = scope.launch {
            customerDao.getAllCustomers().collect { list ->
                send(list)
            }
        }

        val firestore = getFirestore()
        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        if (firestore != null) {
            scope.launch {
                try {
                    val uid = syncManager.getOrEnsureUid()
                    Log.d("FirestoreSync", "Listening for Customers in Firestore at path: agents/$uid/customers")
                    listenerRegistration = firestore.collection("agents").document(uid)
                        .collection("customers")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.w("FirestoreSync", "Firestore customer listener notice for UID: $uid (${error.localizedMessage}). Local database active.")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val list = snapshot.documents.mapNotNull { doc ->
                                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                                    if (id == 0L) null else CustomerEntity(
                                        id = id,
                                        name = doc.getString("name") ?: "",
                                        mobile = doc.getString("mobile") ?: "",
                                        whatsapp = doc.getString("whatsapp") ?: "",
                                        email = doc.getString("email") ?: "",
                                        address = doc.getString("address") ?: "",
                                        dob = doc.getString("dob") ?: "",
                                        anniversary = doc.getString("anniversary") ?: "",
                                        aadhaar = doc.getString("aadhaar") ?: "",
                                        pan = doc.getString("pan") ?: "",
                                        occupation = doc.getString("occupation") ?: "",
                                        notes = doc.getString("notes") ?: "",
                                        photoUri = doc.getString("photoUri")?.ifBlank { null },
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    )
                                }
                                if (list.isNotEmpty()) {
                                    scope.launch {
                                        list.forEach { customerDao.insertCustomer(it) }
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    Log.w("FirestoreSync", "Error attaching customer listener: ${e.localizedMessage}")
                }
            }
        }

        awaitClose {
            roomJob.cancel()
            listenerRegistration?.remove()
        }
    }

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
        return if (query.isBlank()) allCustomers else customerDao.searchCustomers(query)
    }

    suspend fun getCustomerById(id: Long): CustomerEntity? {
        val firestore = getFirestore()
        if (firestore != null) {
            try {
                val uid = syncManager.getOrEnsureUid()
                val doc = firestore.collection("agents").document(uid)
                    .collection("customers").document(id.toString()).get().await()
                if (doc.exists()) {
                    return CustomerEntity(
                        id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: id,
                        name = doc.getString("name") ?: "",
                        mobile = doc.getString("mobile") ?: "",
                        whatsapp = doc.getString("whatsapp") ?: "",
                        email = doc.getString("email") ?: "",
                        address = doc.getString("address") ?: "",
                        dob = doc.getString("dob") ?: "",
                        anniversary = doc.getString("anniversary") ?: "",
                        aadhaar = doc.getString("aadhaar") ?: "",
                        pan = doc.getString("pan") ?: "",
                        occupation = doc.getString("occupation") ?: "",
                        notes = doc.getString("notes") ?: "",
                        photoUri = doc.getString("photoUri")?.ifBlank { null },
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                Log.w("FirestoreSync", "Error getting customer by ID from Firestore: ${e.localizedMessage}")
            }
        }
        return customerDao.getCustomerById(id)
    }

    suspend fun insertCustomer(customer: CustomerEntity): Long {
        val newId = if (customer.id == 0L) System.currentTimeMillis() else customer.id
        val entity = customer.copy(id = newId)

        customerDao.insertCustomer(entity)

        val uid = syncManager.getOrEnsureUid()
        syncManager.backupCustomer(uid, entity)
        syncManager.backupReminders(uid, db)
        return newId
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.updateCustomer(customer)
        val uid = syncManager.getOrEnsureUid()
        syncManager.backupCustomer(uid, customer)
        syncManager.backupReminders(uid, db)
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)
        val uid = syncManager.getOrEnsureUid()
        syncManager.deleteCustomerInCloud(uid, customer.id)
        syncManager.backupReminders(uid, db)
    }

    // --- Policies Firestore Flow & Operations ---
    val allPolicies: Flow<List<PolicyEntity>> = channelFlow {
        val roomJob = scope.launch {
            policyDao.getAllPolicies().collect { list ->
                send(list)
            }
        }

        val firestore = getFirestore()
        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        if (firestore != null) {
            scope.launch {
                try {
                    val uid = syncManager.getOrEnsureUid()
                    Log.d("FirestoreSync", "Listening for Policies in Firestore at path: agents/$uid/policies")
                    listenerRegistration = firestore.collection("agents").document(uid)
                        .collection("policies")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.w("FirestoreSync", "Firestore policy listener notice for UID: $uid (${error.localizedMessage}). Local database active.")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val list = snapshot.documents.mapNotNull { doc ->
                                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                                    if (id == 0L) null else PolicyEntity(
                                        id = id,
                                        policyNumber = doc.getString("policyNumber") ?: "",
                                        customerId = doc.getLong("customerId") ?: 0L,
                                        customerName = doc.getString("customerName") ?: "",
                                        planName = doc.getString("planName") ?: "",
                                        premiumAmount = doc.getDouble("premiumAmount") ?: 0.0,
                                        sumAssured = doc.getDouble("sumAssured") ?: 0.0,
                                        premiumMode = doc.getString("premiumMode") ?: "Yearly",
                                        dueDate = doc.getString("dueDate") ?: "",
                                        maturityDate = doc.getString("maturityDate") ?: "",
                                        status = doc.getString("status") ?: "Active",
                                        nominee = doc.getString("nominee") ?: "",
                                        policyTerm = (doc.getLong("policyTerm") ?: 20L).toInt(),
                                        premiumPayingTerm = (doc.getLong("premiumPayingTerm") ?: 16L).toInt(),
                                        issueDate = doc.getString("issueDate") ?: "",
                                        gracePeriodDays = (doc.getLong("gracePeriodDays") ?: 30L).toInt(),
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    )
                                }
                                if (list.isNotEmpty()) {
                                    scope.launch {
                                        list.forEach { policyDao.insertPolicy(it) }
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    Log.w("FirestoreSync", "Error attaching policy listener: ${e.localizedMessage}")
                }
            }
        }

        awaitClose {
            roomJob.cancel()
            listenerRegistration?.remove()
        }
    }

    fun searchPolicies(query: String): Flow<List<PolicyEntity>> {
        return if (query.isBlank()) allPolicies else policyDao.searchPolicies(query)
    }

    fun getPoliciesByCustomerId(customerId: Long): Flow<List<PolicyEntity>> = policyDao.getPoliciesByCustomerId(customerId)
    suspend fun getPolicyById(id: Long): PolicyEntity? = policyDao.getPolicyById(id)

    suspend fun insertPolicy(policy: PolicyEntity): Long {
        val newId = if (policy.id == 0L) System.currentTimeMillis() else policy.id
        val entity = policy.copy(id = newId)

        policyDao.insertPolicy(entity)

        val uid = syncManager.getOrEnsureUid()
        syncManager.backupPolicy(uid, entity)
        syncManager.backupReminders(uid, db)
        return newId
    }

    suspend fun updatePolicy(policy: PolicyEntity) {
        policyDao.updatePolicy(policy)

        val uid = syncManager.getOrEnsureUid()
        syncManager.backupPolicy(uid, policy)
        syncManager.backupReminders(uid, db)
    }

    suspend fun deletePolicy(policy: PolicyEntity) {
        policyDao.deletePolicy(policy)

        val uid = syncManager.getOrEnsureUid()
        syncManager.deletePolicyInCloud(uid, policy.id)
        syncManager.backupReminders(uid, db)
    }

    // --- Payments Firestore Flow & Operations ---
    val allPayments: Flow<List<PaymentEntity>> = channelFlow {
        val roomJob = scope.launch {
            paymentDao.getAllPayments().collect { list ->
                send(list)
            }
        }

        val firestore = getFirestore()
        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        if (firestore != null) {
            scope.launch {
                try {
                    val uid = syncManager.getOrEnsureUid()
                    Log.d("FirestoreSync", "Listening for Payments in Firestore at path: agents/$uid/payments")
                    listenerRegistration = firestore.collection("agents").document(uid)
                        .collection("payments")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.w("FirestoreSync", "Firestore payment listener notice for UID: $uid (${error.localizedMessage}). Local database active.")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val list = snapshot.documents.mapNotNull { doc ->
                                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                                    if (id == 0L) null else PaymentEntity(
                                        id = id,
                                        policyId = doc.getLong("policyId") ?: 0L,
                                        policyNumber = doc.getString("policyNumber") ?: "",
                                        customerId = doc.getLong("customerId") ?: 0L,
                                        customerName = doc.getString("customerName") ?: "",
                                        paidAmount = doc.getDouble("paidAmount") ?: 0.0,
                                        lateFee = doc.getDouble("lateFee") ?: 0.0,
                                        paymentDate = doc.getString("paymentDate") ?: "",
                                        paymentMode = doc.getString("paymentMode") ?: "UPI",
                                        receiptNumber = doc.getString("receiptNumber") ?: "",
                                        notes = doc.getString("notes") ?: "",
                                        installmentDueDate = doc.getString("installmentDueDate") ?: "",
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    )
                                }
                                if (list.isNotEmpty()) {
                                    scope.launch {
                                        list.forEach { paymentDao.insertPayment(it) }
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    Log.w("FirestoreSync", "Error attaching payment listener: ${e.localizedMessage}")
                }
            }
        }

        awaitClose {
            roomJob.cancel()
            listenerRegistration?.remove()
        }
    }

    fun getPaymentsByPolicyId(policyId: Long): Flow<List<PaymentEntity>> = paymentDao.getPaymentsByPolicyId(policyId)

    suspend fun collectPremium(payment: PaymentEntity, nextDueDate: String? = null) {
        val newPaymentId = if (payment.id == 0L) System.currentTimeMillis() else payment.id
        val policy = policyDao.getPolicyById(payment.policyId)

        val targetDueDate = if (payment.installmentDueDate.isNotBlank()) {
            payment.installmentDueDate
        } else {
            policy?.dueDate ?: LocalDate.now().toString()
        }

        val insertedPayment = payment.copy(
            id = newPaymentId,
            installmentDueDate = targetDueDate
        )

        paymentDao.insertPayment(insertedPayment)

        val uid = syncManager.getOrEnsureUid()

        if (policy != null) {
            recalculatePolicyAndDueDate(policy, nextDueDate)
        }

        syncManager.backupPayment(uid, insertedPayment)
        syncManager.backupReminders(uid, db)
    }

    suspend fun updatePayment(payment: PaymentEntity) {
        paymentDao.updatePayment(payment)

        val uid = syncManager.getOrEnsureUid()
        val policy = policyDao.getPolicyById(payment.policyId)
        if (policy != null) {
            recalculatePolicyAndDueDate(policy, null)
        }

        syncManager.backupPayment(uid, payment)
        syncManager.backupReminders(uid, db)
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        paymentDao.deletePayment(payment)

        val uid = syncManager.getOrEnsureUid()
        val policy = policyDao.getPolicyById(payment.policyId)
        if (policy != null) {
            recalculatePolicyAndDueDate(policy, null)
        }

        syncManager.deletePaymentInCloud(uid, payment.id)
        syncManager.backupReminders(uid, db)
    }

    private suspend fun recalculatePolicyAndDueDate(policy: PolicyEntity, providedNextDueDate: String?) {
        val uid = syncManager.getOrEnsureUid()
        val allPaymentsForPolicy = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            paymentDao.getAllPaymentsSync()
        }.filter { it.policyId == policy.id }
        val installment = policy.premiumAmount

        if (installment <= 0) return

        val summary = PaymentAllocationEngine.calculateCurrentDueSummary(policy, allPaymentsForPolicy)

        val updatedDueDate = if (providedNextDueDate != null && providedNextDueDate.isNotBlank()) {
            providedNextDueDate
        } else if (summary.outstanding == 0.0 && summary.totalPaidForCurrentDue >= policy.premiumAmount) {
            PaymentAllocationEngine.advanceDueDate(policy.dueDate, policy.premiumMode)
        } else {
            policy.dueDate
        }

        val updatedPolicy = policy.copy(
            dueDate = updatedDueDate,
            status = "Active"
        )
        policyDao.updatePolicy(updatedPolicy)
        syncManager.backupPolicy(uid, updatedPolicy)
    }

    private fun advanceDueDate(currentDue: String, mode: String): String {
        val baseDate = try { LocalDate.parse(currentDue) } catch (e: Exception) { LocalDate.now() }
        val nextDate = when (mode) {
            "Monthly" -> baseDate.plusMonths(1)
            "Quarterly" -> baseDate.plusMonths(3)
            "Half-Yearly" -> baseDate.plusMonths(6)
            "Yearly" -> baseDate.plusYears(1)
            else -> baseDate.plusMonths(6)
        }
        return nextDate.toString()
    }

    // --- Documents Firestore Flow & Operations ---
    val allDocuments: Flow<List<DocumentEntity>> = channelFlow {
        val roomJob = scope.launch {
            documentDao.getAllDocuments().collect { list ->
                send(list)
            }
        }

        val firestore = getFirestore()
        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        if (firestore != null) {
            scope.launch {
                try {
                    val uid = syncManager.getOrEnsureUid()
                    Log.d("FirestoreSync", "Listening for Documents in Firestore at path: agents/$uid/documents")
                    listenerRegistration = firestore.collection("agents").document(uid)
                        .collection("documents")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.w("FirestoreSync", "Firestore document listener notice for UID: $uid (${error.localizedMessage}). Local database active.")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                Log.d("FirestoreSync", "Received Document snapshot update from Firestore for UID: $uid (Doc count: ${snapshot.size()})")
                                val list = snapshot.documents.mapNotNull { doc ->
                                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                                    if (id == 0L) null else DocumentEntity(
                                        id = id,
                                        customerId = doc.getLong("customerId"),
                                        customerName = doc.getString("customerName") ?: "",
                                        policyId = doc.getLong("policyId"),
                                        docType = doc.getString("docType") ?: "Document",
                                        title = doc.getString("title") ?: "",
                                        fileUri = doc.getString("fileUri") ?: "",
                                        fileSize = doc.getString("fileSize") ?: "1.0 MB",
                                        uploadDate = doc.getString("uploadDate") ?: "",
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    )
                                }
                                if (list.isNotEmpty()) {
                                    scope.launch {
                                        list.forEach { documentDao.insertDocument(it) }
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    Log.w("FirestoreSync", "Error attaching document listener: ${e.localizedMessage}")
                }
            }
        }

        awaitClose {
            roomJob.cancel()
            listenerRegistration?.remove()
        }
    }

    fun getDocumentsForCustomer(customerId: Long): Flow<List<DocumentEntity>> = documentDao.getDocumentsForCustomer(customerId)
    fun getDocumentsForPolicy(policyId: Long): Flow<List<DocumentEntity>> = documentDao.getDocumentsForPolicy(policyId)

    suspend fun insertDocument(document: DocumentEntity): Long {
        val newId = if (document.id == 0L) System.currentTimeMillis() else document.id
        val entity = document.copy(id = newId)

        documentDao.insertDocument(entity)

        val uid = syncManager.getOrEnsureUid()
        syncManager.backupDocument(uid, entity)
        return newId
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document)

        val uid = syncManager.getOrEnsureUid()
        syncManager.backupDocument(uid, document)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document)

        val uid = syncManager.getOrEnsureUid()
        syncManager.deleteDocumentInCloud(uid, document.id)
    }

    // --- Agent Profile Firestore Flow & Operations ---
    val agentProfile: Flow<AgentProfileEntity?> = channelFlow {
        val roomJob = scope.launch {
            agentDao.getAgentProfile().collect { profile ->
                send(profile)
            }
        }

        val firestore = getFirestore()
        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        if (firestore != null) {
            scope.launch {
                try {
                    val uid = syncManager.getOrEnsureUid()
                    Log.d("FirestoreSync", "Listening for Agent Profile in Firestore at path: agents/$uid")
                    listenerRegistration = firestore.collection("agents").document(uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null || snapshot == null || !snapshot.exists()) {
                                return@addSnapshotListener
                            }
                            val profile = AgentProfileEntity(
                                id = 1,
                                agentName = snapshot.getString("agentName") ?: "Agent",
                                agencyCode = snapshot.getString("agencyCode") ?: "",
                                branchName = snapshot.getString("branchName") ?: "",
                                licenseNumber = snapshot.getString("licenseNumber") ?: "",
                                email = snapshot.getString("email") ?: (try { FirebaseAuth.getInstance().currentUser?.email } catch (_: Throwable) { null } ?: ""),
                                mobile = snapshot.getString("mobile") ?: "",
                                photoUri = snapshot.getString("photoUri") ?: "",
                                themeMode = snapshot.getString("themeMode") ?: "System",
                                pinCode = snapshot.getString("pinCode") ?: "",
                                autoLogoutMinutes = (snapshot.getLong("autoLogoutMinutes") ?: 15L).toInt(),
                                isAutoSyncEnabled = true,
                                lastSyncedTime = "Just now"
                            )
                            scope.launch {
                                send(profile)
                                agentDao.saveAgentProfile(profile)
                            }
                        }
                } catch (e: Exception) {
                    Log.w("FirestoreSync", "Error attaching agent profile listener: ${e.localizedMessage}")
                }
            }
        }

        awaitClose {
            roomJob.cancel()
            listenerRegistration?.remove()
        }
    }

    suspend fun saveAgentProfile(profile: AgentProfileEntity) {
        agentDao.saveAgentProfile(profile)
        val uid = syncManager.getOrEnsureUid()
        syncManager.backupAgentProfile(uid, profile)
    }

    // Trigger full manual sync / restore
    suspend fun restoreAndSyncAll() {
        val uid = syncManager.getOrEnsureUid()
        if (uid.isNotBlank()) {
            syncManager.autoRestoreAndSync(uid, db)
        }
    }

    // Dashboard Statistics Flow
    val dashboardStats: Flow<DashboardStats> = combine(
        allCustomers,
        allPolicies,
        allPayments
    ) { customers, policies, payments ->
        val todayStr = LocalDate.now().toString()
        val currentMonth = LocalDate.now().monthValue
        val currentYear = LocalDate.now().year

        var dueTodayCount = 0
        var dueTodayAmount = 0.0
        var dueThisMonthCount = 0
        var dueThisMonthAmount = 0.0
        var totalOutstanding = 0.0

        policies.forEach { policy ->
            val due = try { LocalDate.parse(policy.dueDate) } catch (e: Exception) { null }
            if (due != null) {
                if (due.toString() == todayStr) {
                    dueTodayCount++
                    dueTodayAmount += policy.premiumAmount
                }
                if (due.monthValue == currentMonth && due.year == currentYear) {
                    dueThisMonthCount++
                    dueThisMonthAmount += policy.premiumAmount
                }
                if (due.isBefore(LocalDate.now()) || (due.monthValue == currentMonth && due.year == currentYear)) {
                    totalOutstanding += policy.premiumAmount
                }
            }
        }

        val totalCollected = payments.sumOf { it.paidAmount }

        DashboardStats(
            totalCustomers = customers.size,
            totalPolicies = policies.size,
            dueTodayCount = dueTodayCount,
            dueTodayAmount = dueTodayAmount,
            dueThisMonthCount = dueThisMonthCount,
            dueThisMonthAmount = dueThisMonthAmount,
            premiumCollectedTotal = totalCollected,
            outstandingAmount = totalOutstanding
        )
    }

    // --- Follow-Up Operations ---
    val allFollowUps: Flow<List<FollowUpEntity>> = db.followUpDao().getAllFollowUps()

    suspend fun insertFollowUp(followUp: FollowUpEntity): Long = db.followUpDao().insertFollowUp(followUp)

    suspend fun updateFollowUp(followUp: FollowUpEntity) = db.followUpDao().updateFollowUp(followUp)

    suspend fun deleteFollowUp(followUp: FollowUpEntity) = db.followUpDao().deleteFollowUp(followUp)
}

