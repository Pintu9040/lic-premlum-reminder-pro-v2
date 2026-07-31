package com.example.data.repository

import com.example.data.local.*
import com.example.data.remote.FirebaseSyncManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    private fun getCurrentUid(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Throwable) {
            null
        }
    }

    // Customer operations
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
        return if (query.isBlank()) allCustomers else customerDao.searchCustomers(query)
    }

    suspend fun getCustomerById(id: Long): CustomerEntity? = customerDao.getCustomerById(id)

    suspend fun insertCustomer(customer: CustomerEntity): Long {
        val id = customerDao.insertCustomer(customer)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.backupCustomer(uid, customer.copy(id = id))
        }
        return id
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.updateCustomer(customer)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.backupCustomer(uid, customer)
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.deleteCustomerInCloud(uid, customer.id)
        }
    }

    // Policy operations
    val allPolicies: Flow<List<PolicyEntity>> = policyDao.getAllPolicies()

    fun searchPolicies(query: String): Flow<List<PolicyEntity>> {
        return if (query.isBlank()) allPolicies else policyDao.searchPolicies(query)
    }

    fun getPoliciesByCustomerId(customerId: Long): Flow<List<PolicyEntity>> = policyDao.getPoliciesByCustomerId(customerId)
    suspend fun getPolicyById(id: Long): PolicyEntity? = policyDao.getPolicyById(id)

    suspend fun insertPolicy(policy: PolicyEntity): Long {
        val id = policyDao.insertPolicy(policy)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.backupPolicy(uid, policy.copy(id = id))
        }
        return id
    }

    suspend fun updatePolicy(policy: PolicyEntity) {
        policyDao.updatePolicy(policy)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.backupPolicy(uid, policy)
        }
    }

    suspend fun deletePolicy(policy: PolicyEntity) {
        policyDao.deletePolicy(policy)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.deletePolicyInCloud(uid, policy.id)
        }
    }

    // Payment operations
    val allPayments: Flow<List<PaymentEntity>> = paymentDao.getAllPayments()
    fun getPaymentsByPolicyId(policyId: Long): Flow<List<PaymentEntity>> = paymentDao.getPaymentsByPolicyId(policyId)

    suspend fun collectPremium(payment: PaymentEntity, nextDueDate: String) {
        val id = paymentDao.insertPayment(payment)
        val insertedPayment = payment.copy(id = id)

        val policy = policyDao.getPolicyById(payment.policyId)
        val uid = getCurrentUid()

        if (policy != null) {
            val updatedPolicy = policy.copy(
                dueDate = nextDueDate,
                status = "Active"
            )
            policyDao.updatePolicy(updatedPolicy)
            if (!uid.isNullOrBlank()) {
                syncManager.backupPolicy(uid, updatedPolicy)
            }
        }

        if (!uid.isNullOrBlank()) {
            syncManager.backupPayment(uid, insertedPayment)
        }
    }

    suspend fun updatePayment(payment: PaymentEntity) {
        paymentDao.updatePayment(payment)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.backupPayment(uid, payment)
        }
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        paymentDao.deletePayment(payment)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.deletePaymentInCloud(uid, payment.id)
        }
    }

    // Document operations
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    fun getDocumentsForCustomer(customerId: Long): Flow<List<DocumentEntity>> = documentDao.getDocumentsForCustomer(customerId)
    fun getDocumentsForPolicy(policyId: Long): Flow<List<DocumentEntity>> = documentDao.getDocumentsForPolicy(policyId)

    suspend fun insertDocument(document: DocumentEntity): Long {
        val id = documentDao.insertDocument(document)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.backupDocument(uid, document.copy(id = id))
        }
        return id
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.backupDocument(uid, document)
        }
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.deleteDocumentInCloud(uid, document.id)
        }
    }

    // Agent profile
    val agentProfile: Flow<AgentProfileEntity?> = agentDao.getAgentProfile()

    suspend fun saveAgentProfile(profile: AgentProfileEntity) {
        agentDao.saveAgentProfile(profile)
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.backupAgentProfile(uid, profile)
        }
    }

    // Trigger full manual sync / restore
    suspend fun restoreAndSyncAll() {
        val uid = getCurrentUid()
        if (!uid.isNullOrBlank()) {
            syncManager.autoRestoreAndSync(uid, db)
        }
    }

    // Dashboard Statistics Flow
    val dashboardStats: Flow<DashboardStats> = combine(
        customerDao.getAllCustomers(),
        policyDao.getAllPolicies(),
        paymentDao.getAllPayments()
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
}
