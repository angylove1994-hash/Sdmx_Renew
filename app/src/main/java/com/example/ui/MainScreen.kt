package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.data.BatteryOptimizationHelper
import com.example.data.PreferencesManager
import com.example.data.UserModel
import com.example.ui.theme.*
import com.example.worker.SdmxAlarmScheduler
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val userSdmx by viewModel.userSdmx.collectAsState()
    val intervalHours by viewModel.intervalHours.collectAsState()
    val isAggressiveMode by viewModel.isAggressiveMode.collectAsState()
    val lastExecutionTime by viewModel.lastExecutionTime.collectAsState()
    val nextExecutionTime by viewModel.nextExecutionTime.collectAsState()
    val ntfyTopic by viewModel.ntfyTopic.collectAsState()
    val users by viewModel.users.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<UserModel?>(null) }
    var showAdminCredentialsDialog by remember { mutableStateOf(false) }
    var showHttpSettingsDialog by remember { mutableStateOf(false) }
    var isIgnoringBattery by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }

    LaunchedEffect(Unit) {
        isIgnoringBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    val timeFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }
    val lastRunText = remember(lastExecutionTime) {
        if (lastExecutionTime > 0) timeFormat.format(Date(lastExecutionTime)) else "Aún no ejecutado"
    }
    val nextRunText = remember(nextExecutionTime) {
        if (nextExecutionTime > 0) timeFormat.format(Date(nextExecutionTime)) else "En ~${intervalHours}h"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GeoBackground)
    ) {
        val isWideOrTv = maxWidth >= 650.dp

        if (isWideOrTv) {
            // TV BOX & LANDSCAPE DUAL-PANE LAYOUT
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // LEFT PANE: Controls & System Actions
                Surface(
                    color = GeoSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutline),
                    modifier = Modifier
                        .widthIn(min = 340.dp, max = 390.dp)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header Box
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val openAdminAction = { showAdminCredentialsDialog = true }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = openAdminAction)
                                    .dpadAndTabNav(focusManager, onEnter = openAdminAction)
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(GeoPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("RA", color = GeoSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "SDMX Auto-Renew",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GeoOnBackground
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Admin: ${userSdmx ?: "No config"}",
                                            fontSize = 11.sp,
                                            color = GeoOnSurfaceVariant,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Configurar admin",
                                            tint = GeoOnSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val openHttpAction = { showHttpSettingsDialog = true }
                                Surface(
                                    onClick = openHttpAction,
                                    shape = RoundedCornerShape(50),
                                    color = GeoPrimary,
                                    contentColor = GeoSurface,
                                    modifier = Modifier.dpadAndTabNav(focusManager, onEnter = openHttpAction, borderShape = RoundedCornerShape(50))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Http,
                                            contentDescription = "HTTP",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("HTTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isAggressiveMode) GeoSecondaryContainer else GeoSurfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isAggressiveMode) GeoOnSecondaryContainer else GeoOnSurfaceVariant)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (isAggressiveMode) "24/7 ON" else "PAUSADO",
                                            color = if (isAggressiveMode) GeoOnSecondaryContainer else GeoOnSurfaceVariant,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Battery Alert (Compact)
                        AnimatedVisibility(visible = !isIgnoringBattery) {
                            val batteryAction = {
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                isIgnoringBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1F1F)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = batteryAction)
                                    .dpadAndTabNav(focusManager, onEnter = batteryAction, borderShape = RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryAlert,
                                        contentDescription = "Batería",
                                        tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Toca para desactivar ahorro de batería 24/7",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = Color(0xFFFFD2D2)
                                    )
                                }
                            }
                        }

                        // Interval & Next Execution Block
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(GeoSurfaceVariant)
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var expanded by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { expanded = true }
                                    .dpadAndTabNav(focusManager, onEnter = { expanded = true })
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "INTERVALO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoOnSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Text(
                                    "Cada $intervalHours h ▾",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoOnBackground
                                )
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    listOf("1", "2", "4", "6", "12", "24", "48").forEach { h ->
                                        DropdownMenuItem(
                                            text = { Text("Cada $h horas") },
                                            onClick = {
                                                viewModel.saveInterval(h)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(GeoOutline)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "PRÓXIMA RENOVACIÓN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoOnSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Text(
                                    nextRunText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoPrimary
                                )
                            }
                        }

                        // Aggressive Switch Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(GeoSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "24/7",
                                    tint = if (isAggressiveMode) GeoPrimary else GeoOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Modo Agresivo 24/7", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GeoOnBackground)
                                    Text("Último: $lastRunText", fontSize = 9.sp, color = GeoOnSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isAggressiveMode,
                                onCheckedChange = { viewModel.setAggressiveMode(it) },
                                modifier = Modifier
                                    .size(width = 40.dp, height = 24.dp)
                                    .dpadAndTabNav(focusManager, onEnter = { viewModel.setAggressiveMode(!isAggressiveMode) })
                            )
                        }

                        // Push Notifications (ntfy.sh) Status Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = "Ntfy",
                                            tint = GeoPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Push Remoto (ntfy.sh)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GeoOnBackground
                                        )
                                    }
                                    val testPushAction = { viewModel.sendTestNtfyPush() }
                                    TextButton(
                                        onClick = testPushAction,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(26.dp)
                                            .dpadAndTabNav(focusManager, onEnter = testPushAction)
                                    ) {
                                        Text("Probar Push", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GeoPrimary)
                                    }
                                }
                                Text(
                                    "Canal: https://ntfy.sh/$ntfyTopic",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = GeoOnSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        // Primary Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val manualAction = { 
                                viewModel.runManualCycle()
                                Unit
                            }
                            val addAction = { showAddDialog = true }

                            Button(
                                onClick = manualAction,
                                enabled = !isLoading,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .dpadAndTabNav(focusManager, onEnter = manualAction),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GeoPrimary,
                                    contentColor = GeoSurface
                                )
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GeoSurface, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Procesando...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Renovar Ahora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = addAction,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .dpadAndTabNav(focusManager, onEnter = addAction),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GeoSurfaceVariant,
                                    contentColor = GeoOnBackground
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp), tint = GeoPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nuevo Usuario", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Secondary Buttons: Database Backup & Depurar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            var showPurgeConfirmation by remember { mutableStateOf(false) }

                            DatabaseBackupButtons(
                                viewModel = viewModel,
                                modifier = Modifier.weight(1.3f)
                            )

                            val purgeAction = { showPurgeConfirmation = true }
                            OutlinedButton(
                                onClick = purgeAction,
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadAndTabNav(focusManager, onEnter = purgeAction),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = "Depurar",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFFE57373)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Depurar 'Test'", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                            }

                            if (showPurgeConfirmation) {
                                AlertDialog(
                                    onDismissRequest = { showPurgeConfirmation = false },
                                    title = { Text("¿Eliminar cuentas 'Test'?", fontWeight = FontWeight.Bold) },
                                    text = {
                                        Text("Esta acción buscará y eliminará del panel SDMX y de la base de datos todas las líneas que comiencen con el prefijo 'Test'.\n\n¿Deseas continuar?")
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                showPurgeConfirmation = false
                                                viewModel.purgeTestUsers()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                        ) {
                                            Text("Sí, Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showPurgeConfirmation = false }) {
                                            Text("Cancelar")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // RIGHT PANE: Split into Users (Top 42%) and Live Terminal Logs (Bottom 58%)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. ACTIVE USERS SECTION
                    Surface(
                        color = GeoSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.42f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = "Usuarios",
                                        tint = GeoPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "USUARIOS ACTIVOS (${users.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GeoOnBackground,
                                        letterSpacing = 1.sp
                                    )
                                }
                                val addAction = { showAddDialog = true }
                                TextButton(
                                    onClick = addAction,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .dpadAndTabNav(focusManager, onEnter = addAction)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(14.dp), tint = GeoPrimary)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Agregar", fontSize = 11.sp, color = GeoPrimary, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (users.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(GeoSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No hay usuarios registrados. Presiona '+ Agregar' para registrar líneas a renovar.",
                                        fontSize = 12.sp,
                                        color = GeoOnSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(users) { user ->
                                        UserItemCard(
                                            user = user,
                                            onEdit = { userToEdit = user },
                                            focusManager = focusManager
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. LIVE TERMINAL / REGISTRO DE ACTIVIDAD (FULL CONSOLE VIEW FOR TV BOX)
                    LiveLogConsole(
                        logs = logs,
                        ntfyTopic = ntfyTopic,
                        onClearLogs = { viewModel.clearLogs() },
                        onTestNtfy = { viewModel.sendTestNtfyPush() },
                        focusManager = focusManager,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.58f)
                    )
                }
            }
        } else {
            // PORTRAIT MOBILE LAYOUT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GeoBackground)
            ) {
                // Mobile Header
                Surface(
                    color = GeoSurface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val openAdminAction = { showAdminCredentialsDialog = true }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable(onClick = openAdminAction)
                                    .dpadAndTabNav(focusManager, onEnter = openAdminAction)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(GeoPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("RA", color = GeoSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("SDMX Auto-Renew", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GeoOnBackground)
                                    Text("Admin: ${userSdmx ?: ""}", fontSize = 11.sp, color = GeoOnSurfaceVariant, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val openHttpAction = { showHttpSettingsDialog = true }
                                Surface(
                                    onClick = openHttpAction,
                                    shape = RoundedCornerShape(50),
                                    color = GeoPrimary,
                                    contentColor = GeoSurface,
                                    modifier = Modifier.dpadAndTabNav(focusManager, onEnter = openHttpAction, borderShape = RoundedCornerShape(50))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Http, contentDescription = "HTTP", modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("HTTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Interval & Next run
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(GeoSurfaceVariant)
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var expanded by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { expanded = true }
                                    .dpadAndTabNav(focusManager, onEnter = { expanded = true })
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("INTERVALO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GeoOnSurfaceVariant)
                                Text("Cada $intervalHours h ▾", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GeoOnBackground)
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    listOf("1", "2", "4", "6", "12", "24", "48").forEach { h ->
                                        DropdownMenuItem(
                                            text = { Text("Cada $h horas") },
                                            onClick = {
                                                viewModel.saveInterval(h)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("PRÓXIMA RENOVACIÓN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GeoOnSurfaceVariant)
                                Text(nextRunText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GeoPrimary)
                            }
                        }

                        // Aggressive Switch Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(GeoSurfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = "24/7",
                                    tint = if (isAggressiveMode) GeoPrimary else GeoOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Modo 24/7 Continuo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GeoOnBackground)
                            }
                            Switch(
                                checked = isAggressiveMode,
                                onCheckedChange = { viewModel.setAggressiveMode(it) },
                                modifier = Modifier
                                    .size(width = 40.dp, height = 24.dp)
                                    .dpadAndTabNav(focusManager, onEnter = { viewModel.setAggressiveMode(!isAggressiveMode) })
                            )
                        }
                    }
                }

                // Mobile Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val manualAction = { 
                            viewModel.runManualCycle()
                            Unit
                        }
                        val addAction = { showAddDialog = true }

                        Button(
                            onClick = manualAction,
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .dpadAndTabNav(focusManager, onEnter = manualAction),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary, contentColor = GeoSurface)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GeoSurface, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ejecutando...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Run", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Renovar Ahora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = addAction,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .dpadAndTabNav(focusManager, onEnter = addAction),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoSurfaceVariant, contentColor = GeoOnBackground)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp), tint = GeoPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nuevo Usuario", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Backup & Purge Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        var showPurgeConfirmation by remember { mutableStateOf(false) }

                        DatabaseBackupButtons(viewModel = viewModel, modifier = Modifier.weight(1.3f))

                        val purgeAction = { showPurgeConfirmation = true }
                        OutlinedButton(
                            onClick = purgeAction,
                            modifier = Modifier
                                .weight(1f)
                                .dpadAndTabNav(focusManager, onEnter = purgeAction),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = "Depurar", modifier = Modifier.size(14.dp), tint = Color(0xFFE57373))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Depurar 'Test'", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                        }

                        if (showPurgeConfirmation) {
                            AlertDialog(
                                onDismissRequest = { showPurgeConfirmation = false },
                                title = { Text("¿Eliminar cuentas 'Test'?", fontWeight = FontWeight.Bold) },
                                text = {
                                    Text("Esta acción buscará y eliminará del panel SDMX y de la app todas las líneas que comiencen con el prefijo 'Test'.\n\n¿Deseas continuar?")
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showPurgeConfirmation = false
                                            viewModel.purgeTestUsers()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                    ) {
                                        Text("Sí, Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showPurgeConfirmation = false }) {
                                        Text("Cancelar")
                                    }
                                }
                            )
                        }
                    }

                    // Users List (Weight 1f)
                    Surface(
                        color = GeoSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutline),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            Text(
                                "USUARIOS ACTIVOS (${users.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeoOnSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            if (users.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Sin usuarios registrados.", fontSize = 12.sp, color = GeoOnSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(users) { user ->
                                        UserItemCard(
                                            user = user,
                                            onEdit = { userToEdit = user },
                                            focusManager = focusManager
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Mobile Logs Console
                    LiveLogConsole(
                        logs = logs,
                        ntfyTopic = ntfyTopic,
                        onClearLogs = { viewModel.clearLogs() },
                        onTestNtfy = { viewModel.sendTestNtfyPush() },
                        focusManager = focusManager,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }
    }

    // Dialogs
    if (showAddDialog) {
        AddUserDialog(
            onDismissRequest = { showAddDialog = false },
            onAddUser = { user, pass, meses, adultos ->
                viewModel.addUser(user, pass, meses, adultos)
                showAddDialog = false
            }
        )
    }

    if (userToEdit != null) {
        EditUserDialog(
            user = userToEdit!!,
            onDismissRequest = { userToEdit = null },
            onUpdateUser = { old, new -> viewModel.updateUser(old, new) },
            onDeleteUser = { viewModel.deleteUser(it) }
        )
    }

    if (showAdminCredentialsDialog) {
        AdminCredentialsDialog(
            currentUsername = userSdmx ?: "",
            onDismissRequest = { showAdminCredentialsDialog = false },
            onSaveCredentials = { user, pass ->
                viewModel.saveCredentials(user, pass)
                Toast.makeText(context, "Credenciales guardadas", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showHttpSettingsDialog) {
        HttpSettingsDialog(
            onDismissRequest = { showHttpSettingsDialog = false }
        )
    }

    LaunchedEffect(intervalHours) {
        val h = intervalHours.toIntOrNull() ?: 24
        try {
            SdmxAlarmScheduler.scheduleNextExactAlarm(context.applicationContext, h)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModel.loadData()
    }
}

// User item component with high-contrast card styling for TV and Phone
@Composable
private fun UserItemCard(
    user: UserModel,
    onEdit: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GeoSurfaceVariant)
            .border(1.dp, GeoOutline, RoundedCornerShape(12.dp))
            .clickable { onEdit() }
            .dpadAndTabNav(focusManager, onEnter = onEdit, borderShape = RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.usuario,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeoOnBackground
                    )
                    if (user.adultos) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GeoSecondaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "18+",
                                color = GeoOnSecondaryContainer,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(24.dp)
                        .dpadAndTabNav(focusManager, onEnter = onEdit)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = GeoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ID: ${user.id.ifEmpty { "Pendiente" }}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = GeoOnSurfaceVariant
                )
                Text(
                    "Vence: ${user.vencimiento.substringBefore("T")}",
                    fontSize = 11.sp,
                    color = GeoPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Live Terminal Console Component optimized for TV Box and Mobile
@Composable
private fun LiveLogConsole(
    logs: List<String>,
    ntfyTopic: String,
    onClearLogs: () -> Unit,
    onTestNtfy: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    Surface(
        color = Color(0xFF0F141C),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232D3B)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Console Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "CONSOLA DE EVENTOS & LOGS (${logs.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val copyAction = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SDMX Logs", logs.reversed().joinToString("\n"))
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Logs copiados al portapapeles", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error al copiar: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Copy to clipboard
                    IconButton(
                        onClick = copyAction,
                        modifier = Modifier
                            .size(26.dp)
                            .dpadAndTabNav(focusManager, onEnter = copyAction)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Test push notification
                    IconButton(
                        onClick = onTestNtfy,
                        modifier = Modifier
                            .size(26.dp)
                            .dpadAndTabNav(focusManager, onEnter = onTestNtfy)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Probar Ntfy",
                            tint = GeoPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Clear logs
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .size(26.dp)
                            .dpadAndTabNav(focusManager, onEnter = onClearLogs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Limpiar",
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Divider(
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Console output text
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Esperando actividad... Las operaciones de renovación y respuestas HTTP se mostrarán aquí en tiempo real.",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF64748B)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(logs) { log ->
                        val textColor = when {
                            log.contains("❌") || log.contains("Error") || log.contains("Fallo") -> Color(0xFFFF6B6B)
                            log.contains("✅") || log.contains("🎉") || log.contains("exitosamente") -> Color(0xFF4ADE80)
                            log.contains("📲") || log.contains("Ntfy") -> Color(0xFF38BDF8)
                            log.contains("⚠️") -> Color(0xFFFBBF24)
                            log.contains("🚀") || log.contains("Iniciando") -> Color(0xFFA78BFA)
                            log.contains("🗑️") || log.contains("Depurar") -> Color(0xFFFB7185)
                            else -> Color(0xFFCBD5E1)
                        }

                        Text(
                            text = log,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textColor,
                            lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
