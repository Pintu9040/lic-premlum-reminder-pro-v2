package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.DashboardStats
import com.example.data.repository.LicRepository
import com.example.util.PaymentAllocationEngine
import com.example.util.SearchFilterEngine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PolicyFilterStatus { ALL, ACTIVE, DUE, LAPSED, MATURED }
enum class PolicyModeFilter { ALL, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY }
enum class PolicySortOption { NEXT_DUE, PREMIUM_AMOUNT, CUSTOMER_NAME, RECENTLY_ADDED, CUSTOMER_NAME_AZ, CUSTOMER_NAME_ZA, PREMIUM_HIGH_LOW, PREMIUM_LOW_HIGH, DUE_DATE }
enum class PolicyFilterDue { ALL, DUE_TODAY, DUE_THIS_MONTH, OVERDUE, UPCOMING }
enum class CustomerFilterStatus { ALL, ACTIVE, DUE, LAPSED, INACTIVE, DUE_TODAY, DUE_TOMORROW, UPCOMING, OVERDUE }

private data class PolicyFilters(
    val status: PolicyFilterStatus,
    val mode: PolicyModeFilter,
    val due: PolicyFilterDue,
    val sort: PolicySortOption
)

private data class PaymentFilters(
    val dateFilter: PaymentDateFilter,
    val modeFilter: PaymentModeFilter,
    val startDate: String?,
    val endDate: String?
)

enum class FilterCategory { DUE_DATE, STATUS, MODE }

enum class SearchFilterOption(val label: String, val category: FilterCategory) {
    TODAY_DUE("Today Due", FilterCategory.DUE_DATE),
    TOMORROW_DUE("Tomorrow Due", FilterCategory.DUE_DATE),
    THIS_WEEK("This Week", FilterCategory.DUE_DATE),
    THIS_MONTH("This Month", FilterCategory.DUE_DATE),
    UPCOMING("Upcoming", FilterCategory.DUE_DATE),
    OVERDUE("Overdue", FilterCategory.DUE_DATE),
    PAID("Paid", FilterCategory.STATUS),
    UNPAID("Unpaid", FilterCategory.STATUS),
    HALF_YEARLY("Half-Yearly", FilterCategory.MODE),
    QUARTERLY("Quarterly", FilterCategory.MODE),
    MONTHLY("Monthly", FilterCategory.MODE),
    YEARLY("Yearly", FilterCategory.MODE)
}

enum class PaymentDateFilter { ALL, TODAY, THIS_WEEK, THIS_MONTH, CUSTOM_DATE }
enum class PaymentModeFilter { ALL, CASH, UPI, BANK_TRANSFER, CHEQUE, ONLINE }

data class PaymentDashboardStats(
    val totalPremium: Double = 0.0,
    val totalPaid: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val outstandingAmount: Double = 0.0,
    val todayCollection: Double = 0.0,
    val monthlyCollection: Double = 0.0,
    val paymentProgressPercent: Float = 0f
)

@OptIn(FlowPreview::class)
class LicViewModel(application: Application) : AndroidViewModel(application) {
    private val syncManager = com.example.data.remote.FirebaseSyncManager(application)
    private val repository = LicRepository(AppDatabase.getDatabase(application), syncManager)

    val syncStatus: StateFlow<com.example.data.remote.SyncStatus> = syncManager.syncStatus

    // Pull To Refresh & Sync Management
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshTime = MutableStateFlow(
        try {
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a, dd MMM yyyy"))
        } catch (e: Exception) {
            "Just now"
        }
    )
    val lastRefreshTime: StateFlow<String> = _lastRefreshTime.asStateFlow()

