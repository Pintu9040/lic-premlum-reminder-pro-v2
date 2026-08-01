package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.DashboardStats
import com.example.data.repository.LicRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PolicyFilterStatus { ALL, ACTIVE, DUE, LAPSED, MATURED }
enum class PolicyModeFilter { ALL, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY }
enum class PolicySortOption { NEXT_DUE, PREMIUM_AMOUNT, CUSTOMER_NAME, RECENTLY_ADDED }
enum class PolicyFilterDue { ALL, DUE_TODAY, DUE_THIS_MONTH, OVERDUE, UPCOMING }
enum class CustomerFilterStatus { ALL, ACTIVE, DUE, LAPSED }

enum class PaymentDateFilter { ALL, TODAY, THIS_WEEK, THIS_MONTH }
enum class PaymentModeFilter { ALL, CASH, UPI, BANK_TRANSFER, CHEQUE }

data class PaymentDashboardStats(
    val totalPremium: Double = 0.0,
    val totalPaid: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val outstandingAmount: Double = 0.0,
    val todayCollection: Double = 0.0,
    val monthlyCollection: Double = 0.0,
    val paymentProgressPercent: Float = 0f
)

class LicViewModel(application: Application) : AndroidViewModel(application) {
    private val syncManager = com.example.data.remote.FirebaseSyncManager(application)
    private val repository = LicRepository(AppDatabase.getDatabase(application), syncManager)

    val syncStatus: StateFlow<com.example.data.remote.SyncStatus> = syncManager.syncStatus

    fun triggerSync() {
        viewModelScope.launch {
            repository.restoreAndSyncAll()
        }
    }

    // Search & Filter State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow(PolicyFilterStatus.ALL)
    val statusFilter: StateFlow<PolicyFilterStatus> = _statusFilter.asStateFlow()

    private val _modeFilter = MutableStateFlow(PolicyModeFilter.ALL)
    val modeFilter: StateFlow<PolicyModeFilter> = _modeFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(PolicySortOption.NEXT_DUE)
    val sortOption: StateFlow<PolicySortOption> = _sortOption.asStateFlow()

    private val _dueFilter = MutableStateFlow(PolicyFilterDue.ALL)
    val dueFilter: StateFlow<PolicyFilterDue> = _dueFilter.asStateFlow()

    private val _customerFilter = MutableStateFlow(CustomerFilterStatus.ALL)
    val customerFilter: StateFlow<CustomerFilterStatus> = _customerFilter.asStateFlow()

    // Payment Filter State
    private val _paymentDateFilter = MutableStateFlow(PaymentDateFilter.ALL)
    val paymentDateFilter: StateFlow<PaymentDateFilter> = _paymentDateFilter.asStateFlow()

    private val _paymentModeFilter = MutableStateFlow(PaymentModeFilter.ALL)
    val paymentModeFilter: StateFlow<PaymentModeFilter> = _paymentModeFilter.asStateFlow()

    private val _paymentSearchQuery = MutableStateFlow("")
    val paymentSearchQuery: StateFlow<String> = _paymentSearchQuery.asStateFlow()

