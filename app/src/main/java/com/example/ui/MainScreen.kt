package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BatteryOptimizationHelper
import com.example.data.UserModel
import com.example.ui.theme.*
import com.example.worker.SdmxAlarmScheduler
import com.example.worker.SdmxWorker
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val userSdmx by viewModel.userSdmx.collectAsState()
    val intervalHours by viewModel.intervalHours.collectAsState()
    val isAggressiveMode by viewModel.isAggressiveMode.collectAsState()
    val lastExecutionTime by viewModel.lastExecutionTime.collectAsState()
    val nextExecutionTime by viewModel.nextExecutionTime.collectAsState()
    val users by viewModel.users.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<UserModel?>(null) }
    var showAdminCredentialsDialog by remember { mutableStateOf(false) }
    var showHttpSettingsDialog by remember { mutableStateOf(false) }
    var isIgnoringBattery by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }

    // Re-check battery status periodically
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeoBackground)
    ) {
        // App Header
        Surface(
            color = GeoSurface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GeoPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "RA",
                                color = GeoSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.clickable { showAdminCredentialsDialog = true }
                        ) {
                            Text(
                                "SDMX Auto-Renew 24/7",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeoOnBackground
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Admin: $userSdmx",
                                    fontSize = 12.sp,
                                    color = GeoOnSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Configurar admin",
                                    tint = GeoOnSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showHttpSettingsDialog = true },
                            shape = RoundedCornerShape(50),
                            color = GeoPrimary,
                            contentColor = GeoSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Http,
                                    contentDescription = "Configuración HTTP",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "HTTP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isAggressiveMode) GeoSecondaryContainer else GeoSurfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
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
                                    if (isAggressiveMode) "24/7 ON" else "PAUSED",
                                    color = if (isAggressiveMode) GeoOnSecondaryContainer else GeoOnSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                // Battery Alert Banner (if battery optimization is active)
                AnimatedVisibility(visible = !isIgnoringBattery) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1F1F)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clickable {
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                isIgnoringBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = "Batería",
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "¡Evita que Android suspenda la app!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFD2D2)
                                )
                                Text(
                                    "Toca aquí para desactivar la optimización de batería y permitir renovación continua 24/7.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE0B0B0)
                                )
                            }
                        }
                    }
                }

                // Info & Control Blocks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GeoSurfaceVariant)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { expanded = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "INTERVALO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoOnSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Cada $intervalHours Horas",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
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
                            .height(32.dp)
                            .background(GeoOutline)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "PRÓXIMA RENOVACIÓN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoOnSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            nextRunText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GeoPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Aggressive 24/7 Mode Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GeoSurfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Modo Agresivo",
                            tint = if (isAggressiveMode) GeoPrimary else GeoOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                "Modo Agresivo 24/7 (Servicio Persistente + Alarma)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeoOnBackground
                            )
                            Text(
                                "Último ciclo: $lastRunText",
                                fontSize = 10.sp,
                                color = GeoOnSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isAggressiveMode,
                        onCheckedChange = { viewModel.setAggressiveMode(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Text(
                "USUARIOS ACTIVOS (${users.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GeoOnSurfaceVariant,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(users) { user ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(GeoSurface)
                            .border(1.dp, GeoOutline, RoundedCornerShape(16.dp))
                            .clickable { userToEdit = user }
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    user.usuario,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoOnBackground
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (user.adultos) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(GeoSecondaryContainer)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "18+",
                                                color = GeoOnSecondaryContainer,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { userToEdit = user },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar",
                                            tint = GeoOnSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "ID: ${user.id.ifEmpty { "Pendiente" }}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = GeoOnSurfaceVariant
                                )
                                Text(
                                    "Vence: ${user.vencimiento.substringBefore("T")}",
                                    fontSize = 12.sp,
                                    color = GeoPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions: Import/Export Database & Depurar Cuentas Test
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var showPurgeConfirmation by remember { mutableStateOf(false) }

                DatabaseBackupButtons(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )

                // Purge "Test..." Users Button
                OutlinedButton(
                    onClick = { showPurgeConfirmation = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Depurar",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFE57373)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Depurar 'Test'",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE57373)
                    )
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

            Spacer(modifier = Modifier.height(12.dp))

            // Manual Run & Add User
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.runManualCycle() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeoSurfaceVariant,
                        contentColor = GeoOnBackground
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = GeoPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ejecutando...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Manual Run", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Renovar Ahora", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeoPrimary,
                        contentColor = GeoSurface
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add User", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nuevo Usuario", fontWeight = FontWeight.Bold)
                }
            }

            // Real-Time Log Viewer Component
            var showLogsExpanded by remember { mutableStateOf(false) }
            
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = GeoSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GeoPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "REGISTRO DE ACTIVIDAD (${logs.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeoOnBackground,
                                letterSpacing = 1.sp
                            )
                        }
                        Row {
                            TextButton(
                                onClick = { showLogsExpanded = !showLogsExpanded },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (showLogsExpanded) "Minimizar" else "Expandir",
                                    fontSize = 11.sp,
                                    color = GeoPrimary
                                )
                            }
                            TextButton(
                                onClick = { viewModel.clearLogs() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Limpiar",
                                    fontSize = 11.sp,
                                    color = GeoOnSurfaceVariant
                                )
                            }
                        }
                    }

                    if (logs.isEmpty()) {
                        Text(
                            "Sin actividad registrada aún.",
                            fontSize = 12.sp,
                            color = GeoOnSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        val displayLogs = if (showLogsExpanded) logs.take(30) else logs.take(3)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (showLogsExpanded) 200.dp else 65.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(displayLogs) { log ->
                                Text(
                                    text = log,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (log.contains("❌") || log.contains("Error")) Color(0xFFFF8A80)
                                           else if (log.contains("✅") || log.contains("🎉")) Color(0xFFB9F6CA)
                                           else GeoOnBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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

// Extension modifier for scale
private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.size(width = (51 * scale).dp, height = (31 * scale).dp)
)
