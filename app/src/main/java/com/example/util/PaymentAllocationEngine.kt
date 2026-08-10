package com.example.util

import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import java.time.LocalDate

/**
 * Summary for a specific Policy's current due cycle.
 */
data class CurrentDueSummary(
    val policyId: Long,
    val policyNumber: String,
    val currentDueDate: String,        // YYYY-MM-DD
    val customerName: String = "",
    val totalDue: Double,              // Premium due for current cycle
    val premiumAmount: Double,         // Same as totalDue
    val advanceAdjusted: Double,       // Advance carried forward from previous cycle
    val directPaid: Double,            // Payments directly made in current cycle
    val totalPaidForCurrentDue: Double,// advanceAdjusted + directPaid
    val totalPaid: Double,             // Total paid across all payments for policy
    val balance: Double,               // Outstanding remaining for current cycle = max(0, totalDue - totalPaidForCurrentDue)
    val outstanding: Double,           // Same as balance
    val advance: Double,               // Excess/advance carried forward beyond current cycle = max(0, totalPaidForCurrentDue - totalDue)
    val status: String                 // "Paid", "Partial", "Pending", "Overpaid"
)

/**
 * Details for a single payment row in payment history.
 */
data class PaymentRowAllocationDetails(
    val payment: PaymentEntity,
    val policy: PolicyEntity?,
    val allocatedDueDate: String,        // Target due date cycle (YYYY-MM-DD)
    val cumulativePaidForCycle: Double, // Cumulative paid for this due cycle up to this payment
    val cyclePremiumAmount: Double,      // Premium amount for this due cycle
    val outstandingAfterPayment: Double, // Outstanding remaining after this payment
    val advanceAfterPayment: Double,     // Advance/Excess after this payment
    val status: String                   // "Paid", "Partial", "Pending", "Overpaid"
)

object PaymentAllocationEngine {

    /**
     * Helper to check if two due date strings represent the same date (by string match or parsed LocalDate).
     */
    fun isSameDueDate(d1: String, d2: String): Boolean {
        if (d1.trim().equals(d2.trim(), ignoreCase = true)) return true
        val l1 = SearchFilterEngine.parseLocalDateSafe(d1)
        val l2 = SearchFilterEngine.parseLocalDateSafe(d2)
        return l1 != null && l2 != null && l1 == l2
    }

    /**
     * Resolves which due date cycle a payment is allocated to.
     * Uses explicit `installmentDueDate` if available, otherwise falls back to chronological allocation.
     */
    fun resolveAllocatedDueDate(
        payment: PaymentEntity,
        policy: PolicyEntity?,
        allPaymentsForPolicy: List<PaymentEntity>
    ): String {
        // 1. Explicit allocation
        if (payment.installmentDueDate.isNotBlank()) {
            return payment.installmentDueDate
        }

        if (policy == null || policy.premiumAmount <= 0.0) {
            return payment.paymentDate.ifBlank { LocalDate.now().toString() }
        }

        // 2. Chronological allocation for legacy/unallocated payments
        val sortedPayments = allPaymentsForPolicy
            .filter { it.policyId == policy.id }
            .sortedWith(compareBy({ it.paymentDate }, { it.createdAt }, { it.id }))

        val premium = policy.premiumAmount
        val baseDueDate = if (policy.dueDate.isNotBlank()) policy.dueDate else LocalDate.now().toString()

        var currentCycleDueDate = baseDueDate
        var currentCycleAccumulated = 0.0

        for (p in sortedPayments) {
            if (p.installmentDueDate.isNotBlank()) {
                if (p.id == payment.id) {
                    return p.installmentDueDate
                }
                continue
            }

            val isTarget = (p.id == payment.id)
            val assignedDueDate = currentCycleDueDate

            currentCycleAccumulated += p.paidAmount
            if (currentCycleAccumulated >= premium) {
                currentCycleDueDate = advanceDueDate(currentCycleDueDate, policy.premiumMode)
                currentCycleAccumulated = 0.0
            }

            if (isTarget) {
                return assignedDueDate
            }
        }

        return policy.dueDate.ifBlank { LocalDate.now().toString() }
    }