    fun refreshData(onComplete: ((Boolean, String) -> Unit)? = null) {
        if (_isRefreshing.value) return // Disable duplicate refresh requests

        viewModelScope.launch {
            _isRefreshing.value = true
            val isOnline = syncManager.isOnline()
            try {
                if (isOnline) {
                    val uid = syncManager.getOrEnsureUid()
                    if (uid.isNotBlank()) {
                        syncManager.autoRestoreAndSync(uid, AppDatabase.getDatabase(getApplication()))
                    }
                    val nowStr = try {
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a, dd MMM yyyy"))
                    } catch (e: Exception) { "Just now" }
                    _lastRefreshTime.value = nowStr
                    _isRefreshing.value = false
                    onComplete?.invoke(true, "Data synced & refreshed successfully ($nowStr)")
                } else {
                    kotlinx.coroutines.delay(600) // Smooth animation feedback for local database reload
                    val nowStr = try {
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a, dd MMM yyyy"))
                    } catch (e: Exception) { "Just now" }
                    _lastRefreshTime.value = nowStr
                    _isRefreshing.value = false
                    onComplete?.invoke(true, "Offline data refreshed ($nowStr)")
                }
            } catch (e: Exception) {
                _isRefreshing.value = false
                onComplete?.invoke(false, "Refresh error: ${e.localizedMessage ?: "Sync failed"}")
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            repository.restoreAndSyncAll()
        }
    }

    // Search & Filter State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 300ms Debounced search flow for smooth typing performance without UI lag
    val debouncedSearchQuery: Flow<String> = _searchQuery
        .debounce(300L)
        .distinctUntilChanged()

    private val _selectedSearchFilters = MutableStateFlow<Set<SearchFilterOption>>(emptySet())
    val selectedSearchFilters: StateFlow<Set<SearchFilterOption>> = _selectedSearchFilters.asStateFlow()

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

    val debouncedPaymentSearchQuery: Flow<String> = _paymentSearchQuery
        .debounce(300L)
        .distinctUntilChanged()

    private val _paymentStartDate = MutableStateFlow<String?>(null)
    val paymentStartDate: StateFlow<String?> = _paymentStartDate.asStateFlow()

    private val _paymentEndDate = MutableStateFlow<String?>(null)
    val paymentEndDate: StateFlow<String?> = _paymentEndDate.asStateFlow()

    // Intermediate filter states for combine optimization
    private val _customerFilterState = combine(_customerFilter, _selectedSearchFilters) { filter, searchFilters ->
        filter to searchFilters
    }

    private val _policyFiltersState = combine(_statusFilter, _modeFilter, _dueFilter, _sortOption) { status, mode, due, sort ->
        PolicyFilters(status, mode, due, sort)
    }

    private val _paymentFiltersState = combine(_paymentDateFilter, _paymentModeFilter, _paymentStartDate, _paymentEndDate) { dateFilter, modeFilter, startDate, endDate ->
        PaymentFilters(dateFilter, modeFilter, startDate, endDate)
    }

    // Base flows
    val dashboardStats: StateFlow<DashboardStats> = repository.dashboardStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardStats()
    )

