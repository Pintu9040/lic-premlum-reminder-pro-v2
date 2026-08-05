package com.example.ui.documents

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.CustomerEntity
import com.example.ui.LicViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Royal Blue Dark Theme Palette
private val DarkBackground = Color(0xFF0B1120)
private val DarkCardSurface = Color(0xFF1E293B)
private val DarkCardSurfaceVariant = Color(0xFF0F172A)
private val RoyalBluePrimary = Color(0xFF1D4ED8)
private val RoyalBlueLight = Color(0xFF3B82F6)
private val RoyalBlueGlow = Color(0xFF2563EB)
private val EmeraldGreen = Color(0xFF10B981)
private val EmeraldGreenContainer = Color(0xFF064E3B)
private val AmberDue = Color(0xFFF59E0B)
private val AmberDueContainer = Color(0xFF78350F)
private val CrimsonOverdue = Color(0xFFEF4444)
private val CrimsonOverdueContainer = Color(0xFF7F1D1D)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)
private val BorderSlate = Color(0xFF334155)

// UI Document Item Data Model
data class VaultDocumentModel(
    val id: Long,
    val title: String,
    val category: String,
    val isUploaded: Boolean,
    val uploadDate: String?,
    val fileSize: String?,
    val icon: ImageVector,
    val fileUri: String? = null,
    val fileName: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentVaultScreen(
    customer: CustomerEntity? = null,
    viewModel: LicViewModel? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // SharedPreferences for local persistence
    val prefs = remember { context.getSharedPreferences("vault_documents_pref", Context.MODE_PRIVATE) }

    fun saveDocToPrefs(doc: VaultDocumentModel) {
        prefs.edit()
            .putBoolean("doc_${doc.id}_uploaded", doc.isUploaded)
            .putString("doc_${doc.id}_date", doc.uploadDate)
            .putString("doc_${doc.id}_size", doc.fileSize)
            .putString("doc_${doc.id}_uri", doc.fileUri)
            .putString("doc_${doc.id}_name", doc.fileName)
            .apply()
    }

    fun clearDocFromPrefs(doc: VaultDocumentModel) {
        prefs.edit()
            .putBoolean("doc_${doc.id}_uploaded", false)
            .remove("doc_${doc.id}_date")
            .remove("doc_${doc.id}_size")
            .remove("doc_${doc.id}_uri")
            .remove("doc_${doc.id}_name")
            .apply()
    }

    // Sample Customer Details
    val displayCustomer = remember(customer) {
        customer ?: CustomerEntity(
            id = 101L,
            name = "Rajesh Kumar Sharma",
            mobile = "+91 98765 43210",
            whatsapp = "+91 98765 43210",
            address = "Sector 14, Gurgaon, Haryana",
            pan = "ABCDE1234F",
            aadhaar = "9876 5432 1098"
        )
    }

    val policyNumber = "867452901"
    val planName = "Jeevan Umang (Plan 945)"

    // Default 6 Required Document Categories with local persistence lookup
    val defaultDocuments = remember {
        listOf(
            VaultDocumentModel(
                id = 1L,
                title = "Aadhaar Card",
                category = "Identity Proof",
                isUploaded = prefs.getBoolean("doc_1_uploaded", true),
                uploadDate = prefs.getString("doc_1_date", "15 Aug 2025"),
                fileSize = prefs.getString("doc_1_size", "1.8 MB • PDF"),
                icon = Icons.Default.Badge,
                fileUri = prefs.getString("doc_1_uri", null),
                fileName = prefs.getString("doc_1_name", "aadhaar_card.pdf")
            ),
            VaultDocumentModel(
                id = 2L,
                title = "PAN Card",
                category = "Tax Identity",
                isUploaded = prefs.getBoolean("doc_2_uploaded", true),
                uploadDate = prefs.getString("doc_2_date", "10 Aug 2025"),
                fileSize = prefs.getString("doc_2_size", "850 KB • JPG"),
                icon = Icons.Default.CreditCard,
                fileUri = prefs.getString("doc_2_uri", null),
                fileName = prefs.getString("doc_2_name", "pan_card.jpg")
            ),
            VaultDocumentModel(
                id = 3L,
                title = "Policy Bond",
                category = "LIC Document",
                isUploaded = prefs.getBoolean("doc_3_uploaded", true),
                uploadDate = prefs.getString("doc_3_date", "02 Jan 2026"),
                fileSize = prefs.getString("doc_3_size", "3.2 MB • PDF"),
                icon = Icons.Default.Description,
                fileUri = prefs.getString("doc_3_uri", null),
                fileName = prefs.getString("doc_3_name", "policy_bond.pdf")
            ),
            VaultDocumentModel(
                id = 4L,
                title = "Nominee ID",
                category = "Nominee Proof",
                isUploaded = prefs.getBoolean("doc_4_uploaded", true),
                uploadDate = prefs.getString("doc_4_date", "18 Jan 2026"),
                fileSize = prefs.getString("doc_4_size", "1.1 MB • PDF"),
                icon = Icons.Default.AssignmentInd,
                fileUri = prefs.getString("doc_4_uri", null),
                fileName = prefs.getString("doc_4_name", "nominee_id.pdf")
            ),
            VaultDocumentModel(
                id = 5L,
                title = "Address Proof",
                category = "Residence Proof",
                isUploaded = prefs.getBoolean("doc_5_uploaded", false),
                uploadDate = prefs.getString("doc_5_date", null),
                fileSize = prefs.getString("doc_5_size", null),
                icon = Icons.Default.Home,
                fileUri = prefs.getString("doc_5_uri", null),
                fileName = prefs.getString("doc_5_name", null)
            ),
            VaultDocumentModel(
                id = 6L,
                title = "Other Documents",
                category = "Medical / Supporting",
                isUploaded = prefs.getBoolean("doc_6_uploaded", false),
                uploadDate = prefs.getString("doc_6_date", null),
                fileSize = prefs.getString("doc_6_size", null),
                icon = Icons.Default.InsertDriveFile,
                fileUri = prefs.getString("doc_6_uri", null),
                fileName = prefs.getString("doc_6_name", null)
            )
        )
    }

    var documentList by remember { mutableStateOf(defaultDocuments) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Uploaded", "Pending"

    // Dialog & Sheet States
    var previewDocument by remember { mutableStateOf<VaultDocumentModel?>(null) }
    var replaceDocument by remember { mutableStateOf<VaultDocumentModel?>(null) }
    var deleteDocument by remember { mutableStateOf<VaultDocumentModel?>(null) }
    var showUploadModal by remember { mutableStateOf(false) }
    var showSourceBottomSheet by remember { mutableStateOf(false) }
    var pendingDocToUpload by remember { mutableStateOf<VaultDocumentModel?>(null) }

    // File Processing logic
    val processSelectedFile: (Uri, String) -> Unit = { uri, sourceType ->
        val target = pendingDocToUpload
        if (target != null) {
            val uriStr = uri.toString()
            val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

            var displayName: String? = null
            var calculatedSizeStr = if (sourceType == "PDF") "1.5 MB • PDF" else "950 KB • JPG"

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                        if (sizeIdx != -1) {
                            val bytes = cursor.getLong(sizeIdx)
                            if (bytes > 0) {
                                val kb = bytes / 1024
                                val mb = kb / 1024.0
                                calculatedSizeStr = if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb) else "$kb KB"
                                calculatedSizeStr += if (sourceType == "PDF") " • PDF" else " • JPG"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query exceptions
            }

            val finalFileName = displayName ?: "${target.title.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.${if (sourceType == "PDF") "pdf" else "jpg"}"

            val updatedDoc = target.copy(
                isUploaded = true,
                uploadDate = formattedDate,
                fileSize = calculatedSizeStr,
                fileUri = uriStr,
                fileName = finalFileName
            )

            val exists = documentList.any { it.id == target.id }
            documentList = if (exists) {
                documentList.map { if (it.id == target.id) updatedDoc else it }
            } else {
                documentList + updatedDoc
            }

            saveDocToPrefs(updatedDoc)

            showSourceBottomSheet = false
            pendingDocToUpload = null

            coroutineScope.launch {
                snackbarHostState.showSnackbar("Document uploaded successfully.")
            }
        }
    }

    // Launchers for Camera, Photo Picker, and PDF Document Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { processSelectedFile(it, "IMAGE") }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processSelectedFile(it, "PDF") }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val file = File(context.cacheDir, "camera_doc_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                processSelectedFile(Uri.fromFile(file), "CAMERA")
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to capture camera photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Screen Entrance Animation State
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val filteredList = remember(documentList, selectedFilter) {
        when (selectedFilter) {
            "Uploaded" -> documentList.filter { it.isUploaded }
            "Pending" -> documentList.filter { !it.isUploaded }
            else -> documentList
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = RoyalBluePrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Document Vault",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 20.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextWhite
                )
            )
        },
        bottomBar = {
            // BOTTOM STICKY BUTTON: Upload New Document
            Surface(
                color = DarkBackground,
                border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                ) {
                    Button(
                        onClick = { showUploadModal = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(16.dp, shape = RoundedCornerShape(28.dp), ambientColor = RoyalBlueGlow, spotColor = RoyalBlueGlow)
                            .testTag("upload_new_document_button"),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(RoyalBluePrimary, RoyalBlueLight)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Upload New Document",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = tween(400)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ==================== 1. CUSTOMER CARD ====================
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Circular Customer Avatar
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(RoyalBlueLight, RoyalBluePrimary)
                                        )
                                    )
                                    .border(2.dp, RoyalBlueLight.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val initials = remember(displayCustomer.name) {
                                    displayCustomer.name.split(" ")
                                        .take(2)
                                        .mapNotNull { it.firstOrNull()?.uppercase() }
                                        .joinToString("")
                                        .ifEmpty { "RK" }
                                }
                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayCustomer.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextWhite,
                                        fontSize = 19.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = planName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = RoyalBlueLight,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "Policy #: $policyNumber",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            // Active status badge
                            Surface(
                                color = EmeraldGreenContainer,
                                shape = RoundedCornerShape(50.dp),
                                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = EmeraldGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val uploadedCount = documentList.count { it.isUploaded }
                            val pendingCount = documentList.size - uploadedCount

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("$uploadedCount Uploaded", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PendingActions, contentDescription = null, tint = AmberDue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("$pendingCount Pending", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${documentList.size} Total", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Filter Tabs (All, Uploaded, Pending)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Uploaded", "Pending").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            onClick = { selectedFilter = filter },
                            shape = RoundedCornerShape(50.dp),
                            color = if (isSelected) RoyalBluePrimary else DarkCardSurface,
                            border = BorderStroke(1.dp, if (isSelected) RoyalBlueLight else BorderSlate),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // ==================== 2. DOCUMENT CARDS / EMPTY STATE ====================
                if (filteredList.isEmpty()) {
                    // EMPTY STATE ILLUSTRATION
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(DarkCardSurfaceVariant)
                                    .border(1.dp, BorderSlate, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOff,
                                    contentDescription = null,
                                    tint = RoyalBlueLight,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "No Documents Found",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "No document matches the selected filter ($selectedFilter). Tap below to upload customer KYC and policy documents to the vault.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedButton(
                                onClick = {
                                    selectedFilter = "All"
                                    showUploadModal = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RoyalBlueLight)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Document", color = RoyalBlueLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    filteredList.forEach { doc ->
                        DocumentVaultCard(
                            document = doc,
                            onPreview = { previewDocument = doc },
                            onReplace = {
                                pendingDocToUpload = doc
                                showSourceBottomSheet = true
                            },
                            onDelete = { deleteDocument = doc }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ==================== INTERACTIVE MODALS & DIALOGS ====================

    // 1. Preview Document Dialog
    previewDocument?.let { doc ->
        AlertDialog(
            onDismissRequest = { previewDocument = null },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(doc.icon, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = DarkCardSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (!doc.fileUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = doc.fileUri,
                                    contentDescription = doc.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = doc.icon,
                                        contentDescription = null,
                                        tint = RoyalBlueLight,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = doc.fileName ?: doc.title,
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = doc.fileSize ?: "Encrypted Digital Document",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailItem("Category", doc.category)
                        DetailItem("File Name", doc.fileName ?: "${doc.title.lowercase().replace(" ", "_")}.pdf")
                        DetailItem("Status", if (doc.isUploaded) "Uploaded & Verified" else "Pending Upload")
                        DetailItem("Uploaded On", doc.uploadDate ?: "N/A")
                        DetailItem("Security", "AES-256 Vault Encryption")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { previewDocument = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close Preview", color = Color.White)
                }
            }
        )
    }

    // 2. Replace Document Dialog
    replaceDocument?.let { doc ->
        AlertDialog(
            onDismissRequest = { replaceDocument = null },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PublishedWithChanges, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Replace Document", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Upload a new file from Camera, Gallery, or PDF to replace '${doc.title}'.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = doc
                        replaceDocument = null
                        pendingDocToUpload = target
                        showSourceBottomSheet = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Select & Replace", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { replaceDocument = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // 3. Delete Document Dialog
    deleteDocument?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteDocument = null },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonOverdue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove Document?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Are you sure you want to remove '${doc.title}' from the vault? You can upload it again anytime.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = doc.copy(
                            isUploaded = false,
                            uploadDate = null,
                            fileSize = null,
                            fileUri = null,
                            fileName = null
                        )
                        documentList = documentList.map {
                            if (it.id == doc.id) updated else it
                        }
                        clearDocFromPrefs(doc)
                        deleteDocument = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Document removed from vault.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonOverdue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deleteDocument = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // 4. Upload New Document Category Selection Modal
    if (showUploadModal) {
        AlertDialog(
            onDismissRequest = { showUploadModal = false },
            containerColor = DarkCardSurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = RoyalBlueLight)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Upload New Document", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Select document type to upload to ${displayCustomer.name}'s secure Vault:",
                        color = TextMuted,
                        fontSize = 13.sp
                    )

                    listOf("Aadhaar Card", "PAN Card", "Policy Bond", "Nominee ID", "Address Proof", "Other Documents").forEach { typeName ->
                        Surface(
                            onClick = {
                                val existing = documentList.find { it.title == typeName }
                                val target = existing ?: VaultDocumentModel(
                                    id = System.currentTimeMillis(),
                                    title = typeName,
                                    category = "KYC Proof",
                                    isUploaded = false,
                                    uploadDate = null,
                                    fileSize = null,
                                    icon = Icons.Default.InsertDriveFile
                                )
                                showUploadModal = false
                                pendingDocToUpload = target
                                showSourceBottomSheet = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = DarkCardSurfaceVariant,
                            border = BorderStroke(1.dp, BorderSlate),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = RoyalBlueLight, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(typeName, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { showUploadModal = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", color = TextMuted)
                }
            }
        )
    }

    // 5. Material 3 Bottom Sheet for Camera / Gallery / PDF File Source Selection
    if (showSourceBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSourceBottomSheet = false
                pendingDocToUpload = null
            },
            containerColor = DarkCardSurface,
            scrimColor = Color.Black.copy(alpha = 0.6f),
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = RoyalBlueLight,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Upload ${pendingDocToUpload?.title ?: "Document"}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Choose source to upload customer document",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                HorizontalDivider(color = BorderSlate.copy(alpha = 0.6f))

                // Option 1: Camera
                Surface(
                    onClick = {
                        cameraLauncher.launch(null)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = DarkCardSurfaceVariant,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("source_camera_button")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(RoyalBluePrimary.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera",
                                tint = RoyalBlueLight,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Camera",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = "Capture document via camera",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                // Option 2: Gallery (Android Photo Picker)
                Surface(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = DarkCardSurfaceVariant,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("source_gallery_button")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Gallery",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = "Pick photo via Android Photo Picker",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                // Option 3: PDF File (Android Document Picker)
                Surface(
                    onClick = {
                        pdfPickerLauncher.launch("application/pdf")
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = DarkCardSurfaceVariant,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("source_pdf_button")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AmberDue.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF File",
                                tint = AmberDue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "PDF File",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = "Choose document using Android Document Picker",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// ==================== DOCUMENT CARD SUB-COMPONENT ====================

@Composable
private fun DocumentVaultCard(
    document: VaultDocumentModel,
    onPreview: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Large Icon + Title/Category + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large Document Icon Box
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (document.isUploaded) RoyalBluePrimary.copy(alpha = 0.2f)
                            else BorderSlate.copy(alpha = 0.3f)
                        )
                        .border(
                            1.dp,
                            if (document.isUploaded) RoyalBlueLight.copy(alpha = 0.4f) else BorderSlate,
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = document.icon,
                        contentDescription = document.title,
                        tint = if (document.isUploaded) RoyalBlueLight else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 16.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = document.category,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    )

                    if (document.isUploaded && document.uploadDate != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Uploaded: ${document.uploadDate} ${document.fileSize?.let { "• $it" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = EmeraldGreen,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Upload Status Badge
                Surface(
                    color = if (document.isUploaded) EmeraldGreenContainer else AmberDueContainer,
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(
                        1.dp,
                        if (document.isUploaded) EmeraldGreen.copy(alpha = 0.5f) else AmberDue.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (document.isUploaded) EmeraldGreen else AmberDue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (document.isUploaded) "Uploaded" else "Not Uploaded",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (document.isUploaded) EmeraldGreen else AmberDue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderSlate.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Equal Heights (44dp) - Preview, Upload (Primary Royal Blue), Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Preview Button
                OutlinedButton(
                    onClick = onPreview,
                    enabled = document.isUploaded,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (document.isUploaded) RoyalBlueLight else BorderSlate.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = TextMuted.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Preview",
                            tint = if (document.isUploaded) RoyalBlueLight else TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (document.isUploaded) RoyalBlueLight else TextMuted.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // 2. Upload Button (Primary Royal Blue Button)
                Button(
                    onClick = onReplace,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalBluePrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Upload",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Upload",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // 3. Delete Button
                OutlinedButton(
                    onClick = onDelete,
                    enabled = document.isUploaded,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (document.isUploaded) CrimsonOverdue else BorderSlate.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = TextMuted.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = if (document.isUploaded) CrimsonOverdue else TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (document.isUploaded) CrimsonOverdue else TextMuted.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}
