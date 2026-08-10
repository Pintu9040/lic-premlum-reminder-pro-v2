package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PaymentEntity
import com.example.data.local.PolicyEntity
import com.example.util.PaymentAllocationEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LIC Agent CRM", appName)
  }

  @Test
  fun `verify payment allocation calculations - test case 1 (8400 premium, 2400 paid)`() {
    val policy = createDummyPolicy(premium = 8400.0)
    val payment = createDummyPayment(policyId = policy.id, amount = 2400.0, dueDate = policy.dueDate)

    val summary = PaymentAllocationEngine.calculateCurrentDueSummary(policy, listOf(payment))

    assertEquals(8400.0, summary.premiumAmount, 0.01)
    assertEquals(2400.0, summary.totalPaidForCurrentDue, 0.01)
    assertEquals(6000.0, summary.outstanding, 0.01)
    assertEquals(0.0, summary.advance, 0.01)
    assertEquals("Partial", summary.status)
  }

  @Test
  fun `verify payment allocation calculations - test case 2 (24000 premium, 12000 paid)`() {
    val policy = createDummyPolicy(premium = 24000.0)
    val payment = createDummyPayment(policyId = policy.id, amount = 12000.0, dueDate = policy.dueDate)

    val summary = PaymentAllocationEngine.calculateCurrentDueSummary(policy, listOf(payment))

    assertEquals(24000.0, summary.premiumAmount, 0.01)
    assertEquals(12000.0, summary.totalPaidForCurrentDue, 0.01)
    assertEquals(12000.0, summary.outstanding, 0.01)
    assertEquals(0.0, summary.advance, 0.01)
    assertEquals("Partial", summary.status)
  }

  @Test
  fun `verify payment allocation calculations - test case 3 (24000 premium, 24000 paid)`() {
    val policy = createDummyPolicy(premium = 24000.0)
    val payment = createDummyPayment(policyId = policy.id, amount = 24000.0, dueDate = policy.dueDate)

    val summary = PaymentAllocationEngine.calculateCurrentDueSummary(policy, listOf(payment))

    assertEquals(24000.0, summary.premiumAmount, 0.01)
    assertEquals(24000.0, summary.totalPaidForCurrentDue, 0.01)
    assertEquals(0.0, summary.outstanding, 0.01)
    assertEquals(0.0, summary.advance, 0.01)
    assertEquals("Paid", summary.status)
  }

  @Test
  fun `verify payment allocation calculations - test case 4 (24000 premium, 36000 paid)`() {
    val policy = createDummyPolicy(premium = 24000.0)
    val payment = createDummyPayment(policyId = policy.id, amount = 36000.0, dueDate = policy.dueDate)

    val summary = PaymentAllocationEngine.calculateCurrentDueSummary(policy, listOf(payment))

    assertEquals(24000.0, summary.premiumAmount, 0.01)
    assertEquals(36000.0, summary.totalPaidForCurrentDue, 0.01)
    assertEquals(0.0, summary.outstanding, 0.01)
    assertEquals(12000.0, summary.advance, 0.01)
    assertEquals("Overpaid", summary.status)
  }

  @Test
  fun `verify multiple payments in same cycle`() {
    val policy = createDummyPolicy(premium = 8400.0)
    val p1 = createDummyPayment(id = 1, policyId = policy.id, amount = 2400.0, dueDate = policy.dueDate)
    val p2 = createDummyPayment(id = 2, policyId = policy.id, amount = 3000.0, dueDate = policy.dueDate)

    val summary = PaymentAllocationEngine.calculateCurrentDueSummary(policy, listOf(p1, p2))

    assertEquals(8400.0, summary.premiumAmount, 0.01)
    assertEquals(5400.0, summary.totalPaidForCurrentDue, 0.01)
    assertEquals(3000.0, summary.outstanding, 0.01)
    assertEquals(0.0, summary.advance, 0.01)
    assertEquals("Partial", summary.status)
  }

  private fun createDummyPolicy(id: Long = 1, premium: Double, dueDate: String = "15 Aug 2026"): PolicyEntity {
    return PolicyEntity(
      id = id,
      customerId = 1,
      customerName = "Test Customer",
      policyNumber = "POL-123456",
      planName = "Jeevan Anand",
      sumAssured = 500000.0,
      premiumAmount = premium,
      premiumMode = "Yearly",
      policyTerm = 20,
      premiumPayingTerm = 20,
      issueDate = "2020-08-15",
      dueDate = dueDate,
      maturityDate = "2040-08-15",
      status = "Active"
    )
  }

  private fun createDummyPayment(
    id: Long = 1,
    policyId: Long,
    amount: Double,
    dueDate: String
  ): PaymentEntity {
    return PaymentEntity(
      id = id,
      customerId = 1,
      customerName = "Test Customer",
      policyId = policyId,
      policyNumber = "POL-123456",
      paidAmount = amount,
      paymentDate = "2026-08-08",
      paymentMode = "UPI",
      installmentDueDate = dueDate,
      receiptNumber = "REC-100$id"
    )
  }
}