    // Base flows
    val dashboardStats: StateFlow<DashboardStats> = repository.dashboardStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardStats()
    )

    val customers: StateFlow<List<CustomerEntity>> = combine(
        repository.allCustomers,
        repository.allPolicies,
        _searchQuery,
        _customerFilter
    ) { customerList, policyList, query, filter ->
        val today = LocalDate.now()
        customerList.filter { customer ->
            val custPolicies = policyList.filter { it.customerId == customer.id }

            // Search query matching: Name, Mobile, Email, Aadhaar, PAN, Occupation, or linked Policy Number/Plan
            val matchesQuery = query.isBlank() ||
                    customer.name.contains(query, ignoreCase = true) ||
                    customer.mobile.contains(query) ||
                    customer.email.contains(query, ignoreCase = true) ||
                    customer.aadhaar.contains(query) ||
                    customer.pan.contains(query, ignoreCase = true) ||
                    customer.occupation.contains(query, ignoreCase = true) ||
                    custPolicies.any {
                        it.policyNumber.contains(query, ignoreCase = true) ||
                                it.planName.contains(query, ignoreCase = true)
                    }

            // Customer Status determination
            val isLapsed = custPolicies.any { it.status.equals("Lapsed", ignoreCase = true) }
            val isDue = !isLapsed && custPolicies.any { policy ->
                try {
                    val d = LocalDate.parse(policy.dueDate)
                    d.isBefore(today) || d == today || d.isBefore(today.plusDays(30))
                } catch (e: Exception) { false }
            }

            val computedStatus = when {
                isLapsed -> CustomerFilterStatus.LAPSED
                isDue -> CustomerFilterStatus.DUE
                else -> CustomerFilterStatus.ACTIVE
            }

            val matchesFilter = when (filter) {
                CustomerFilterStatus.ALL -> true
                CustomerFilterStatus.ACTIVE -> computedStatus == CustomerFilterStatus.ACTIVE
                CustomerFilterStatus.DUE -> computedStatus == CustomerFilterStatus.DUE
                CustomerFilterStatus.LAPSED -> computedStatus == CustomerFilterStatus.LAPSED
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val policies: StateFlow<List<PolicyEntity>> = combine(
        repository.allPolicies,
        repository.allCustomers,
        _searchQuery,
        _statusFilter,
        _modeFilter,
        _dueFilter,
        _sortOption
    ) { flows: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        val list = flows[0] as List<PolicyEntity>
        @Suppress("UNCHECKED_CAST")
        val customersList = flows[1] as List<CustomerEntity>
        val query = flows[2] as String
        val status = flows[3] as PolicyFilterStatus
        val mode = flows[4] as PolicyModeFilter
        val due = flows[5] as PolicyFilterDue
        val sort = flows[6] as PolicySortOption

        val todayStr = LocalDate.now().toString()
        val currentMonth = LocalDate.now().monthValue
        val currentYear = LocalDate.now().year

        val filtered = list.filter { policy ->
            val linkedCustomer = customersList.find { it.id == policy.customerId }

            // Search matching (Customer Name, Policy #, Plan Name, Phone Number)
            val matchesQuery = query.isBlank() ||
                    policy.policyNumber.contains(query, ignoreCase = true) ||
                    policy.planName.contains(query, ignoreCase = true) ||
                    policy.customerName.contains(query, ignoreCase = true) ||
                    (linkedCustomer != null && (linkedCustomer.mobile.contains(query) || linkedCustomer.whatsapp.contains(query)))

            // Status filter matching
            val matchesStatus = when (status) {
                PolicyFilterStatus.ALL -> true
                PolicyFilterStatus.ACTIVE -> policy.status.equals("Active", ignoreCase = true)
                PolicyFilterStatus.DUE -> policy.status.equals("Due", ignoreCase = true) || policy.status.equals("Grace", ignoreCase = true)
                PolicyFilterStatus.LAPSED -> policy.status.equals("Lapsed", ignoreCase = true)
                PolicyFilterStatus.MATURED -> policy.status.equals("Matured", ignoreCase = true)
            }

            // Mode filter matching
            val matchesMode = when (mode) {
                PolicyModeFilter.ALL -> true
                PolicyModeFilter.MONTHLY -> policy.premiumMode.equals("Monthly", ignoreCase = true)
                PolicyModeFilter.QUARTERLY -> policy.premiumMode.equals("Quarterly", ignoreCase = true)
                PolicyModeFilter.HALF_YEARLY -> policy.premiumMode.equals("Half-Yearly", ignoreCase = true)
                PolicyModeFilter.YEARLY -> policy.premiumMode.equals("Yearly", ignoreCase = true)
            }

            // Due filter matching
            val policyDueDate = try { LocalDate.parse(policy.dueDate) } catch (e: Exception) { null }
            val matchesDue = when (due) {
                PolicyFilterDue.ALL -> true
                PolicyFilterDue.DUE_TODAY -> policyDueDate?.toString() == todayStr
                PolicyFilterDue.DUE_THIS_MONTH -> policyDueDate != null && policyDueDate.monthValue == currentMonth && policyDueDate.year == currentYear
                PolicyFilterDue.OVERDUE -> policyDueDate != null && policyDueDate.isBefore(LocalDate.now())
                PolicyFilterDue.UPCOMING -> policyDueDate != null && policyDueDate.isAfter(LocalDate.now())
            }

            matchesQuery && matchesStatus && matchesMode && matchesDue
        }

        when (sort) {
            PolicySortOption.NEXT_DUE -> filtered.sortedBy { it.dueDate }
            PolicySortOption.PREMIUM_AMOUNT -> filtered.sortedByDescending { it.premiumAmount }
            PolicySortOption.CUSTOMER_NAME -> filtered.sortedBy { it.customerName.lowercase() }
            PolicySortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentEntity>> = repository.allPayments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredPayments: StateFlow<List<PaymentEntity>> = combine(
        repository.allPayments,
        _paymentSearchQuery,
        _paymentDateFilter,
        _paymentModeFilter
    ) { paymentList, query, dateFilter, modeFilter ->
        val today = LocalDate.now()
        val currentMonth = today.monthValue
        val currentYear = today.year
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)

        paymentList.filter { payment ->
            val matchesQuery = query.isBlank() ||
                    payment.customerName.contains(query, ignoreCase = true) ||
                    payment.policyNumber.contains(query, ignoreCase = true) ||
                    payment.receiptNumber.contains(query, ignoreCase = true) ||
                    payment.notes.contains(query, ignoreCase = true)

            val pDate = try { LocalDate.parse(payment.paymentDate) } catch (e: Exception) { null }

            val matchesDate = when (dateFilter) {
                PaymentDateFilter.ALL -> true
                PaymentDateFilter.TODAY -> pDate?.isEqual(today) == true
                PaymentDateFilter.THIS_WEEK -> pDate != null && !pDate.isBefore(startOfWeek) && !pDate.isAfter(today)
                PaymentDateFilter.THIS_MONTH -> pDate != null && pDate.monthValue == currentMonth && pDate.year == currentYear
            }

            val matchesMode = when (modeFilter) {
                PaymentModeFilter.ALL -> true
                PaymentModeFilter.CASH -> payment.paymentMode.equals("Cash", ignoreCase = true)
                PaymentModeFilter.UPI -> payment.paymentMode.equals("UPI", ignoreCase = true)
                PaymentModeFilter.BANK_TRANSFER -> payment.paymentMode.contains("Bank", ignoreCase = true) || payment.paymentMode.contains("Net", ignoreCase = true)
                PaymentModeFilter.CHEQUE -> payment.paymentMode.equals("Cheque", ignoreCase = true)
            }

            matchesQuery && matchesDate && matchesMode
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentStats: StateFlow<PaymentDashboardStats> = combine(
        repository.allPolicies,
        repository.allPayments
    ) { policies, paymentsList ->
        val today = LocalDate.now()
        val todayStr = today.toString()
        val currentMonth = today.monthValue
        val currentYear = today.year

        val totalPremium = policies.sumOf { it.premiumAmount }
        val totalPaid = paymentsList.sumOf { it.paidAmount + it.lateFee }
        val remaining = (totalPremium - totalPaid).coerceAtLeast(0.0)

        val outstanding = policies.filter { policy ->
            try {
                val d = LocalDate.parse(policy.dueDate)
                d.isBefore(today) || d.isEqual(today)
            } catch (e: Exception) { false }
        }.sumOf { it.premiumAmount }

        val todayCollect = paymentsList.filter { it.paymentDate == todayStr }.sumOf { it.paidAmount + it.lateFee }
        val monthCollect = paymentsList.filter {
            try {
                val d = LocalDate.parse(it.paymentDate)
                d.monthValue == currentMonth && d.year == currentYear
            } catch (e: Exception) { false }
        }.sumOf { it.paidAmount + it.lateFee }

        val progress = if (totalPremium > 0) ((totalPaid / totalPremium) * 100).toFloat().coerceAtMost(100f) else 0f

        PaymentDashboardStats(
            totalPremium = totalPremium,
            totalPaid = totalPaid,
            remainingBalance = remaining,
            outstandingAmount = outstanding,
            todayCollection = todayCollect,
            monthlyCollection = monthCollect,
            paymentProgressPercent = progress
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentDashboardStats())

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val agentProfile: StateFlow<AgentProfileEntity?> = repository.agentProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val followUps: StateFlow<List<FollowUpEntity>> = repository.allFollowUps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCustomerFilter(filter: CustomerFilterStatus) {
        _customerFilter.value = filter
    }

    fun setStatusFilter(filter: PolicyFilterStatus) {
        _statusFilter.value = filter
    }

    fun setModeFilter(filter: PolicyModeFilter) {
        _modeFilter.value = filter
    }

    fun setSortOption(option: PolicySortOption) {
        _sortOption.value = option
    }

    fun setDueFilter(filter: PolicyFilterDue) {
        _dueFilter.value = filter
    }

    // Payment Filter Setters
    fun setPaymentSearchQuery(query: String) {
        _paymentSearchQuery.value = query
    }

    fun setPaymentDateFilter(filter: PaymentDateFilter) {
        _paymentDateFilter.value = filter
    }

    fun setPaymentModeFilter(filter: PaymentModeFilter) {
        _paymentModeFilter.value = filter
    }

    // Customer Actions
    fun addCustomer(customer: CustomerEntity, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertCustomer(customer)
            onComplete(id)
        }
    }

    fun updateCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
        }
    }

    // Policy Actions
    fun addPolicy(policy: PolicyEntity, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertPolicy(policy)
            onComplete(id)
        }
    }

    fun updatePolicy(policy: PolicyEntity) {
        viewModelScope.launch {
            repository.updatePolicy(policy)
        }
    }

    fun deletePolicy(policy: PolicyEntity) {
        viewModelScope.launch {
            repository.deletePolicy(policy)
        }
    }

    // Payment Collection & Management Actions
    fun collectPremium(
        policy: PolicyEntity,
        paidAmount: Double,
        lateFee: Double = 0.0,
        paymentMode: String,
        receiptNo: String = "",
        paymentDate: String = "",
        notes: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val dateStr = if (paymentDate.isNotBlank()) paymentDate else LocalDate.now().toString()
            val generatedReceipt = if (receiptNo.isNotBlank()) receiptNo else "REC-${System.currentTimeMillis()}"

            val payment = PaymentEntity(
                policyId = policy.id,
                policyNumber = policy.policyNumber,
                customerId = policy.customerId,
                customerName = policy.customerName,
                paidAmount = paidAmount,
                lateFee = lateFee,
                paymentDate = dateStr,
                paymentMode = paymentMode,
                receiptNumber = generatedReceipt,
                notes = notes
            )

            repository.collectPremium(payment)
            onSuccess()
        }
    }

    fun updatePayment(payment: PaymentEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updatePayment(payment)
            onSuccess()
        }
    }

    fun deletePayment(payment: PaymentEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            onSuccess()
        }
    }

    private fun calculateNextDueDate(currentDue: String, mode: String): String {
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

    // Document Actions
    fun addDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.insertDocument(doc)
        }
    }

    fun updateDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.updateDocument(doc)
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
        }
    }

    // Agent Profile Action
    fun saveAgentProfile(profile: AgentProfileEntity) {
        viewModelScope.launch {
            repository.saveAgentProfile(profile)
        }
    }

    // FollowUp Actions
    fun addFollowUp(followUp: FollowUpEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertFollowUp(followUp)
            onComplete()
        }
    }

    fun updateFollowUp(followUp: FollowUpEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateFollowUp(followUp)
            onComplete()
        }
    }

    fun deleteFollowUp(followUp: FollowUpEntity) {
        viewModelScope.launch {
            repository.deleteFollowUp(followUp)
        }
    }

    // WhatsApp Message Templates
    fun generatePremiumReminderMsg(
        customerName: String,
        policyNo: String,
        planName: String,
        amount: Double,
        dueDate: String,
        outstandingBalance: Double = 0.0
    ): String {
        val agent = agentProfile.value?.agentName.takeIf { !it.isNullOrBlank() } ?: "LIC Advisor"
        val mobile = agentProfile.value?.mobile ?: ""
        val branch = agentProfile.value?.branchName ?: ""
        val agencyCode = agentProfile.value?.agencyCode ?: ""
        val outstandingStr = if (outstandingBalance > 0) "• Outstanding Balance: ₹${"%.2f".format(outstandingBalance)}\n" else ""

        return "Dear $customerName,\n\n" +
                "This is an official reminder regarding your LIC Policy No: $policyNo ($planName).\n\n" +
                "• Premium Amount: ₹${"%.2f".format(amount)}\n" +
                "• Due Date: $dueDate\n" +
                outstandingStr +
                "• Advisor Name: $agent\n" +
                (if (agencyCode.isNotBlank()) "• Agency Code: $agencyCode\n" else "") +
                (if (branch.isNotBlank()) "• Branch: $branch\n" else "") +
                "\nKindly pay your premium on time to ensure uninterrupted life cover and policy bonuses.\n\n" +
                "Warm Regards,\n" +
                "$agent (LIC Insurance Advisor)\n" +
                (if (mobile.isNotBlank()) "Contact: $mobile" else "")
    }

    fun generateBirthdayWishMsg(customerName: String): String {
        val agent = agentProfile.value?.agentName ?: "LIC Agent"
        return "Wishing you a very Happy Birthday, $customerName! 🎉🎂\n\n" +
                "May this year bring you abundant happiness, health, and financial security.\n\n" +
                "Warm wishes,\n$agent\nLIC India"
    }

    fun generateAnniversaryWishMsg(customerName: String): String {
        val agent = agentProfile.value?.agentName ?: "LIC Agent"
        return "Happy Marriage Anniversary to you & your spouse, $customerName! 💐✨\n\n" +
                "Wishing you both a lifetime of togetherness, love, and prosperity.\n\n" +
                "Best Regards,\n$agent\nLIC India"
    }

    fun generateMaturityReminderMsg(customerName: String, policyNo: String, planName: String, sumAssured: Double, maturityDate: String): String {
        val agent = agentProfile.value?.agentName ?: "LIC Agent"
        val mobile = agentProfile.value?.mobile ?: ""
        return "Dear $customerName,\n\n" +
                "Great news regarding your LIC Policy No: $policyNo ($planName)!\n" +
                "Your policy maturity date is $maturityDate with a Sum Assured of ₹${"%.2f".format(sumAssured)} plus accrued bonuses.\n\n" +
                "Please get in touch to submit your discharge form and NEFT bank details for smooth maturity claim processing.\n\n" +
                "Regards,\n$agent\n$mobile"
    }
}
