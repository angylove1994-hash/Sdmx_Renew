package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.UserModel
import com.example.ui.theme.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

@Composable
fun DatabaseBackupButtons(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val gson = remember { Gson() }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }

    // Standard SAF Export launcher (with fallback)
    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val users = viewModel.users.value
                val json = gson.toJson(users)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                Toast.makeText(context, "Archivo guardado exitosamente (${users.size} usuarios)", Toast.LENGTH_SHORT).show()
                viewModel.addLog("💾 BD exportada mediante explorador (${users.size} usuarios).")
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.addLog("❌ Error al guardar BD: ${e.message}")
            }
        }
    }

    // Standard SAF Import launcher
    val importSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val json = reader.readText()
                    val listType = object : TypeToken<List<UserModel>>() {}.type
                    val importedUsers: List<UserModel>? = gson.fromJson(json, listType)
                    if (importedUsers != null && importedUsers.isNotEmpty()) {
                        Toast.makeText(context, "Iniciando validación y carga (${importedUsers.size} usuarios)...", Toast.LENGTH_SHORT).show()
                        viewModel.replaceUsers(importedUsers)
                    } else {
                        Toast.makeText(context, "Formato JSON no válido o vacío", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.addLog("❌ Error al importar BD: ${e.message}")
            }
        }
    }

    fun shareDatabaseFile() {
        try {
            val users = viewModel.users.value
            val json = gson.toJson(users)
            
            // Create backup file in cache
            val cacheFile = File(context.cacheDir, "sdmx_database_backup.json")
            FileOutputStream(cacheFile).use { fos ->
                fos.write(json.toByteArray())
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Respaldo Base de Datos SDMX (${users.size} usuarios)")
                putExtra(Intent.EXTRA_TEXT, json) // Text fallback for chat apps
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartir base de datos con...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            viewModel.addLog("📤 Abriendo menú para compartir base de datos (${users.size} usuarios)...")
        } catch (e: Exception) {
            // Fallback to text share
            try {
                val users = viewModel.users.value
                val json = gson.toJson(users)
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, json)
                    putExtra(Intent.EXTRA_SUBJECT, "Respaldo Base de Datos SDMX")
                }
                val chooser = Intent.createChooser(textIntent, "Compartir base de datos...")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e2: Exception) {
                Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.addLog("❌ Error al compartir BD: ${e.message}")
            }
        }
    }

    fun saveToDownloads() {
        try {
            val users = viewModel.users.value
            val json = gson.toJson(users)
            
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = if (downloadsDir != null && downloadsDir.exists()) {
                File(downloadsDir, "sdmx_users_backup.json")
            } else {
                File(context.getExternalFilesDir(null), "sdmx_users_backup.json")
            }

            FileOutputStream(destFile).use { fos ->
                fos.write(json.toByteArray())
            }
            Toast.makeText(context, "Guardado en:\n${destFile.absolutePath}", Toast.LENGTH_LONG).show()
            viewModel.addLog("💾 BD guardada en: ${destFile.absolutePath} (${users.size} usuarios)")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al guardar en almacenamiento: ${e.message}", Toast.LENGTH_SHORT).show()
            viewModel.addLog("❌ Error al guardar en almacenamiento: ${e.message}")
        }
    }

    fun copyJsonToClipboard() {
        try {
            val users = viewModel.users.value
            val json = gson.toJson(users)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SDMX Database JSON", json)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "JSON de la base de datos copiado al portapapeles (${users.size} usuarios)", Toast.LENGTH_SHORT).show()
            viewModel.addLog("📋 JSON copiado al portapapeles (${users.size} usuarios).")
        } catch (e: Exception) {
            Toast.makeText(context, "Error al copiar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val openExportAction = { showExportDialog = true }
    val openImportAction = { showImportDialog = true }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(
            onClick = openExportAction,
            modifier = Modifier
                .weight(1f)
                .dpadAndTabNav(focusManager, onEnter = openExportAction),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Exportar y Compartir",
                modifier = Modifier.size(15.dp),
                tint = GeoPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Exportar / Compartir",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = GeoPrimary
            )
        }

        OutlinedButton(
            onClick = openImportAction,
            modifier = Modifier
                .weight(1f)
                .dpadAndTabNav(focusManager, onEnter = openImportAction),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = "Importar",
                modifier = Modifier.size(15.dp),
                tint = GeoPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Importar BD",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = GeoPrimary
            )
        }
    }

    // EXPORT DIALOG
    if (showExportDialog) {
        val users = viewModel.users.value
        Dialog(
            onDismissRequest = { showExportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = GeoSurface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GeoPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Exportar", tint = GeoPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Exportar / Compartir BD", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GeoOnBackground)
                                Text("${users.size} usuarios registrados", fontSize = 11.sp, color = GeoOnSurfaceVariant)
                            }
                        }

                        IconButton(
                            onClick = { showExportDialog = false },
                            modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { showExportDialog = false })
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GeoOnSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = GeoOutline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Option 1: Direct Share Sheet (WhatsApp, Telegram, Gmail, Drive, Bluetooth, etc.)
                        Button(
                            onClick = {
                                showExportDialog = false
                                shareDatabaseFile()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .dpadAndTabNav(focusManager),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary, contentColor = GeoSurface)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Compartir", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("Compartir con Cualquier App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("WhatsApp, Telegram, Correo, Drive, etc.", fontSize = 9.sp, color = GeoSurface.copy(alpha = 0.8f))
                            }
                        }

                        // Option 2: Copy JSON to Clipboard
                        OutlinedButton(
                            onClick = {
                                showExportDialog = false
                                copyJsonToClipboard()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .dpadAndTabNav(focusManager),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(16.dp), tint = GeoPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copiar JSON al Portapapeles", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GeoOnBackground)
                        }

                        // Option 3: Save to Downloads folder
                        OutlinedButton(
                            onClick = {
                                showExportDialog = false
                                saveToDownloads()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .dpadAndTabNav(focusManager),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Guardar", modifier = Modifier.size(16.dp), tint = GeoPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardar en Carpeta Descargas", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GeoOnBackground)
                        }

                        // Option 4: SAF file picker (if available)
                        OutlinedButton(
                            onClick = {
                                showExportDialog = false
                                try {
                                    exportSafLauncher.launch("sdmx_users_backup.json")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Explorador SAF no soportado, usando guardado directo.", Toast.LENGTH_SHORT).show()
                                    saveToDownloads()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .dpadAndTabNav(focusManager),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Explorador", modifier = Modifier.size(16.dp), tint = GeoOnSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardar con Explorador de Archivos", fontSize = 11.sp, color = GeoOnSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    TextButton(
                        onClick = { showExportDialog = false },
                        modifier = Modifier.align(Alignment.End).dpadAndTabNav(focusManager, onEnter = { showExportDialog = false })
                    ) {
                        Text("Cerrar", color = GeoOnSurfaceVariant)
                    }
                }
            }
        }
    }

    // IMPORT DIALOG
    if (showImportDialog) {
        Dialog(
            onDismissRequest = { showImportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = GeoSurface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GeoPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = "Importar", tint = GeoPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Importar Base de Datos", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GeoOnBackground)
                                Text("Carga usuarios desde un archivo o texto", fontSize = 11.sp, color = GeoOnSurfaceVariant)
                            }
                        }

                        IconButton(
                            onClick = { showImportDialog = false },
                            modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { showImportDialog = false })
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GeoOnSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = GeoOutline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Option 1: File picker
                        Button(
                            onClick = {
                                showImportDialog = false
                                try {
                                    importSafLauncher.launch("application/json")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No hay explorador compatible. Usa 'Pegar JSON'.", Toast.LENGTH_SHORT).show()
                                    showPasteImportDialog = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .dpadAndTabNav(focusManager),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary, contentColor = GeoSurface)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Archivo", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Seleccionar Archivo .JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Option 2: Paste JSON from Clipboard
                        OutlinedButton(
                            onClick = {
                                showImportDialog = false
                                showPasteImportDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .dpadAndTabNav(focusManager),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Pegar", modifier = Modifier.size(18.dp), tint = GeoPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pegar JSON desde Portapapeles", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GeoOnBackground)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    TextButton(
                        onClick = { showImportDialog = false },
                        modifier = Modifier.align(Alignment.End).dpadAndTabNav(focusManager, onEnter = { showImportDialog = false })
                    ) {
                        Text("Cancelar", color = GeoOnSurfaceVariant)
                    }
                }
            }
        }
    }

    // PASTE JSON IMPORT DIALOG
    if (showPasteImportDialog) {
        var jsonText by remember { mutableStateOf("") }

        Dialog(
            onDismissRequest = { showPasteImportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = GeoSurface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Pegar Base de Datos JSON", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GeoOnBackground)
                            Text("Pega el texto JSON exportado previamente", fontSize = 11.sp, color = GeoOnSurfaceVariant)
                        }
                        IconButton(
                            onClick = { showPasteImportDialog = false },
                            modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { showPasteImportDialog = false })
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GeoOnSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clipboard.primaryClip?.getItemAt(0)
                                    val text = item?.text?.toString() ?: ""
                                    if (text.isNotEmpty()) {
                                        jsonText = text
                                        Toast.makeText(context, "Texto pegado desde portapapeles", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Portapapeles vacío", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al leer portapapeles: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.dpadAndTabNav(focusManager)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Pegar", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pegar del Portapapeles", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        label = { Text("Contenido JSON de Usuarios") },
                        placeholder = { Text("[{\"usuario\":\"ejemplo\",\"password\":\"123\",...}]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = GeoSurfaceVariant,
                            unfocusedContainerColor = GeoSurfaceVariant,
                            focusedIndicatorColor = GeoPrimary,
                            unfocusedIndicatorColor = GeoOutline
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showPasteImportDialog = false },
                            modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { showPasteImportDialog = false })
                        ) {
                            Text("Cancelar", color = GeoOnSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                try {
                                    val listType = object : TypeToken<List<UserModel>>() {}.type
                                    val importedUsers: List<UserModel>? = gson.fromJson(jsonText.trim(), listType)
                                    if (importedUsers != null && importedUsers.isNotEmpty()) {
                                        Toast.makeText(context, "Iniciando validación y carga (${importedUsers.size} usuarios)...", Toast.LENGTH_SHORT).show()
                                        viewModel.replaceUsers(importedUsers)
                                        showPasteImportDialog = false
                                    } else {
                                        Toast.makeText(context, "Formato JSON inválido o lista vacía", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al procesar JSON: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = jsonText.isNotBlank(),
                            modifier = Modifier.dpadAndTabNav(focusManager),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary, contentColor = GeoSurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cargar e Importar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