    /**
     * Computes the current due cycle summary for a policy with sequential payment allocation and advance carryover.
     */
    fun calculateCurrentDueSummary(
        policy: PolicyEntity,
        allPaymentsForPolicy: List<PaymentEntity>
    ): CurrentDueSummary {
        val currentDueDate = policy.dueDate.ifBlank { LocalDate.now().toString() }
        val premiumAmount = policy.premiumAmount

        val policyPayments = allPaymentsForPolicy
            .filter { it.policyId == policy.id }
            .sortedWith(compareBy({ it.paymentDate }, { it.createdAt }, { it.id }))

        val totalPaidAllTime = policyPayments.sumOf { it.paidAmount }

        if (premiumAmount <= 0.0) {
            return CurrentDueSummary(
                policyId = policy.id,
                policyNumber = policy.policyNumber,
                currentDueDate = currentDueDate,
                customerName = policy.customerName,
                totalDue = 0.0,
                premiumAmount = 0.0,
                advanceAdjusted = 0.0,
                directPaid = totalPaidAllTime,
                totalPaidForCurrentDue = totalPaidAllTime,
                totalPaid = totalPaidAllTime,
                balance = 0.0,
                outstanding = 0.0,
                advance = 0.0,
                status = "Paid"
            )
        }

        // Direct payments specifically targeted or resolved to currentDueDate
        val directPaymentsForCurrent = policyPayments.filter { p ->
            val resolvedDue = resolveAllocatedDueDate(p, policy, policyPayments)
            isSameDueDate(resolvedDue, currentDueDate)
        }
        val directPaid = directPaymentsForCurrent.sumOf { it.paidAmount }

        // Determine how many full cycles were cleared before currentDueDate
        val clearedCycles = if (premiumAmount > 0) (totalPaidAllTime / premiumAmount).toInt() else 0
        val totalConsumedByCleared = clearedCycles * premiumAmount
        val remainingFundsAllTime = kotlin.math.max(0.0, totalPaidAllTime - totalConsumedByCleared)

        // Advance adjusted is the unconsumed carried forward funds before any direct payments for the current cycle
        val advanceAdjusted = kotlin.math.max(0.0, remainingFundsAllTime - directPaid)
        val totalPaidForCurrentDue = advanceAdjusted + directPaid

        val balance = kotlin.math.max(0.0, premiumAmount - totalPaidForCurrentDue)
        val advance = kotlin.math.max(0.0, totalPaidForCurrentDue - premiumAmount)

        val status = when {
            totalPaidForCurrentDue > premiumAmount -> "Overpaid"
            totalPaidForCurrentDue == premiumAmount -> "Paid"
            totalPaidForCurrentDue > 0.0 -> "Partial"
            else -> "Pending"
        }

        return CurrentDueSummary(
            policyId = policy.id,
            policyNumber = policy.policyNumber,
            currentDueDate = currentDueDate,
            customerName = policy.customerName,
            totalDue = premiumAmount,
            premiumAmount = premiumAmount,
            advanceAdjusted = advanceAdjusted,
            directPaid = directPaid,
            totalPaidForCurrentDue = totalPaidForCurrentDue,
            totalPaid = totalPaidAllTime,
            balance = balance,
            outstanding = balance,
            advance = advance,
            status = status
        )
    }

    /**
     * Computes payment row allocation details for display in history tables/cards.
     */
    fun calculatePaymentRowDetails(
        payment: PaymentEntity,
        policy: PolicyEntity?,
        allPaymentsForPolicy: List<PaymentEntity>
    ): PaymentRowAllocationDetails {
        val allocatedDueDate = resolveAllocatedDueDate(payment, policy, allPaymentsForPolicy)
        val cyclePremium = policy?.premiumAmount ?: payment.paidAmount

        val sameCyclePayments = allPaymentsForPolicy
            .filter { it.policyId == payment.policyId }
            .filter { isSameDueDate(resolveAllocatedDueDate(it, policy, allPaymentsForPolicy), allocatedDueDate) }
            .sortedWith(compareBy({ it.paymentDate }, { it.createdAt }, { it.id }))

        var cumulative = 0.0
        for (p in sameCyclePayments) {
            cumulative += p.paidAmount
            if (p.id == payment.id) {
                break
            }
        }

        val currentCyclePaid = kotlin.math.min(cumulative, cyclePremium)
        val outstanding = kotlin.math.max(cyclePremium - currentCyclePaid, 0.0)
        val advance = kotlin.math.max(cumulative - cyclePremium, 0.0)
        val status = when {
            cumulative > cyclePremium -> "Overpaid"
            cumulative == cyclePremium && cyclePremium > 0.0 -> "Paid"
            cumulative > 0.0 -> "Partial"
            else -> "Pending"
        }

        return PaymentRowAllocationDetails(
            payment = payment,
            policy = policy,
            allocatedDueDate = allocatedDueDate,
            cumulativePaidForCycle = cumulative,
            cyclePremiumAmount = cyclePremium,
            outstandingAfterPayment = outstanding,
            advanceAfterPayment = advance,
            status = status
        )
    }

    fun advanceDueDate(currentDue: String, mode: String): String {
        return try {
            val date = SearchFilterEngine.parseLocalDateSafe(currentDue) ?: return currentDue
            val nextDate = when (mode.lowercase()) {
                "monthly" -> date.plusMonths(1)
                "quarterly" -> date.plusMonths(3)
                "half-yearly", "half yearly" -> date.plusMonths(6)
                "yearly", "annual" -> date.plusYears(1)
                else -> date.plusYears(1)
            }
            nextDate.toString()
        } catch (e: Exception) {
            currentDue
        }
    }
}
