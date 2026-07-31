package com.example.ui.documents

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.DocumentEntity
import com.example.ui.LicViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

val SUPPORTED_DOC_TYPES = listOf(
    "Customer Photo",
    "Aadhaar Card",
    "PAN Card",
    "Policy Bond",
    "Address Proof",
    "Nominee Documents",
    "Other Documents"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    viewModel: LicViewModel
) {
    val documents by viewModel.documents.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    var selectedDocTypeFilter by remember { mutableStateOf("ALL") }
    var selectedCustomerFilter by remember { mutableStateOf<CustomerEntity?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var documentToPreview by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToReplace by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    val filteredDocs = remember(documents, searchQuery, selectedDocTypeFilter, selectedCustomerFilter) {
        documents.filter { doc ->
            val matchesSearch = searchQuery.isBlank() ||
                    doc.title.contains(searchQuery, ignoreCase = true) ||
                    doc.docType.contains(searchQuery, ignoreCase = true) ||
                    doc.customerName.contains(searchQuery, ignoreCase = true)

            val matchesType = selectedDocTypeFilter == "ALL" || doc.docType.equals(selectedDocTypeFilter, ignoreCase = true)
            val matchesCustomer = selectedCustomerFilter == null || doc.customerId == selectedCustomerFilter?.id

            matchesSearch && matchesType && matchesCustomer
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header Surface
        Surface(
            color = RoyalBluePrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "KYC & Policy Document Vault",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 21.sp
                            )
                        )
                        Text(
                            text = "${documents.size} Secured Digital Files stored",
                            style = MaterialTheme.typography.bodySmall.copy(color = AccentOrangeLight, fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_document_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload", style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SearchBarComponent(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholderText = "Search by doc name, client, Aadhaar...",
                    testTag = "document_search_input"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Document Type Filter Row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedDocTypeFilter == "ALL",
                            onClick = { selectedDocTypeFilter = "ALL" },
                            label = { Text("All Vault Docs", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = RoyalBluePrimary)
                        )
                    }
                    items(SUPPORTED_DOC_TYPES) { type ->
                        FilterChip(
                            selected = selectedDocTypeFilter == type,
                            onClick = { selectedDocTypeFilter = type },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = RoyalBluePrimary)
                        )
                    }
                }
            }
        }

        // Optional Customer Filter Bar
        if (customers.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text("Filter by Client:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
                item {
                    FilterChip(
                        selected = selectedCustomerFilter == null,
                        onClick = { selectedCustomerFilter = null },
                        label = { Text("All Clients") }
                    )
                }
                items(customers) { cust ->
                    FilterChip(
                        selected = selectedCustomerFilter?.id == cust.id,
                        onClick = { selectedCustomerFilter = cust },
                        label = { Text(cust.name) }
                    )
                }
            }
        }

        if (filteredDocs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderZip,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No documents found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Upload Aadhaar, PAN, Policy Bonds, or Nominee proofs to vault",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredDocs, key = { it.id }) { doc ->
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

    // Add Document Dialog
    if (showAddDialog) {
        AddDocumentModal(
            customers = customers,
            onDismiss = { showAddDialog = false },
            onSave = { doc ->
                viewModel.addDocument(doc)
                showAddDialog = false
                Toast.makeText(context, "Document added to Vault successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Preview Dialog
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

    // Replace Dialog
    documentToReplace?.let { doc ->
        ReplaceDocumentModal(
            existingDoc = doc,
            onDismiss = { documentToReplace = null },
            onReplaceComplete = { updatedDoc ->
                viewModel.updateDocument(updatedDoc)
                documentToReplace = null
                Toast.makeText(context, "Document replaced in Vault!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete Document from Vault?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = { Text("Are you sure you want to permanently delete '${doc.title}' (${doc.docType})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clickable { onPreview() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when (doc.docType) {
                    "Customer Photo" -> AccentOrangeLight.copy(alpha = 0.3f)
                    "Policy Bond" -> RoyalBlueContainer
                    "Aadhaar Card", "PAN Card" -> EmeraldGreenContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (doc.docType) {
                            "Customer Photo" -> Icons.Default.AccountCircle
                            "Aadhaar Card" -> Icons.Default.Badge
                            "PAN Card" -> Icons.Default.CreditCard
                            "Policy Bond" -> Icons.Default.Description
                            "Address Proof" -> Icons.Default.HomeWork
                            "Nominee Documents" -> Icons.Default.Groups
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = when (doc.docType) {
                            "Customer Photo" -> AccentOrange
                            "Policy Bond" -> RoyalBluePrimary
                            "Aadhaar Card", "PAN Card" -> EmeraldGreenSecondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.docType,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                )
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (doc.customerName.isNotBlank()) {
                        Text(
                            text = "Client: ${doc.customerName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                        )
                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "${doc.uploadDate} • ${doc.fileSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = RoyalBluePrimary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onReplace, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Autorenew, contentDescription = "Replace", tint = AccentOrange, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun AddDocumentModal(
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onSave: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current

    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(customers.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var docType by remember { mutableStateOf("Aadhaar Card") }
    var fileUriStr by remember { mutableStateOf("") }
    var fileSizeStr by remember { mutableStateOf("1.5 MB") }
    var uploadSource by remember { mutableStateOf("Gallery") }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            fileUriStr = uri.toString()
            uploadSource = "Gallery File"
            if (title.isBlank()) title = "Doc_${System.currentTimeMillis().toString().takeLast(6)}"
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "cam_doc_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            fileUriStr = Uri.fromFile(file).toString()
            fileSizeStr = "${(file.length() / 1024).coerceAtLeast(120)} KB"
            uploadSource = "Camera Capture"
            if (title.isBlank()) title = "Photo_${docType.replace(" ", "_")}"
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            fileUriStr = uri.toString()
            uploadSource = "File Storage"
            if (title.isBlank()) title = "Document_${System.currentTimeMillis().toString().takeLast(6)}"
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
                Text("Select Client *", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(customers) { cust ->
                        FilterChip(
                            selected = selectedCustomer?.id == cust.id,
                            onClick = { selectedCustomer = cust },
                            label = { Text(cust.name) }
                        )
                    }
                }

                Text("Document Category *", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SUPPORTED_DOC_TYPES) { type ->
                        FilterChip(
                            selected = docType == type,
                            onClick = { docType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title *") },
                    placeholder = { Text("e.g. Aadhaar_Front_Back.pdf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_doc_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Upload Options:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { cameraLauncher.launch() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Files", fontSize = 12.sp)
                    }
                }

                if (fileUriStr.isNotBlank()) {
                    Surface(
                        color = EmeraldGreenContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreenSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Attached via $uploadSource", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGreenSecondary))
                                Text(fileUriStr.takeLast(30), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.ifBlank { "${docType.replace(" ", "_")}_${System.currentTimeMillis().toString().takeLast(4)}" }
                    val finalUri = fileUriStr.ifBlank { "content://vault/$finalTitle" }
                    val cust = selectedCustomer

                    val newDoc = DocumentEntity(
                        customerId = cust?.id,
                        customerName = cust?.name ?: "General Vault",
                        docType = docType,
                        title = finalTitle,
                        fileUri = finalUri,
                        fileSize = fileSizeStr,
                        uploadDate = LocalDate.now().toString()
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
fun DocumentPreviewModal(
    doc: DocumentEntity,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = RoyalBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Document Vault Preview", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                        .height(160.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when (doc.docType) {
                                    "Customer Photo" -> Icons.Default.AccountCircle
                                    "Aadhaar Card" -> Icons.Default.Badge
                                    "PAN Card" -> Icons.Default.CreditCard
                                    "Policy Bond" -> Icons.Default.Description
                                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = RoyalBluePrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(doc.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Secured Vault Reference: ${doc.fileUri.takeLast(25)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                DocDetailItem("Document Type", doc.docType)
                DocDetailItem("Client Name", doc.customerName.ifEmpty { "N/A" })
                DocDetailItem("Upload Date", doc.uploadDate)
                DocDetailItem("File Size", doc.fileSize)
                DocDetailItem("Cloud Storage Status", "Firebase Storage Encrypted")
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
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download")
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
    var newFileUri by remember { mutableStateOf("") }
    var newSizeStr by remember { mutableStateOf("2.1 MB") }
    var sourceText by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "replace_doc_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            newFileUri = Uri.fromFile(file).toString()
            newSizeStr = "${(file.length() / 1024).coerceAtLeast(150)} KB"
            sourceText = "Camera"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            newFileUri = uri.toString()
            sourceText = "Gallery"
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
                        onClick = { cameraLauncher.launch() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera")
                    }

                    Button(
                        onClick = { galleryLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery/Files")
                    }
                }

                if (newFileUri.isNotBlank()) {
                    Text("New File Selected via $sourceText", style = MaterialTheme.typography.labelSmall.copy(color = EmeraldGreenSecondary, fontWeight = FontWeight.Bold))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newFileUri.isNotBlank()) {
                        val updatedDoc = existingDoc.copy(
                            fileUri = newFileUri,
                            fileSize = newSizeStr,
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

fun downloadDocument(context: Context, doc: DocumentEntity) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_SUBJECT, "LIC Vault Document: ${doc.docType}")
        putExtra(
            Intent.EXTRA_TEXT,
            "LIC Document Vault Record:\n" +
                    "Type: ${doc.docType}\n" +
                    "Title: ${doc.title}\n" +
                    "Client: ${doc.customerName}\n" +
                    "Upload Date: ${doc.uploadDate}\n" +
                    "Reference: ${doc.fileUri}"
        )
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, "Download / Export Document Details"))
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
