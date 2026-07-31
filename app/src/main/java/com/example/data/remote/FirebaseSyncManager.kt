package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Synced(val lastSyncTime: String) : SyncStatus()
    data class Offline(val lastSyncTime: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class FirebaseSyncManager(private val context: Context) {
    private val firestore: FirebaseFirestore?
        get() = try { FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
    private val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (e: Throwable) { null }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true // Fallback to attempting request
        }
    }

    private fun getCurrentTimeFormatted(): String {
        return try {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a, dd MMM yyyy"))
        } catch (e: Exception) {
            "Just now"
        }
    }

    // Agent Root Reference in Firestore: agents/{uid}
    private fun agentDocRef(uid: String) = firestore?.collection("agents")?.document(uid)

    // Save Agent Profile in Cloud
    suspend fun backupAgentProfile(uid: String, profile: AgentProfileEntity) {
        if (uid.isBlank()) return
        try {
            _syncStatus.value = SyncStatus.Syncing
            val data = mapOf(
                "id" to profile.id,
                "agentName" to profile.agentName,
                "agencyCode" to profile.agencyCode,
                "branchName" to profile.branchName,
                "licenseNumber" to profile.licenseNumber,
                "email" to profile.email,
                "mobile" to profile.mobile,
                "photoUri" to profile.photoUri,
                "themeMode" to profile.themeMode,
                "pinCode" to profile.pinCode,
                "autoLogoutMinutes" to profile.autoLogoutMinutes,
                "updatedAt" to System.currentTimeMillis()
            )
            val docRef = agentDocRef(uid) ?: return
            docRef.set(data, SetOptions.merge()).await()
            val time = getCurrentTimeFormatted()
            _syncStatus.value = SyncStatus.Synced(time)
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error backing up agent profile", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Profile backup failed: ${e.localizedMessage}")
            }
        }
    }

    // Customer Backup
    suspend fun backupCustomer(uid: String, customer: CustomerEntity) {
        if (uid.isBlank()) return
        try {
            _syncStatus.value = SyncStatus.Syncing
            val data = mapOf(
                "id" to customer.id,
                "name" to customer.name,
                "mobile" to customer.mobile,
                "whatsapp" to customer.whatsapp,
                "email" to customer.email,
                "address" to customer.address,
                "dob" to customer.dob,
                "anniversary" to customer.anniversary,
                "aadhaar" to customer.aadhaar,
                "pan" to customer.pan,
                "occupation" to customer.occupation,
                "notes" to customer.notes,
                "photoUri" to (customer.photoUri ?: ""),
                "createdAt" to customer.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            val docRef = agentDocRef(uid) ?: return
            docRef.collection("customers").document(customer.id.toString())
                .set(data, SetOptions.merge()).await()
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error backing up customer ${customer.id}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Customer backup failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteCustomerInCloud(uid: String, customerId: Long) {
        if (uid.isBlank()) return
        try {
            val docRef = agentDocRef(uid) ?: return
            docRef.collection("customers").document(customerId.toString()).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error deleting customer $customerId in cloud", e)
        }
    }

    // Policy Backup
    suspend fun backupPolicy(uid: String, policy: PolicyEntity) {
        if (uid.isBlank()) return
        try {
            _syncStatus.value = SyncStatus.Syncing
            val data = mapOf(
                "id" to policy.id,
                "policyNumber" to policy.policyNumber,
                "customerId" to policy.customerId,
                "customerName" to policy.customerName,
                "planName" to policy.planName,
                "premiumAmount" to policy.premiumAmount,
                "sumAssured" to policy.sumAssured,
                "premiumMode" to policy.premiumMode,
                "dueDate" to policy.dueDate,
                "maturityDate" to policy.maturityDate,
                "status" to policy.status,
                "nominee" to policy.nominee,
                "policyTerm" to policy.policyTerm,
                "premiumPayingTerm" to policy.premiumPayingTerm,
                "issueDate" to policy.issueDate,
                "gracePeriodDays" to policy.gracePeriodDays,
                "createdAt" to policy.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            val docRef = agentDocRef(uid) ?: return
            docRef.collection("policies").document(policy.id.toString())
                .set(data, SetOptions.merge()).await()
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error backing up policy ${policy.id}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Policy backup failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deletePolicyInCloud(uid: String, policyId: Long) {
        if (uid.isBlank()) return
        try {
            val docRef = agentDocRef(uid) ?: return
            docRef.collection("policies").document(policyId.toString()).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error deleting policy $policyId in cloud", e)
        }
    }

    // Payment Backup
    suspend fun backupPayment(uid: String, payment: PaymentEntity) {
        if (uid.isBlank()) return
        try {
            _syncStatus.value = SyncStatus.Syncing
            val data = mapOf(
                "id" to payment.id,
                "policyId" to payment.policyId,
                "policyNumber" to payment.policyNumber,
                "customerId" to payment.customerId,
                "customerName" to payment.customerName,
                "paidAmount" to payment.paidAmount,
                "lateFee" to payment.lateFee,
                "paymentDate" to payment.paymentDate,
                "paymentMode" to payment.paymentMode,
                "receiptNumber" to payment.receiptNumber,
                "notes" to payment.notes,
                "createdAt" to payment.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            val docRef = agentDocRef(uid) ?: return
            docRef.collection("payments").document(payment.id.toString())
                .set(data, SetOptions.merge()).await()
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error backing up payment ${payment.id}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Payment backup failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deletePaymentInCloud(uid: String, paymentId: Long) {
        if (uid.isBlank()) return
        try {
            val docRef = agentDocRef(uid) ?: return
            docRef.collection("payments").document(paymentId.toString()).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error deleting payment $paymentId in cloud", e)
        }
    }

    // Document Backup
    suspend fun backupDocument(uid: String, doc: DocumentEntity) {
        if (uid.isBlank()) return
        try {
            _syncStatus.value = SyncStatus.Syncing
            val data = mapOf(
                "id" to doc.id,
                "customerId" to (doc.customerId ?: 0L),
                "customerName" to doc.customerName,
                "policyId" to (doc.policyId ?: 0L),
                "docType" to doc.docType,
                "title" to doc.title,
                "fileUri" to doc.fileUri,
                "fileSize" to doc.fileSize,
                "uploadDate" to doc.uploadDate,
                "createdAt" to doc.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            val docRef = agentDocRef(uid) ?: return
            docRef.collection("documents").document(doc.id.toString())
                .set(data, SetOptions.merge()).await()
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error backing up document ${doc.id}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Document backup failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteDocumentInCloud(uid: String, docId: Long) {
        if (uid.isBlank()) return
        try {
            val docRef = agentDocRef(uid) ?: return
            docRef.collection("documents").document(docId.toString()).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error deleting document $docId in cloud", e)
        }
    }

    // AUTO RESTORE: Fetch all data from cloud for this agent and sync into Room DB
    suspend fun autoRestoreAndSync(uid: String, db: AppDatabase) {
        if (uid.isBlank()) return
        if (!isOnline()) {
            _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            return
        }

        try {
            _syncStatus.value = SyncStatus.Syncing
            val rootRef = agentDocRef(uid) ?: return

            // 1. Agent Profile Restore
            val agentSnapshot = rootRef.get().await()
            if (agentSnapshot.exists()) {
                val p = AgentProfileEntity(
                    id = 1,
                    agentName = agentSnapshot.getString("agentName") ?: "Agent",
                    agencyCode = agentSnapshot.getString("agencyCode") ?: "",
                    branchName = agentSnapshot.getString("branchName") ?: "",
                    licenseNumber = agentSnapshot.getString("licenseNumber") ?: "",
                    email = agentSnapshot.getString("email") ?: (auth?.currentUser?.email ?: ""),
                    mobile = agentSnapshot.getString("mobile") ?: "",
                    photoUri = agentSnapshot.getString("photoUri") ?: "",
                    themeMode = agentSnapshot.getString("themeMode") ?: "System",
                    pinCode = agentSnapshot.getString("pinCode") ?: "",
                    autoLogoutMinutes = (agentSnapshot.getLong("autoLogoutMinutes") ?: 15L).toInt(),
                    isAutoSyncEnabled = true,
                    lastSyncedTime = getCurrentTimeFormatted()
                )
                db.agentDao().saveAgentProfile(p)
            }

            // 2. Customers Restore
            val customerSnapshots = rootRef.collection("customers").get().await()
            for (doc in customerSnapshots.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                val customer = CustomerEntity(
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
                db.customerDao().insertCustomer(customer)
            }

            // 3. Policies Restore
            val policySnapshots = rootRef.collection("policies").get().await()
            for (doc in policySnapshots.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                val policy = PolicyEntity(
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
                db.policyDao().insertPolicy(policy)
            }

            // 4. Payments Restore
            val paymentSnapshots = rootRef.collection("payments").get().await()
            for (doc in paymentSnapshots.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                val payment = PaymentEntity(
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
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                db.paymentDao().insertPayment(payment)
            }

            // 5. Documents Restore
            val documentSnapshots = rootRef.collection("documents").get().await()
            for (doc in documentSnapshots.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                val document = DocumentEntity(
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
                db.documentDao().insertDocument(document)
            }

            val syncTime = getCurrentTimeFormatted()
            _syncStatus.value = SyncStatus.Synced(syncTime)

            // Update agent's lastSyncedTime in local profile
            val currentProfile = db.agentDao().getAgentProfileSync()
            if (currentProfile != null) {
                db.agentDao().saveAgentProfile(currentProfile.copy(lastSyncedTime = syncTime))
            }

        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error during cloud restore", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Restore failed: ${e.localizedMessage}")
            }
        }
    }

    // Bulk Initial Backup (if registering or syncing existing local database)
    suspend fun initialBackupAll(uid: String, db: AppDatabase) {
        if (uid.isBlank()) return
        if (!isOnline()) return

        try {
            _syncStatus.value = SyncStatus.Syncing

            // Profile
            val profile = db.agentDao().getAgentProfileSync()
            if (profile != null) {
                backupAgentProfile(uid, profile)
            }

            // Customers
            val customers = db.customerDao().getAllCustomersSync()
            customers.forEach { backupCustomer(uid, it) }

            // Policies
            val policies = db.policyDao().getAllPoliciesSync()
            policies.forEach { backupPolicy(uid, it) }

            // Payments
            val payments = db.paymentDao().getAllPaymentsSync()
            payments.forEach { backupPayment(uid, it) }

            // Documents
            val documents = db.documentDao().getAllDocumentsSync()
            documents.forEach { backupDocument(uid, it) }

            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Error during initial bulk backup", e)
        }
    }
}