    val customers: StateFlow<List<CustomerEntity>> = combine(
        repository.allCustomers,
        repository.allPolicies,
        repository.allPayments,
        _searchQuery,
        _customerFilterState
    ) { customerList, policyList, paymentList, query, (filter, searchFilters) ->
        val today = LocalDate.now()
        val todayStr = today.toString()
        val tomorrowStr = today.plusDays(1).toString()
        val weekEnd = today.plusDays(7)

        val dateFilters = searchFilters.filter { it.category == FilterCategory.DUE_DATE }
        val statusFilters = searchFilters.filter { it.category == FilterCategory.STATUS }
        val modeFilters = searchFilters.filter { it.category == FilterCategory.MODE }

        customerList.filter { customer ->
            val custPolicies = policyList.filter { it.customerId == customer.id }
            val custPayments = paymentList.filter { it.customerId == customer.id }

            // Global Multi-Keyword Search across Name, Mobile, WhatsApp, Policy #, Plan Name, Nominee Name, Receipt #
            val matchesQuery = SearchFilterEngine.matchesQuery(
                query = query,
                fields = listOf(
                    customer.name,
                    customer.mobile,
                    customer.whatsapp,
                    customer.email,
                    customer.aadhaar,
                    customer.pan,
                    customer.occupation
                ) + custPolicies.flatMap { listOf(it.policyNumber, it.planName, it.nominee) }
                  + custPayments.map { it.receiptNumber }
            )

            // Customer Status determination
            val isLapsed = custPolicies.any { it.status.equals("Lapsed", ignoreCase = true) }
            val isDueToday = custPolicies.any { it.dueDate == todayStr }
            val isDueTomorrow = custPolicies.any { it.dueDate == tomorrowStr }
            val isOverdue = custPolicies.any { p ->
                val d = SearchFilterEngine.parseLocalDateSafe(p.dueDate)
                d != null && d.isBefore(today) && !p.status.equals("Paid-up", ignoreCase = true) && !p.status.equals("Matured", ignoreCase = true)
            }
            val isUpcoming = custPolicies.any { p ->
                val d = SearchFilterEngine.parseLocalDateSafe(p.dueDate)
                d != null && d.isAfter(today) && d.isBefore(today.plusDays(30))
            }

            val matchesFilter = when (filter) {
                CustomerFilterStatus.ALL -> true
                CustomerFilterStatus.ACTIVE -> custPolicies.isNotEmpty() && !isLapsed && !isOverdue
                CustomerFilterStatus.INACTIVE, CustomerFilterStatus.LAPSED -> isLapsed || custPolicies.isEmpty()
                CustomerFilterStatus.DUE, CustomerFilterStatus.DUE_TODAY -> isDueToday
                CustomerFilterStatus.DUE_TOMORROW -> isDueTomorrow
                CustomerFilterStatus.UPCOMING -> isUpcoming
                CustomerFilterStatus.OVERDUE -> isOverdue
            }

            // Multi-selection Search Filters
            val matchesDateFilter = if (dateFilters.isEmpty()) true else {
                dateFilters.any { opt ->
                    when (opt) {
                        SearchFilterOption.TODAY_DUE -> custPolicies.any { it.dueDate == todayStr }
                        SearchFilterOption.TOMORROW_DUE -> custPolicies.any { it.dueDate == tomorrowStr }
                        SearchFilterOption.THIS_WEEK -> custPolicies.any { p ->
                            val d = SearchFilterEngine.parseLocalDateSafe(p.dueDate)
                            d != null && !d.isBefore(today) && !d.isAfter(weekEnd)
                        }
                        SearchFilterOption.THIS_MONTH -> custPolicies.any { p ->
                            val d = SearchFilterEngine.parseLocalDateSafe(p.dueDate)
                            d != null && d.monthValue == today.monthValue && d.year == today.year
                        }
                        SearchFilterOption.UPCOMING -> isUpcoming
                        SearchFilterOption.OVERDUE -> isOverdue
                        else -> false
                    }
                }
            }

            val matchesStatusFilter = if (statusFilters.isEmpty()) true else {
                statusFilters.any { opt ->
                    when (opt) {
                        SearchFilterOption.PAID -> custPolicies.isNotEmpty() && custPolicies.all {
                            it.status.equals("Paid", ignoreCase = true) || it.status.equals("Paid-up", ignoreCase = true) || it.status.equals("Active", ignoreCase = true)
                        }
                        SearchFilterOption.UNPAID -> custPolicies.any {
                            it.status.equals("Due", ignoreCase = true) || it.status.equals("Grace", ignoreCase = true) || it.status.equals("Lapsed", ignoreCase = true) || it.status.equals("Unpaid", ignoreCase = true)
                        }
                        else -> false
                    }
                }
            }

            val matchesModeFilter = if (modeFilters.isEmpty()) true else {
                modeFilters.any { opt ->
                    when (opt) {
                        SearchFilterOption.HALF_YEARLY -> custPolicies.any { it.premiumMode.contains("Half", ignoreCase = true) || it.premiumMode.equals("Hly", ignoreCase = true) }
                        SearchFilterOption.QUARTERLY -> custPolicies.any { it.premiumMode.contains("Quarter", ignoreCase = true) || it.premiumMode.equals("Qly", ignoreCase = true) }
                        SearchFilterOption.MONTHLY -> custPolicies.any { it.premiumMode.contains("Month", ignoreCase = true) || it.premiumMode.equals("Mly", ignoreCase = true) }
                        SearchFilterOption.YEARLY -> custPolicies.any { it.premiumMode.contains("Year", ignoreCase = true) || it.premiumMode.equals("Yly", ignoreCase = true) }
                        else -> false
                    }
                }
            }

            matchesQuery && matchesFilter && matchesDateFilter && matchesStatusFilter && matchesModeFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val policies: StateFlow<List<PolicyEntity>> = combine(
        repository.allPolicies,
        repository.allCustomers,
        repository.allPayments,
        _searchQuery,
        _policyFiltersState
    ) { list, customersList, paymentsList, query, (status, mode, due, sort) ->
        val todayStr = LocalDate.now().toString()
        val currentMonth = LocalDate.now().monthValue
        val currentYear = LocalDate.now().year

        val filtered = list.filter { policy ->
            val linkedCustomer = customersList.find { it.id == policy.customerId }
            val linkedReceipts = paymentsList.filter { it.policyId == policy.id }.map { it.receiptNumber }

            // Global Multi-Keyword Search: Customer Name, Mobile, WhatsApp, Policy #, Plan Name, Nominee, Receipt #
            val matchesQuery = SearchFilterEngine.matchesQuery(
                query = query,
                fields = listOf(
                    policy.policyNumber,
                    policy.planName,
                    policy.customerName,
                    policy.nominee,
                    linkedCustomer?.mobile,
                    linkedCustomer?.whatsapp,
                    linkedCustomer?.email
                ) + linkedReceipts
            )

            // Status filter matching: Active, Paid, Pending, Lapsed, Matured, Cancelled
            val matchesStatus = when (status) {
                PolicyFilterStatus.ALL -> true
                PolicyFilterStatus.ACTIVE -> policy.status.equals("Active", ignoreCase = true)
                PolicyFilterStatus.DUE -> policy.status.equals("Due", ignoreCase = true) || policy.status.equals("Grace", ignoreCase = true) || policy.status.equals("Pending", ignoreCase = true)
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
            val policyDueDate = SearchFilterEngine.parseLocalDateSafe(policy.dueDate)
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
            PolicySortOption.NEXT_DUE, PolicySortOption.DUE_DATE -> filtered.sortedBy { it.dueDate }
            PolicySortOption.PREMIUM_AMOUNT, PolicySortOption.PREMIUM_HIGH_LOW -> filtered.sortedByDescending { it.premiumAmount }
            PolicySortOption.PREMIUM_LOW_HIGH -> filtered.sortedBy { it.premiumAmount }
            PolicySortOption.CUSTOMER_NAME, PolicySortOption.CUSTOMER_NAME_AZ -> filtered.sortedBy { it.customerName.lowercase() }
            PolicySortOption.CUSTOMER_NAME_ZA -> filtered.sortedByDescending { it.customerName.lowercase() }
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
        repository.allCustomers,
        _paymentSearchQuery,
        _paymentFiltersState
    ) { paymentList, customersList, query, (dateFilter, modeFilter, startDate, endDate) ->
        val today = LocalDate.now()
        val currentMonth = today.monthValue
        val currentYear = today.year
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)

        val startLocalDate = SearchFilterEngine.parseLocalDateSafe(startDate)
        val endLocalDate = SearchFilterEngine.parseLocalDateSafe(endDate)

        paymentList.filter { payment ->
            val linkedCustomer = customersList.find { it.id == payment.customerId }

            // Search matching: Customer Name, Mobile, WhatsApp, Policy #, Receipt #, Notes
            val matchesQuery = SearchFilterEngine.matchesQuery(
                query = query,
                fields = listOf(
                    payment.customerName,
                    payment.policyNumber,
                    payment.receiptNumber,
                    payment.notes,
                    linkedCustomer?.mobile,
                    linkedCustomer?.whatsapp
                )
            )

            val pDate = SearchFilterEngine.parseLocalDateSafe(payment.paymentDate)

            val matchesDate = when (dateFilter) {
                PaymentDateFilter.ALL -> true
                PaymentDateFilter.TODAY -> pDate?.isEqual(today) == true
                PaymentDateFilter.THIS_WEEK -> pDate != null && !pDate.isBefore(startOfWeek) && !pDate.isAfter(today)
                PaymentDateFilter.THIS_MONTH -> pDate != null && pDate.monthValue == currentMonth && pDate.year == currentYear
                PaymentDateFilter.CUSTOM_DATE -> {
                    if (pDate == null) false
                    else {
                        val matchesStart = startLocalDate == null || !pDate.isBefore(startLocalDate)
                        val matchesEnd = endLocalDate == null || !pDate.isAfter(endLocalDate)
                        matchesStart && matchesEnd
                    }
                }
            }

            val matchesMode = when (modeFilter) {
                PaymentModeFilter.ALL -> true
                PaymentModeFilter.CASH -> payment.paymentMode.equals("Cash", ignoreCase = true)
                PaymentModeFilter.UPI -> payment.paymentMode.equals("UPI", ignoreCase = true)
                PaymentModeFilter.BANK_TRANSFER -> payment.paymentMode.contains("Bank", ignoreCase = true) || payment.paymentMode.contains("Net", ignoreCase = true)
                PaymentModeFilter.CHEQUE -> payment.paymentMode.equals("Cheque", ignoreCase = true)
                PaymentModeFilter.ONLINE -> payment.paymentMode.contains("Online", ignoreCase = true) || payment.paymentMode.contains("UPI", ignoreCase = true)
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

        val policySummaries = policies.map { PaymentAllocationEngine.calculateCurrentDueSummary(it, paymentsList) }
        val totalPremium = policySummaries.sumOf { it.premiumAmount }
        val totalPaidForCurrentDues = policySummaries.sumOf { it.totalPaidForCurrentDue }
        val totalPaidAllTime = paymentsList.sumOf { it.paidAmount + it.lateFee }
        val remaining = policySummaries.sumOf { it.outstanding }

        val outstanding = policySummaries.filter { summary ->
            val d = SearchFilterEngine.parseLocalDateSafe(summary.currentDueDate)
            if (d != null) {
                (d.isBefore(today) || d.isEqual(today)) && summary.outstanding > 0.0
            } else {
                summary.outstanding > 0.0
            }
        }.sumOf { it.outstanding }

        val todayCollect = paymentsList.filter { it.paymentDate == todayStr }.sumOf { it.paidAmount + it.lateFee }
        val monthCollect = paymentsList.filter {
            try {
                val d = LocalDate.parse(it.paymentDate)
                d.monthValue == currentMonth && d.year == currentYear
            } catch (e: Exception) { false }
        }.sumOf { it.paidAmount + it.lateFee }

        val progress = if (totalPremium > 0) ((totalPaidForCurrentDues / totalPremium) * 100).toFloat().coerceAtMost(100f) else 0f

        PaymentDashboardStats(
            totalPremium = totalPremium,
            totalPaid = totalPaidAllTime,
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

    fun setSearchFilters(filters: Set<SearchFilterOption>) {
        _selectedSearchFilters.value = filters
    }

    fun resetSearchFilters() {
        _selectedSearchFilters.value = emptySet()
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

    fun setPaymentCustomDateRange(startDate: String?, endDate: String?) {
        _paymentStartDate.value = startDate
        _paymentEndDate.value = endDate
        if (startDate != null || endDate != null) {
            _paymentDateFilter.value = PaymentDateFilter.CUSTOM_DATE
        }
    }

    fun setPaymentModeFilter(filter: PaymentModeFilter) {
        _paymentModeFilter.value = filter
    }

    fun clearAllFilters() {
        _searchQuery.value = ""
        _paymentSearchQuery.value = ""
        _selectedSearchFilters.value = emptySet()
        _statusFilter.value = PolicyFilterStatus.ALL
        _modeFilter.value = PolicyModeFilter.ALL
        _dueFilter.value = PolicyFilterDue.ALL
        _customerFilter.value = CustomerFilterStatus.ALL
        _paymentDateFilter.value = PaymentDateFilter.ALL
        _paymentModeFilter.value = PaymentModeFilter.ALL
        _paymentStartDate.value = null
        _paymentEndDate.value = null
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
                notes = notes,
                installmentDueDate = policy.dueDate
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
