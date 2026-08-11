package com.example.ui.documents

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.local.CustomerEntity
import com.example.data.local.DocumentEntity
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.Locale

private val TextMuted = Color(0xFF64748B)

val SUPPORTED_DOC_TYPES = listOf(
    "Customer Photo",
    "Aadhaar Card",
    "PAN Card",
    "Policy Bond",
    "Proposal Form",
    "Other Document"
)

data class SavedFileInfo(
    val fileUri: String,
    val fileName: String,
    val fileSize: String,
    val mimeType: String
)

fun saveUriToInternalVault(context: Context, uri: Uri, defaultDocType: String): SavedFileInfo? {
    return try {
        val resolver = context.contentResolver
        var displayName = ""
        var sizeBytes = 0L
        var mimeType = resolver.getType(uri) ?: "*/*"

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: ""
                if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
            }
        }

        if (displayName.isBlank()) {
            val extension = when {
                mimeType.contains("pdf", ignoreCase = true) -> "pdf"
                mimeType.contains("png", ignoreCase = true) -> "png"
                else -> "jpg"
            }
            displayName = "${defaultDocType.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.$extension"
        }

        val vaultDir = File(context.filesDir, "vault_documents").apply { if (!exists()) mkdirs() }
        val sanitizedFileName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val savedFile = File(vaultDir, "doc_${System.currentTimeMillis()}_$sanitizedFileName")

        resolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(savedFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        val actualLength = if (savedFile.length() > 0) savedFile.length() else sizeBytes
        val formattedSize = if (actualLength > 1024 * 1024) {
            String.format(Locale.US, "%.1f MB", actualLength / (1024.0 * 1024.0))
        } else {
            "${(actualLength / 1024).coerceAtLeast(1)} KB"
        }

        SavedFileInfo(
            fileUri = savedFile.absolutePath,
            fileName = displayName,
            fileSize = formattedSize,
            mimeType = mimeType
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun saveBitmapToInternalVault(context: Context, bitmap: Bitmap, defaultDocType: String): SavedFileInfo? {
    return try {
        val vaultDir = File(context.filesDir, "vault_documents").apply { if (!exists()) mkdirs() }
        val fileName = "${defaultDocType.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.jpg"
        val savedFile = File(vaultDir, "doc_${System.currentTimeMillis()}_$fileName")

        FileOutputStream(savedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val formattedSize = if (savedFile.length() > 1024 * 1024) {
            String.format(Locale.US, "%.1f MB", savedFile.length() / (1024.0 * 1024.0))
        } else {
            "${(savedFile.length() / 1024).coerceAtLeast(1)} KB"
        }

        SavedFileInfo(
            fileUri = savedFile.absolutePath,
            fileName = fileName,
            fileSize = formattedSize,
            mimeType = "image/jpeg"
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun openOrShareDocument(context: Context, doc: DocumentEntity) {
    try {
        val file = File(doc.fileUri)
        if (file.exists()) {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "pdf" -> "application/pdf"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> context.contentResolver.getType(contentUri) ?: "*/*"
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(viewIntent, "Open ${doc.title}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } else if (doc.fileUri.startsWith("content://")) {
            val uri = Uri.parse(doc.fileUri)
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(viewIntent, "Open ${doc.title}"))
        } else {
            Toast.makeText(context, "File reference not found: ${doc.title}", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file viewer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun downloadDocument(context: Context, doc: DocumentEntity) {
    openOrShareDocument(context, doc)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    viewModel: LicViewModel,
    initialCustomer: CustomerEntity? = null
) {
    val documents by viewModel.documents.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    var selectedDocTypeFilter by remember { mutableStateOf("ALL") }
    var selectedCustomerFilter by remember { mutableStateOf<CustomerEntity?>(initialCustomer) }

    var showAddDialog by remember { mutableStateOf(false) }
    var preselectedCustomerForAdd by remember { mutableStateOf<CustomerEntity?>(null) }
    var documentToPreview by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToReplace by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    LaunchedEffect(initialCustomer) {
        if (initialCustomer != null) {
            selectedCustomerFilter = initialCustomer
        }
    }

    // Filtered Documents
    val filteredDocs = remember(documents, customers, policies, searchQuery, selectedDocTypeFilter, selectedCustomerFilter) {
        documents.filter { doc ->
            val cust = customers.find { it.id == doc.customerId }
            val custPolicies = if (cust != null) policies.filter { it.customerId == cust.id } else emptyList()

            val matchesSearch = searchQuery.isBlank() ||
                    doc.title.contains(searchQuery, ignoreCase = true) ||
                    doc.docType.contains(searchQuery, ignoreCase = true) ||
                    doc.customerName.contains(searchQuery, ignoreCase = true) ||
                    (cust != null && cust.name.contains(searchQuery, ignoreCase = true)) ||
                    (cust != null && cust.mobile.contains(searchQuery, ignoreCase = true)) ||
                    (cust != null && cust.aadhaar.contains(searchQuery, ignoreCase = true)) ||
                    (cust != null && cust.pan.contains(searchQuery, ignoreCase = true)) ||
                    custPolicies.any { it.policyNumber.contains(searchQuery, ignoreCase = true) } ||
                    doc.fileUri.contains(searchQuery, ignoreCase = true)

            val matchesType = selectedDocTypeFilter == "ALL" || doc.docType.equals(selectedDocTypeFilter, ignoreCase = true)

            val matchesCustomer = selectedCustomerFilter == null ||
                    doc.customerId == selectedCustomerFilter?.id ||
                    doc.customerName.equals(selectedCustomerFilter?.name, ignoreCase = true)

            matchesSearch && matchesType && matchesCustomer
        }
    }

    val displayCustomers = remember(customers, filteredDocs, searchQuery, selectedDocTypeFilter) {
        if (searchQuery.isNotBlank() || selectedDocTypeFilter != "ALL") {
            val matchingCustIds = filteredDocs.mapNotNull { it.customerId }.toSet()
            val matchingCustNames = filteredDocs.map { it.customerName }.toSet()
            customers.filter { c -> c.id in matchingCustIds || c.name in matchingCustNames }
        } else {
            customers
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Single Full-Screen Scroll Container
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            item {
                Surface(
                    color = RoyalBluePrimary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "KYC & Policy Document Vault",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${documents.size} Secured Digital Files stored",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFFFEDD5),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = {
                        Text(
                            "Search client, mobile, policy # or doc name...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = RoyalBluePrimary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("document_search_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalBluePrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // Document Category Filter Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Document Category:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = selectedDocTypeFilter == "ALL",
                                onClick = { selectedDocTypeFilter = "ALL" },
                                label = { Text("All Vault Docs", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (selectedDocTypeFilter == "ALL") {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                        items(SUPPORTED_DOC_TYPES) { type ->
                            FilterChip(
                                selected = selectedDocTypeFilter == type,
                                onClick = { selectedDocTypeFilter = type },
                                label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (selectedDocTypeFilter == type) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Client Filter Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filter by Client:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (selectedCustomerFilter != null) {
                            TextButton(
                                onClick = { selectedCustomerFilter = null },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Show All Clients", fontSize = 12.sp, color = RoyalBluePrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCustomerFilter == null,
                                onClick = { selectedCustomerFilter = null },
                                label = { Text("All Clients (${customers.size})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        items(customers) { cust ->
                            val docCount = documents.count { it.customerId == cust.id || it.customerName.equals(cust.name, ignoreCase = true) }
                            FilterChip(
                                selected = selectedCustomerFilter?.id == cust.id,
                                onClick = { selectedCustomerFilter = cust },
                                label = { Text("${cust.name} ($docCount)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // Client-Wise Grouped Documents
            if (selectedCustomerFilter != null) {
                // Single Client Selected View
                val cust = selectedCustomerFilter!!
                val clientDocs = filteredDocs.filter { it.customerId == cust.id || it.customerName.equals(cust.name, ignoreCase = true) }

                item {
                    ClientHeaderBanner(
                        customer = cust,
                        docCount = clientDocs.size,
                        onUploadForClient = {
                            preselectedCustomerForAdd = cust
                            showAddDialog = true
                        }
                    )
                }

                if (clientDocs.isEmpty()) {
                    item {
                        EmptyClientDocBox(
                            customerName = cust.name,
                            onUploadClick = {
                                preselectedCustomerForAdd = cust
                                showAddDialog = true
                            }
                        )
                    }
                } else {
                    items(clientDocs, key = { it.id }) { doc ->
                        DocumentCardItem(
                            doc = doc,
                            onPreview = { documentToPreview = doc },
                            onReplace = { documentToReplace = doc },
                            onDelete = { documentToDelete = doc },
                            onDownload = { downloadDocument(context, doc) }
                        )
                    }
                }
            } else {
                // All Clients View - Grouped by Client
                if (displayCustomers.isEmpty() && filteredDocs.isEmpty()) {
                    item {
                        StandardEmptyState(
                            title = "No Documents Found",
                            description = "No documents match your search or filter. Use the circular button below to upload documents.",
                            icon = Icons.Outlined.FolderOff,
                            actionLabel = "Upload Document",
                            onActionClick = {
                                preselectedCustomerForAdd = customers.firstOrNull()
                                showAddDialog = true
                            }
                        )
                    }
                } else {
                    for (cust in displayCustomers) {
                        val custDocs = filteredDocs.filter { it.customerId == cust.id || it.customerName.equals(cust.name, ignoreCase = true) }

                        item(key = "client_header_${cust.id}") {
                            ClientHeaderBanner(
                                customer = cust,
                                docCount = custDocs.size,
                                onUploadForClient = {
                                    preselectedCustomerForAdd = cust
                                    showAddDialog = true
                                }
                            )
                        }

                        if (custDocs.isEmpty()) {
                            item(key = "client_empty_${cust.id}") {
                                EmptyClientDocBox(
                                    customerName = cust.name,
                                    onUploadClick = {
                                        preselectedCustomerForAdd = cust
                                        showAddDialog = true
                                    }
                                )
                            }
                        } else {
                            items(custDocs, key = { "doc_${it.id}" }) { doc ->
                                DocumentCardItem(
                                    doc = doc,
                                    onPreview = { documentToPreview = doc },
                                    onReplace = { documentToReplace = doc },
                                    onDelete = { documentToDelete = doc },
                                    onDownload = { downloadDocument(context, doc) }
                                )
                            }
                        }

                        item(key = "client_spacer_${cust.id}") {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // Any unassigned documents
                    val unassignedDocs = filteredDocs.filter { doc ->
                        doc.customerId == null && displayCustomers.none { it.name.equals(doc.customerName, ignoreCase = true) }
                    }

                    if (unassignedDocs.isNotEmpty()) {
                        item {
                            Text(
                                text = "GENERAL VAULT DOCUMENTS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBluePrimary
                                )
                            )
                        }
                        items(unassignedDocs, key = { "unassigned_${it.id}" }) { doc ->
                            DocumentCardItem(
                                doc = doc,
                                onPreview = { documentToPreview = doc },
                                onReplace = { documentToReplace = doc },
                                onDelete = { documentToDelete = doc },
                                onDownload = { downloadDocument(context, doc) }
                            )
                        }
                    }
                }
            }
        }

        // Small Circular FAB at Bottom-Right
        FloatingActionButton(
            onClick = {
                preselectedCustomerForAdd = selectedCustomerFilter ?: customers.firstOrNull()
                showAddDialog = true
            },
            shape = CircleShape,
            containerColor = AccentOrange,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
                .size(56.dp)
                .testTag("fab_upload_doc")
        ) {
            Icon(
                imageVector = Icons.Default.UploadFile,
                contentDescription = "Upload Document",
                modifier = Modifier.size(26.dp)
            )
        }
    }

    // Modal Dialogs
    if (showAddDialog) {
        AddDocumentModal(
            customers = customers,
            initialSelectedCustomer = preselectedCustomerForAdd ?: selectedCustomerFilter ?: customers.firstOrNull(),
            onDismiss = { showAddDialog = false },
            onSave = { doc ->
                viewModel.addDocument(doc)
                showAddDialog = false
                Toast.makeText(context, "Document saved for ${doc.customerName}!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    documentToPreview?.let { doc ->
        DocumentPreviewModal(
            doc = doc,
            onDismiss = { documentToPreview = null },
            onReplace = {
                documentToReplace = doc
                documentToPreview = null
            },
            onDelete = {
                documentToDelete = doc
                documentToPreview = null
            },
            onDownload = { downloadDocument(context, doc) }
        )
    }

    documentToReplace?.let { doc ->
        ReplaceDocumentModal(
            existingDoc = doc,
            onDismiss = { documentToReplace = null },
            onReplaceComplete = { updatedDoc ->
                viewModel.updateDocument(updatedDoc)
                documentToReplace = null
                Toast.makeText(context, "Document updated in Vault!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = {
                Text(
                    "Delete Document from Vault?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text("Are you sure you want to permanently delete '${doc.title}' (${doc.docType}) for client ${doc.customerName}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc)
                        try {
                            if (doc.fileUri.isNotBlank()) {
                                val file = File(doc.fileUri)
                                if (file.exists()) file.delete()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        documentToDelete = null
                        Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_delete_doc_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DocumentCardItem(
    doc: DocumentEntity,
    onPreview: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    val imageFile = remember(doc.fileUri) {
        if (doc.fileUri.isNotBlank()) {
            try { File(doc.fileUri) } catch (e: Exception) { null }
        } else null
    }
    val isImage = remember(doc.docType, doc.fileUri, doc.title) {
        doc.docType == "Customer Photo" ||
        doc.title.endsWith(".jpg", ignoreCase = true) ||
        doc.title.endsWith(".jpeg", ignoreCase = true) ||
        doc.title.endsWith(".png", ignoreCase = true) ||
        doc.fileUri.endsWith(".jpg", ignoreCase = true) ||
        doc.fileUri.endsWith(".png", ignoreCase = true)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clickable { onPreview() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon or Image Thumbnail
                if (isImage && imageFile != null && imageFile.exists()) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = doc.title,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (doc.docType) {
                            "Customer Photo" -> AccentOrangeContainer.copy(alpha = 0.2f)
                            "Policy Bond" -> RoyalBlueContainer
                            "Aadhaar Card", "PAN Card" -> EmeraldGreenContainer.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (doc.docType) {
                                    "Customer Photo" -> Icons.Default.AccountCircle
                                    "Aadhaar Card" -> Icons.Default.Badge
                                    "PAN Card" -> Icons.Default.CreditCard
                                    "Policy Bond" -> Icons.Default.Description
                                    "Proposal Form" -> Icons.Default.Assignment
                                    else -> Icons.Default.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = when (doc.docType) {
                                    "Customer Photo" -> AccentOrange
                                    "Policy Bond" -> RoyalBluePrimary
                                    "Aadhaar Card", "PAN Card" -> EmeraldGreenSecondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Category
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.docType,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Footer row with Client name, Upload Date, File size & Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Info Column (Client, Date, Size)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (doc.customerName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = RoyalBluePrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = doc.customerName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBluePrimary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (doc.uploadDate.isNotBlank()) doc.uploadDate else "Recent",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            ),
                            softWrap = false,
                            maxLines = 1
                        )
                        Text("•", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            text = if (doc.fileSize.isNotBlank()) doc.fileSize else "File",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            ),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }

                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open / Download",
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onReplace,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "Restore / Replace",
                            tint = AccentOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientHeaderBanner(
    customer: CustomerEntity,
    docCount: Int,
    onUploadForClient: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RoyalBluePrimary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = RoyalBluePrimary,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = customer.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "CLIENT: ${customer.name}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Mobile: ${customer.mobile.ifBlank { "N/A" }} • Documents ($docCount)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = onUploadForClient,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add document",
                    tint = RoyalBluePrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyClientDocBox(
    customerName: String,
    onUploadClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUploadClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FileUpload,
                contentDescription = null,
                tint = RoyalBluePrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "0 Documents for $customerName — Tap to upload",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = RoyalBluePrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
fun AddDocumentModal(
    customers: List<CustomerEntity>,
    initialSelectedCustomer: CustomerEntity? = null,
    onDismiss: () -> Unit,
    onSave: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current

    var selectedCustomer by remember { mutableStateOf(initialSelectedCustomer ?: customers.firstOrNull()) }
    var docType by remember { mutableStateOf("Aadhaar Card") }
    var savedFileInfo by remember { mutableStateOf<SavedFileInfo?>(null) }
    var uploadSourceText by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val info = saveBitmapToInternalVault(context, bitmap, docType)
            if (info != null) {
                savedFileInfo = info
                uploadSourceText = "Camera Capture"
                if (customTitle.isBlank()) customTitle = info.fileName
            } else {
                Toast.makeText(context, "Failed to save camera photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val info = saveUriToInternalVault(context, uri, docType)
            if (info != null) {
                savedFileInfo = info
                uploadSourceText = "Gallery / Photos"
                if (customTitle.isBlank()) customTitle = info.fileName
            } else {
                Toast.makeText(context, "Failed to read gallery photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val info = saveUriToInternalVault(context, uri, docType)
            if (info != null) {
                savedFileInfo = info
                uploadSourceText = "File Picker"
                if (customTitle.isBlank()) customTitle = info.fileName
            } else {
                Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.UploadFile, contentDescription = null, tint = RoyalBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Document to Vault", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. SELECT CLIENT
                Text("1. Select Client *", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                if (customers.isEmpty()) {
                    Text("No clients found. Please add a client first.", color = ErrorRed, fontSize = 12.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(customers) { cust ->
                            FilterChip(
                                selected = selectedCustomer?.id == cust.id,
                                onClick = { selectedCustomer = cust },
                                label = { Text(cust.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (selectedCustomer?.id == cust.id) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // 2. DOCUMENT TYPE
                Text("2. Document Category *", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SUPPORTED_DOC_TYPES) { type ->
                        FilterChip(
                            selected = docType == type,
                            onClick = { docType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (docType == type) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                // 3. SOURCE SELECTOR
                Text("3. Attach File / Photo *", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera", fontSize = 11.sp, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery", fontSize = 11.sp, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Files", fontSize = 11.sp, softWrap = false)
                    }
                }

                // DISPLAY FILE DETAILS IMMEDIATELY AFTER SELECTION
                savedFileInfo?.let { info ->
                    Surface(
                        color = EmeraldGreenContainer.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EmeraldGreenSecondary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("File Attached ($uploadSourceText)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                            }
                            HorizontalDivider(color = EmeraldGreenSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            DocDetailRow("File Name", info.fileName)
                            DocDetailRow("Type", if (info.mimeType.contains("pdf", ignoreCase = true)) "PDF Document" else "Image / Photo")
                            DocDetailRow("Size", info.fileSize)
                            DocDetailRow("Client", selectedCustomer?.name ?: "None")
                            DocDetailRow("Category", docType)
                        }
                    }
                }

                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Display Title / File Name") },
                    placeholder = { Text("e.g. Sagarika_Aadhaar.pdf") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_doc_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cust = selectedCustomer
                    if (cust == null) {
                        Toast.makeText(context, "Please select a client first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val fileInfo = savedFileInfo
                    if (fileInfo == null) {
                        Toast.makeText(context, "Please attach a file or take a photo first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val finalTitle = customTitle.ifBlank { fileInfo.fileName }
                    val newDoc = DocumentEntity(
                        customerId = cust.id,
                        customerName = cust.name,
                        docType = docType,
                        title = finalTitle,
                        fileUri = fileInfo.fileUri,
                        fileSize = fileInfo.fileSize,
                        uploadDate = LocalDate.now().toString(),
                        createdAt = System.currentTimeMillis()
                    )
                    onSave(newDoc)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_doc_button")
            ) {
                Text("Save Document", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DocDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun DocumentPreviewModal(
    doc: DocumentEntity,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    val file = remember(doc.fileUri) { if (doc.fileUri.isNotBlank()) File(doc.fileUri) else null }
    val isImage = remember(doc.docType, doc.fileUri, doc.title) {
        doc.docType == "Customer Photo" ||
        doc.title.endsWith(".jpg", ignoreCase = true) ||
        doc.title.endsWith(".jpeg", ignoreCase = true) ||
        doc.title.endsWith(".png", ignoreCase = true) ||
        doc.fileUri.endsWith(".jpg", ignoreCase = true) ||
        doc.fileUri.endsWith(".png", ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = RoyalBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Document Vault Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (isImage && file != null && file.exists()) {
                            AsyncImage(
                                model = file,
                                contentDescription = doc.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = when (doc.docType) {
                                        "Customer Photo" -> Icons.Default.AccountCircle
                                        "Aadhaar Card" -> Icons.Default.Badge
                                        "PAN Card" -> Icons.Default.CreditCard
                                        "Policy Bond" -> Icons.Default.Description
                                        else -> Icons.Default.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    tint = RoyalBluePrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(doc.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Secured File: ${doc.fileUri.takeLast(25)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                DocDetailItem("Document Type", doc.docType)
                DocDetailItem("Client Name", doc.customerName.ifEmpty { "N/A" })
                DocDetailItem("Upload Date", doc.uploadDate)
                DocDetailItem("File Size", doc.fileSize)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReplace, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replace")
                }
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open File")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = ErrorRed)
            }
        }
    )
}

@Composable
fun ReplaceDocumentModal(
    existingDoc: DocumentEntity,
    onDismiss: () -> Unit,
    onReplaceComplete: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    var savedFileInfo by remember { mutableStateOf<SavedFileInfo?>(null) }
    var sourceText by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val info = saveBitmapToInternalVault(context, bitmap, existingDoc.docType)
            if (info != null) {
                savedFileInfo = info
                sourceText = "Camera Capture"
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val info = saveUriToInternalVault(context, uri, existingDoc.docType)
            if (info != null) {
                savedFileInfo = info
                sourceText = "Gallery / Files"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace '${existingDoc.docType}'", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Target File: ${existingDoc.title}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                Text("Choose new document source to overwrite vault reference:", style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera", fontSize = 11.sp, softWrap = false)
                    }

                    Button(
                        onClick = { galleryLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery/Files", fontSize = 11.sp, softWrap = false)
                    }
                }

                savedFileInfo?.let { info ->
                    Text("New File Selected via $sourceText (${info.fileSize})", style = MaterialTheme.typography.labelSmall.copy(color = EmeraldGreenSecondary, fontWeight = FontWeight.Bold))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val info = savedFileInfo
                    if (info != null) {
                        val updatedDoc = existingDoc.copy(
                            fileUri = info.fileUri,
                            fileSize = info.fileSize,
                            uploadDate = LocalDate.now().toString()
                        )
                        onReplaceComplete(updatedDoc)
                    } else {
                        Toast.makeText(context, "Please capture or select a new file first", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm Replace", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DocDetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}
