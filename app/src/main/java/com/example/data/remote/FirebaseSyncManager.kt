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
import java.time.LocalDate
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

    // Resolves or ensures a non-empty authenticated Firebase Auth UID or local fallback
    suspend fun getOrEnsureUid(providedUid: String = ""): String {
        val currentAuth = auth
        if (currentAuth != null) {
            val user = currentAuth.currentUser
            if (user != null && user.uid.isNotBlank()) {
                Log.d("FirestoreSync", "Resolved active FirebaseAuth UID: ${user.uid}")
                return user.uid
            }
            try {
                Log.d("FirestoreSync", "No active FirebaseAuth currentUser. Attempting anonymous sign-in to ensure Firebase Auth session...")
                val res = currentAuth.signInAnonymously().await()
                val anonUid = res.user?.uid
                if (!anonUid.isNullOrBlank()) {
                    Log.i("FirestoreSync", "Firebase anonymous sign-in succeeded. Authenticated UID: $anonUid")
                    return anonUid
                }
            } catch (e: Throwable) {
                Log.w("FirestoreSync", "Firebase anonymous sign-in failed or unavailable: ${e.localizedMessage}")
            }
        }

        if (providedUid.isNotBlank() && providedUid != "default_agent") {
            Log.d("FirestoreSync", "Using provided UID: $providedUid")
            return providedUid
        }

        return try {
            val db = AppDatabase.getDatabase(context)
            val profile = db.agentDao().getAgentProfileSync()
            if (profile != null && profile.email.isNotBlank()) {
                val emailUid = "agent_" + kotlin.math.abs(profile.email.trim().lowercase().hashCode())
                Log.d("FirestoreSync", "Using local profile email UID: $emailUid")
                emailUid
            } else {
                Log.d("FirestoreSync", "Fallback to default_agent UID")
                "default_agent"
            }
        } catch (e: Throwable) {
            Log.d("FirestoreSync", "Fallback to default_agent UID due to error: ${e.message}")
            "default_agent"
        }
    }

    // Agent Root Reference in Firestore: agents/{uid}
    private fun agentDocRef(uid: String) = firestore?.collection("agents")?.document(uid)

    // Save Agent Profile / Settings in Cloud
    suspend fun backupAgentProfile(providedUid: String, profile: AgentProfileEntity) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid"
        Log.d(tag, "Attempting Firestore upload for Agent Profile (Name: ${profile.agentName}, Email: ${profile.email}) to path: $docPath")

        try {
            _syncStatus.value = SyncStatus.Syncing
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null. Verify google-services.json and Firebase initialization.")
            val data = mapOf(
                "id" to uid,
                "uid" to uid,
                "agentName" to profile.agentName,
                "agencyCode" to profile.agencyCode,
                "branchCode" to profile.branchCode,
                "branchName" to profile.branchName,
                "licenseNumber" to profile.licenseNumber,
                "email" to profile.email,
                "mobile" to profile.mobile,
                "photoUri" to profile.photoUri,
                "themeMode" to profile.themeMode,
                "isDarkMode" to profile.isDarkMode,
                "pinCode" to profile.pinCode,
                "isBiometricEnabled" to profile.isBiometricEnabled,
                "autoLogoutMinutes" to profile.autoLogoutMinutes,
                "isAutoSyncEnabled" to profile.isAutoSyncEnabled,
                "lastSyncedTime" to profile.lastSyncedTime,
                "updatedAt" to System.currentTimeMillis()
            )
            Log.d(tag, "Payload map for Agent Profile / Settings: $data")

            dbInstance.collection("agents").document(uid)
                .set(data, SetOptions.merge()).await()
            dbInstance.collection("settings").document(uid)
                .set(data, SetOptions.merge()).await()

            Log.i(tag, "SUCCESS: Agent Profile uploaded to Firestore at path: $docPath & settings/$uid")
            val time = getCurrentTimeFormatted()
            _syncStatus.value = SyncStatus.Synced(time)
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Agent Profile upload failed for path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Profile backup failed: ${e.localizedMessage}")
            }
        }
    }

    // Customer / Client Backup
    suspend fun backupCustomer(providedUid: String, customer: CustomerEntity) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid/customers/${customer.id}"
        Log.d(tag, "Attempting Firestore upload for Customer/Client ID: ${customer.id}, Name: '${customer.name}', Target Path: $docPath")

        try {
            _syncStatus.value = SyncStatus.Syncing
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null. Verify google-services.json and Firebase initialization.")
            val data = mapOf(
                "id" to customer.id,
                "clientId" to customer.id.toString(),
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
            Log.d(tag, "Payload map for Customer ID ${customer.id}: $data")

            dbInstance.collection("agents").document(uid)
                .collection("customers").document(customer.id.toString())
                .set(data, SetOptions.merge()).await()
            dbInstance.collection("clients").document(customer.id.toString())
                .set(data, SetOptions.merge()).await()

            Log.i(tag, "SUCCESS: Customer ID ${customer.id} ('${customer.name}') uploaded to Firestore at path: $docPath & clients/${customer.id}")
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Customer ID ${customer.id} ('${customer.name}') upload to Firestore failed at path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Customer backup failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteCustomerInCloud(providedUid: String, customerId: Long) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid/customers/$customerId"
        Log.d(tag, "Attempting Firestore deletion for Customer ID: $customerId at path: $docPath")
        try {
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null")
            dbInstance.collection("agents").document(uid).collection("customers").document(customerId.toString()).delete().await()
            dbInstance.collection("clients").document(customerId.toString()).delete().await()
            Log.i(tag, "SUCCESS: Deleted Customer ID $customerId from Firestore at path: $docPath & clients/$customerId")
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Customer ID $customerId deletion failed at path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
        }
    }

    // Policy Backup
    suspend fun backupPolicy(providedUid: String, policy: PolicyEntity) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid/policies/${policy.id}"
        Log.d(tag, "Attempting Firestore upload for Policy ID: ${policy.id}, Policy No: '${policy.policyNumber}', Target Path: $docPath")

        try {
            _syncStatus.value = SyncStatus.Syncing
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null")
            val data = mapOf(
                "id" to policy.id,
                "policyId" to policy.id.toString(),
                "policyNumber" to policy.policyNumber,
                "customerId" to policy.customerId,
                "clientId" to policy.customerId.toString(),
                "customerName" to policy.customerName,
                "clientName" to policy.customerName,
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
            Log.d(tag, "Payload map for Policy ID ${policy.id}: $data")

            dbInstance.collection("agents").document(uid)
                .collection("policies").document(policy.id.toString())
                .set(data, SetOptions.merge()).await()
            dbInstance.collection("policies").document(policy.id.toString())
                .set(data, SetOptions.merge()).await()

            Log.i(tag, "SUCCESS: Policy ID ${policy.id} (${policy.policyNumber}) uploaded to Firestore at path: $docPath & policies/${policy.id}")
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Policy ID ${policy.id} (${policy.policyNumber}) upload to Firestore failed at path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Policy backup failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deletePolicyInCloud(providedUid: String, policyId: Long) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid/policies/$policyId"
        Log.d(tag, "Attempting Firestore deletion for Policy ID: $policyId at path: $docPath")
        try {
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null")
            dbInstance.collection("agents").document(uid).collection("policies").document(policyId.toString()).delete().await()
            dbInstance.collection("policies").document(policyId.toString()).delete().await()
            Log.i(tag, "SUCCESS: Deleted Policy ID $policyId from Firestore at path: $docPath & policies/$policyId")
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Policy ID $policyId deletion failed at path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
        }
    }

    // Payment Backup
    suspend fun backupPayment(providedUid: String, payment: PaymentEntity) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid/payments/${payment.id}"
        Log.d(tag, "Attempting Firestore upload for Payment ID: ${payment.id}, Policy No: '${payment.policyNumber}', Amount: ${payment.paidAmount}, Target Path: $docPath")

        try {
            _syncStatus.value = SyncStatus.Syncing
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null")
            val data = mapOf(
                "id" to payment.id,
                "paymentId" to payment.id.toString(),
                "policyId" to payment.policyId,
                "policyNumber" to payment.policyNumber,
                "customerId" to payment.customerId,
                "clientId" to payment.customerId.toString(),
                "customerName" to payment.customerName,
                "clientName" to payment.customerName,
                "paidAmount" to payment.paidAmount,
                "amount" to payment.paidAmount,
                "lateFee" to payment.lateFee,
                "paymentDate" to payment.paymentDate,
                "paymentMode" to payment.paymentMode,
                "mode" to payment.paymentMode,
                "receiptNumber" to payment.receiptNumber,
                "collectedBy" to "Agent",
                "notes" to payment.notes,
                "createdAt" to payment.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            Log.d(tag, "Payload map for Payment ID ${payment.id}: $data")

            dbInstance.collection("agents").document(uid)
                .collection("payments").document(payment.id.toString())
                .set(data, SetOptions.merge()).await()
            dbInstance.collection("payments").document(payment.id.toString())
                .set(data, SetOptions.merge()).await()

            Log.i(tag, "SUCCESS: Payment ID ${payment.id} uploaded to Firestore at path: $docPath & payments/${payment.id}")
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Payment ID ${payment.id} upload to Firestore failed at path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Payment backup failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deletePaymentInCloud(providedUid: String, paymentId: Long) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid/payments/$paymentId"
        Log.d(tag, "Attempting Firestore deletion for Payment ID: $paymentId at path: $docPath")
        try {
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null")
            dbInstance.collection("agents").document(uid).collection("payments").document(paymentId.toString()).delete().await()
            dbInstance.collection("payments").document(paymentId.toString()).delete().await()
            Log.i(tag, "SUCCESS: Deleted Payment ID $paymentId from Firestore at path: $docPath & payments/$paymentId")
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Payment ID $paymentId deletion failed at path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
        }
    }

    // Document Backup
    suspend fun backupDocument(providedUid: String, doc: DocumentEntity) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid/documents/${doc.id}"
        Log.d(tag, "Attempting Firestore upload for Document ID: ${doc.id}, Title: '${doc.title}', Target Path: $docPath")

        try {
            _syncStatus.value = SyncStatus.Syncing
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null")
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
            Log.d(tag, "Payload map for Document ID ${doc.id}: $data")

            dbInstance.collection("agents").document(uid)
                .collection("documents").document(doc.id.toString())
                .set(data, SetOptions.merge()).await()

            Log.i(tag, "SUCCESS: Document ID ${doc.id} uploaded to Firestore at path: $docPath")
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Document ID ${doc.id} upload to Firestore failed at path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Document backup failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteDocumentInCloud(providedUid: String, docId: Long) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val docPath = "agents/$uid/documents/$docId"
        Log.d(tag, "Attempting Firestore deletion for Document ID: $docId at path: $docPath")
        try {
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null")
            dbInstance.collection("agents").document(uid).collection("documents").document(docId.toString()).delete().await()
            Log.i(tag, "SUCCESS: Deleted Document ID $docId from Firestore at path: $docPath")
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Document ID $docId deletion failed at path $docPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
        }
    }

    // Reminders Backup: Computes and writes reminders to agents/{uid}/reminders & reminders/ collection
    suspend fun backupReminders(providedUid: String, db: AppDatabase) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        val colPath = "agents/$uid/reminders"
        Log.d(tag, "Attempting Firestore upload for Reminders snapshot to path: $colPath")

        try {
            val dbInstance = firestore ?: throw IllegalStateException("FirebaseFirestore instance is null")
            val policies = db.policyDao().getAllPoliciesSync()
            val customers = db.customerDao().getAllCustomersSync()

            val remindersSubCol = dbInstance.collection("agents").document(uid).collection("reminders")
            val remindersTopCol = dbInstance.collection("reminders")

            // 1. Premium Due Reminders
            policies.forEach { p ->
                val pDueDate = try { LocalDate.parse(p.dueDate) } catch (e: Exception) { null }
                if (pDueDate != null && p.status != "Paid-up" && p.status != "Matured") {
                    val reminderId = "due_policy_${p.id}"
                    val data = mapOf(
                        "id" to reminderId,
                        "reminderId" to reminderId,
                        "type" to "PREMIUM_DUE",
                        "title" to "Premium Due: ${p.policyNumber}",
                        "message" to "Premium ₹${p.premiumAmount} due on ${p.dueDate} for ${p.customerName}",
                        "policyNumber" to p.policyNumber,
                        "customerName" to p.customerName,
                        "clientName" to p.customerName,
                        "dueDate" to p.dueDate,
                        "amount" to p.premiumAmount,
                        "status" to "Pending",
                        "updatedAt" to System.currentTimeMillis()
                    )
                    remindersSubCol.document(reminderId).set(data, SetOptions.merge()).await()
                    remindersTopCol.document(reminderId).set(data, SetOptions.merge()).await()
                }
            }

            // 2. Birthday Reminders
            customers.forEach { c ->
                if (c.dob.isNotBlank()) {
                    val reminderId = "birthday_cust_${c.id}"
                    val data = mapOf(
                        "id" to reminderId,
                        "reminderId" to reminderId,
                        "type" to "BIRTHDAY",
                        "title" to "Birthday Reminder: ${c.name}",
                        "message" to "Birthday on ${c.dob} for ${c.name} (${c.mobile})",
                        "customerName" to c.name,
                        "clientName" to c.name,
                        "customerMobile" to c.mobile,
                        "dob" to c.dob,
                        "status" to "Pending",
                        "updatedAt" to System.currentTimeMillis()
                    )
                    remindersSubCol.document(reminderId).set(data, SetOptions.merge()).await()
                    remindersTopCol.document(reminderId).set(data, SetOptions.merge()).await()
                }
            }

            // 3. Anniversary Reminders
            customers.forEach { c ->
                if (c.anniversary.isNotBlank()) {
                    val reminderId = "anniversary_cust_${c.id}"
                    val data = mapOf(
                        "id" to reminderId,
                        "reminderId" to reminderId,
                        "type" to "ANNIVERSARY",
                        "title" to "Anniversary Reminder: ${c.name}",
                        "message" to "Anniversary on ${c.anniversary} for ${c.name} (${c.mobile})",
                        "customerName" to c.name,
                        "clientName" to c.name,
                        "customerMobile" to c.mobile,
                        "anniversary" to c.anniversary,
                        "status" to "Pending",
                        "updatedAt" to System.currentTimeMillis()
                    )
                    remindersSubCol.document(reminderId).set(data, SetOptions.merge()).await()
                    remindersTopCol.document(reminderId).set(data, SetOptions.merge()).await()
                }
            }

            // 4. Maturity Reminders
            policies.forEach { p ->
                val pMaturity = try { LocalDate.parse(p.maturityDate) } catch (e: Exception) { null }
                if (pMaturity != null) {
                    val reminderId = "maturity_policy_${p.id}"
                    val data = mapOf(
                        "id" to reminderId,
                        "reminderId" to reminderId,
                        "type" to "MATURITY",
                        "title" to "Policy Maturity: ${p.policyNumber}",
                        "message" to "Policy ${p.policyNumber} maturing on ${p.maturityDate} (Sum Assured: ₹${p.sumAssured})",
                        "policyNumber" to p.policyNumber,
                        "customerName" to p.customerName,
                        "clientName" to p.customerName,
                        "maturityDate" to p.maturityDate,
                        "sumAssured" to p.sumAssured,
                        "status" to "Pending",
                        "updatedAt" to System.currentTimeMillis()
                    )
                    remindersSubCol.document(reminderId).set(data, SetOptions.merge()).await()
                    remindersTopCol.document(reminderId).set(data, SetOptions.merge()).await()
                }
            }

            Log.i(tag, "SUCCESS: Reminders snapshot written to Firestore at path: $colPath & reminders/")
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Reminders upload failed at path $colPath. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
        }
    }

    // AUTO RESTORE: Fetch all data from cloud for this agent and sync into Room DB
    suspend fun autoRestoreAndSync(providedUid: String, db: AppDatabase) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        if (uid.isBlank()) return
        if (!isOnline()) {
            _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            return
        }

        Log.d(tag, "Starting Firestore cloud auto-restore and sync for UID: $uid")
        try {
            _syncStatus.value = SyncStatus.Syncing
            val rootRef = agentDocRef(uid) ?: throw IllegalStateException("FirebaseFirestore agentDocRef is null")

            // 1. Agent Profile Restore
            Log.d(tag, "Fetching Agent Profile from agents/$uid...")
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
                Log.i(tag, "Restored Agent Profile from Firestore")
            }

            // 2. Customers Restore
            Log.d(tag, "Fetching Customers from agents/$uid/customers...")
            val customerSnapshots = rootRef.collection("customers").get().await()
            Log.d(tag, "Downloaded ${customerSnapshots.size()} customers from Firestore")
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
            Log.d(tag, "Fetching Policies from agents/$uid/policies...")
            val policySnapshots = rootRef.collection("policies").get().await()
            Log.d(tag, "Downloaded ${policySnapshots.size()} policies from Firestore")
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
            Log.d(tag, "Fetching Payments from agents/$uid/payments...")
            val paymentSnapshots = rootRef.collection("payments").get().await()
            Log.d(tag, "Downloaded ${paymentSnapshots.size()} payments from Firestore")
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
            Log.d(tag, "Fetching Documents from agents/$uid/documents...")
            val documentSnapshots = rootRef.collection("documents").get().await()
            Log.d(tag, "Downloaded ${documentSnapshots.size()} documents from Firestore")
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

            // Perform two-way sync: push local data to cloud so Firestore is immediately updated
            initialBackupAll(uid, db)

        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Firestore auto-restore failed for UID: $uid. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
            if (!isOnline()) {
                _syncStatus.value = SyncStatus.Offline(getCurrentTimeFormatted())
            } else {
                _syncStatus.value = SyncStatus.Error("Restore failed: ${e.localizedMessage}")
            }
        }
    }

    // Bulk Initial Backup (if registering or syncing existing local database)
    suspend fun initialBackupAll(providedUid: String, db: AppDatabase) {
        val uid = getOrEnsureUid(providedUid)
        val tag = "FirestoreSync"
        if (uid.isBlank()) return
        if (!isOnline()) return

        Log.d(tag, "Starting bulk initial upload to Firestore for UID: $uid")
        try {
            _syncStatus.value = SyncStatus.Syncing

            // Profile
            val profile = db.agentDao().getAgentProfileSync()
            if (profile != null) {
                backupAgentProfile(uid, profile)
            }

            // Customers
            val customers = db.customerDao().getAllCustomersSync()
            Log.d(tag, "Bulk uploading ${customers.size} customers to Firestore...")
            customers.forEach { backupCustomer(uid, it) }

            // Policies
            val policies = db.policyDao().getAllPoliciesSync()
            Log.d(tag, "Bulk uploading ${policies.size} policies to Firestore...")
            policies.forEach { backupPolicy(uid, it) }

            // Payments
            val payments = db.paymentDao().getAllPaymentsSync()
            Log.d(tag, "Bulk uploading ${payments.size} payments to Firestore...")
            payments.forEach { backupPayment(uid, it) }

            // Documents
            val documents = db.documentDao().getAllDocumentsSync()
            Log.d(tag, "Bulk uploading ${documents.size} documents to Firestore...")
            documents.forEach { backupDocument(uid, it) }

            // Reminders
            backupReminders(uid, db)

            Log.i(tag, "SUCCESS: Bulk initial upload to Firestore completed for UID: $uid")
            _syncStatus.value = SyncStatus.Synced(getCurrentTimeFormatted())
        } catch (e: Throwable) {
            Log.e(tag, "FAILED: Bulk initial upload to Firestore failed for UID: $uid. Exception type: ${e.javaClass.simpleName}, Message: ${e.message}", e)
        }
    }
}

