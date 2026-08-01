package com.example.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LicApplication
import com.example.data.local.AgentProfileEntity
import com.example.data.local.AppDatabase
import com.example.data.remote.FirebaseSyncManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object LoggedOut : AuthState()
    object Loading : AuthState()
    data class LoggedIn(
        val uid: String,
        val email: String,
        val name: String,
        val agencyCode: String,
        val branchName: String = "",
        val mobile: String = ""
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private fun getFirebaseAuthResult(): Result<FirebaseAuth> {
        return try {
            if (com.google.firebase.FirebaseApp.getApps(getApplication()).isEmpty()) {
                Log.w("AuthViewModel", "FirebaseApp not initialized prior to Auth call. Initializing now...")
                val app = com.google.firebase.FirebaseApp.initializeApp(getApplication())
                if (app == null) {
                    val initErr = LicApplication.firebaseInitializationError ?: "FirebaseApp initialization returned null"
                    Log.e("AuthViewModel", "Firebase initialization failed: $initErr")
                    return Result.failure(IllegalStateException("Firebase Initialization Failed: $initErr"))
                }
            }
            val auth = FirebaseAuth.getInstance()
            Result.success(auth)
        } catch (e: Throwable) {
            val msg = e.localizedMessage ?: e.message ?: e.toString()
            Log.e("AuthViewModel", "Failed to initialize or retrieve FirebaseAuth: $msg", e)
            Result.failure(e)
        }
    }

    private val firestore: FirebaseFirestore?
        get() = try { FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
    private val syncManager: FirebaseSyncManager by lazy { FirebaseSyncManager(getApplication()) }
    private val db: AppDatabase by lazy { AppDatabase.getDatabase(getApplication()) }

    private val _authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _forgotPasswordSuccess = MutableStateFlow<String?>(null)
    val forgotPasswordSuccess: StateFlow<String?> = _forgotPasswordSuccess.asStateFlow()

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            try {
                val authResult = getFirebaseAuthResult()
                val firebaseAuth = authResult.getOrNull()
                val user = firebaseAuth?.currentUser
                if (user != null) {
                    val uid = user.uid
                    val email = user.email ?: ""
                    var name = user.displayName ?: ""
                    var agencyCode = "LIC-AGENT-89421"
                    var branchName = "Branch 883"
                    var mobile = ""

                    // Load cached or cloud profile
                    try {
                        val fs = firestore
                        if (fs != null) {
                            val profileDoc = fs.collection("agents").document(uid).get().await()
                            if (profileDoc.exists()) {
                                name = profileDoc.getString("agentName")?.ifBlank { null } ?: name
                                agencyCode = profileDoc.getString("agencyCode") ?: agencyCode
                                branchName = profileDoc.getString("branchName") ?: branchName
                                mobile = profileDoc.getString("mobile") ?: mobile
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("AuthViewModel", "Offline or failed to fetch profile on start: ${e.localizedMessage}")
                    }

                    if (name.isBlank()) {
                        name = email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                    }

                    val profile = AgentProfileEntity(
                        id = 1,
                        agentName = name,
                        agencyCode = agencyCode,
                        branchName = branchName,
                        email = email,
                        mobile = mobile
                    )
                    db.agentDao().saveAgentProfile(profile)

                    _authState.value = AuthState.LoggedIn(
                        uid = uid,
                        email = email,
                        name = name,
                        agencyCode = agencyCode,
                        branchName = branchName,
                        mobile = mobile
                    )

                    // Trigger auto restore in background
                    launch {
                        syncManager.autoRestoreAndSync(uid, db)
                    }
                } else {
                    _authState.value = AuthState.LoggedOut
                }
            } catch (e: Throwable) {
                Log.e("AuthViewModel", "Failed to check existing session", e)
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    fun login(email: String, pass: String) {
        val trimmedEmail = email.trim()
        val trimmedPass = pass.trim()

        if (trimmedEmail.isBlank() || trimmedPass.isBlank()) {
            _authState.value = AuthState.Error("Please enter valid email and password")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val authResult = getFirebaseAuthResult()
            val firebaseAuth = authResult.getOrElse { throwable ->
                val errorMsg = throwable.localizedMessage ?: throwable.message ?: "Firebase initialization failed."
                Log.e("AuthViewModel", "Login aborted: Firebase initialization failed ($errorMsg)", throwable)
                _authState.value = AuthState.Error(errorMsg)
                return@launch
            }

            try {
                Log.d("AuthViewModel", "Attempting Firebase signInWithEmailAndPassword for $trimmedEmail")
                val result = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, trimmedPass).await()
                val user = result.user
                if (user != null) {
                    val uid = user.uid
                    var name = user.displayName ?: ""
                    var agencyCode = "LIC-AGENT-89421"
                    var branchName = "Branch 883"
                    var mobile = ""

                    // Fetch Agent Profile from Cloud
                    try {
                        val fs = firestore
                        if (fs != null) {
                            val profileDoc = fs.collection("agents").document(uid).get().await()
                            if (profileDoc.exists()) {
                                name = profileDoc.getString("agentName") ?: name
                                agencyCode = profileDoc.getString("agencyCode") ?: agencyCode
                                branchName = profileDoc.getString("branchName") ?: branchName
                                mobile = profileDoc.getString("mobile") ?: mobile
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AuthViewModel", "Error reading profile during login", e)
                    }

                    if (name.isBlank()) {
                        name = trimmedEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                    }

                    val profile = AgentProfileEntity(
                        id = 1,
                        agentName = name,
                        agencyCode = agencyCode,
                        branchName = branchName,
                        email = trimmedEmail,
                        mobile = mobile
                    )
                    db.agentDao().saveAgentProfile(profile)

                    _authState.value = AuthState.LoggedIn(
                        uid = uid,
                        email = trimmedEmail,
                        name = name,
                        agencyCode = agencyCode,
                        branchName = branchName,
                        mobile = mobile
                    )

                    launch {
                        syncManager.autoRestoreAndSync(uid, db)
                    }
                } else {
                    val errorMsg = "Authentication failed: Empty user account returned from Firebase."
                    Log.e("AuthViewModel", "Login error: $errorMsg")
                    _authState.value = AuthState.Error(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: e.toString()
                Log.e("AuthViewModel", "Firebase signInWithEmailAndPassword exception: $errorMsg", e)
                _authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    fun register(
        name: String,
        email: String,
        agencyCode: String,
        branchName: String,
        mobile: String,
        pass: String
    ) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedAgency = agencyCode.trim()
        val trimmedBranch = branchName.trim()
        val trimmedMobile = mobile.trim()
        val trimmedPass = pass.trim()

        if (trimmedName.isBlank() || trimmedEmail.isBlank() || trimmedPass.length < 6) {
            _authState.value = AuthState.Error("Please fill out all required fields. Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val authResult = getFirebaseAuthResult()
            val firebaseAuth = authResult.getOrElse { throwable ->
                val errorMsg = throwable.localizedMessage ?: throwable.message ?: "Firebase initialization failed."
                Log.e("AuthViewModel", "Registration aborted: Firebase initialization failed ($errorMsg)", throwable)
                _authState.value = AuthState.Error(errorMsg)
                return@launch
            }

            val profile = AgentProfileEntity(
                id = 1,
                agentName = trimmedName,
                agencyCode = if (trimmedAgency.isNotBlank()) trimmedAgency else "LIC-AGENT-89421",
                branchName = if (trimmedBranch.isNotBlank()) trimmedBranch else "Branch Office",
                email = trimmedEmail,
                mobile = trimmedMobile
            )

            try {
                Log.d("AuthViewModel", "Attempting Firebase createUserWithEmailAndPassword for $trimmedEmail")
                val result = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, trimmedPass).await()
                val user = result.user
                if (user != null) {
                    val uid = user.uid

                    try {
                        user.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(trimmedName)
                                .build()
                        ).await()
                    } catch (e: Exception) {
                        Log.w("AuthViewModel", "Could not set auth display name: ${e.localizedMessage}")
                    }

                    db.agentDao().saveAgentProfile(profile)
                    syncManager.backupAgentProfile(uid, profile)
                    syncManager.initialBackupAll(uid, db)

                    _authState.value = AuthState.LoggedIn(
                        uid = uid,
                        email = trimmedEmail,
                        name = trimmedName,
                        agencyCode = profile.agencyCode,
                        branchName = profile.branchName,
                        mobile = profile.mobile
                    )
                } else {
                    val errorMsg = "Registration failed: Empty user account returned from Firebase."
                    Log.e("AuthViewModel", "Registration error: $errorMsg")
                    _authState.value = AuthState.Error(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: e.toString()
                Log.e("AuthViewModel", "Firebase createUserWithEmailAndPassword exception: $errorMsg", e)
                _authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    fun resetPassword(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _authState.value = AuthState.Error("Please enter your registered email address")
            return
        }

        viewModelScope.launch {
            val authResult = getFirebaseAuthResult()
            val firebaseAuth = authResult.getOrElse { throwable ->
                val errorMsg = throwable.localizedMessage ?: throwable.message ?: "Firebase initialization failed."
                Log.e("AuthViewModel", "Reset password aborted: Firebase initialization failed ($errorMsg)", throwable)
                _authState.value = AuthState.Error(errorMsg)
                return@launch
            }

            try {
                firebaseAuth.sendPasswordResetEmail(trimmedEmail).await()
                _forgotPasswordSuccess.value = "Password reset email sent to $trimmedEmail. Please check your inbox."
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: e.toString()
                Log.e("AuthViewModel", "Reset password request failed: $errorMsg", e)
                _authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    fun clearForgotPasswordSuccess() {
        _forgotPasswordSuccess.value = null
    }

    fun logout() {
        viewModelScope.launch {
            try {
                getFirebaseAuthResult().getOrNull()?.signOut()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Logout error", e)
            }
            _authState.value = AuthState.LoggedOut
        }
    }
}
