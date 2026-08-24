package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.HttpConfigStorage
import com.example.network.HttpConfig
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpSettingsDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val currentConfig = remember { HttpConfigStorage.getConfig(context) }

    var ignoreSslErrors by remember { mutableStateOf(currentConfig.ignoreSslErrors) }
    var userAgent by remember { mutableStateOf(currentConfig.userAgent) }
    var loginUrl by remember { mutableStateOf(currentConfig.loginUrl) }
    var loginReferer by remember { mutableStateOf(currentConfig.loginReferer) }
    var loginOrigin by remember { mutableStateOf(currentConfig.loginOrigin) }
    var createLineUrl by remember { mutableStateOf(currentConfig.createLineUrl) }
    var createLineReferer by remember { mutableStateOf(currentConfig.createLineReferer) }
    var packageAdults by remember { mutableStateOf(currentConfig.packageAdults) }
    var packageNormal by remember { mutableStateOf(currentConfig.packageNormal) }
    var packageDurationAdults by remember { mutableStateOf(currentConfig.packageDurationAdults) }
    var packageDurationNormal by remember { mutableStateOf(currentConfig.packageDurationNormal) }
    var bouquetsDefault by remember { mutableStateOf(currentConfig.bouquetsDefault) }
    var bouquetAdults by remember { mutableStateOf(currentConfig.bouquetAdults) }
    var deleteLineUrl by remember { mutableStateOf(currentConfig.deleteLineUrl) }
    var deleteLineReferer by remember { mutableStateOf(currentConfig.deleteLineReferer) }
    var tableUrl by remember { mutableStateOf(currentConfig.tableUrl) }
    var tableReferer by remember { mutableStateOf(currentConfig.tableReferer) }
    var customHeadersJson by remember { mutableStateOf(currentConfig.customHeadersJson) }

    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = GeoSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Http,
                            contentDescription = "Http Settings",
                            tint = GeoPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configuración HTTP",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoOnBackground
                        )
                    }
                    IconButton(
                        onClick = {
                            val def = HttpConfigStorage.resetDefaults(context)
                            ignoreSslErrors = def.ignoreSslErrors
                            userAgent = def.userAgent
                            loginUrl = def.loginUrl
                            loginReferer = def.loginReferer
                            loginOrigin = def.loginOrigin
                            createLineUrl = def.createLineUrl
                            createLineReferer = def.createLineReferer
                            packageAdults = def.packageAdults
                            packageNormal = def.packageNormal
                            packageDurationAdults = def.packageDurationAdults
                            packageDurationNormal = def.packageDurationNormal
                            bouquetsDefault = def.bouquetsDefault
                            bouquetAdults = def.bouquetAdults
                            deleteLineUrl = def.deleteLineUrl
                            deleteLineReferer = def.deleteLineReferer
                            tableUrl = def.tableUrl
                            tableReferer = def.tableReferer
                            customHeadersJson = def.customHeadersJson
                            Toast.makeText(context, "Valores restablecidos por defecto", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restablecer",
                            tint = GeoOnSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Ajusta las URLs, headers y parámetros para evitar fallos de servidor o certificado.",
                    fontSize = 12.sp,
                    color = GeoOnSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Divider(color = GeoOutline, modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. SSL & Security Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Seguridad",
                                    tint = GeoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Seguridad & Certificados SSL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = GeoOnBackground
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ignorar Errores SSL/HTTPS",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = GeoOnBackground
                                    )
                                    Text(
                                        text = "Omite la verificación de certificados no válidos, auto-firmados o expirados.",
                                        fontSize = 11.sp,
                                        color = GeoOnSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = ignoreSslErrors,
                                    onCheckedChange = { ignoreSslErrors = it }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = userAgent,
                                onValueChange = { userAgent = it },
                                label = { Text("User-Agent Header") },
                                singleLine = false,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 2. Login Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "🔑 Petición Iniciar Sesión (Login)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GeoOnBackground
                            )
                            OutlinedTextField(
                                value = loginUrl,
                                onValueChange = { loginUrl = it },
                                label = { Text("URL Login") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = loginReferer,
                                onValueChange = { loginReferer = it },
                                label = { Text("Header Referer Login") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = loginOrigin,
                                onValueChange = { loginOrigin = it },
                                label = { Text("Header Origin Login") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 3. Create/Renew Line Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "➕ Petición Crear / Renovar Usuario",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GeoOnBackground
                            )
                            OutlinedTextField(
                                value = createLineUrl,
                                onValueChange = { createLineUrl = it },
                                label = { Text("URL Crear Línea") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = createLineReferer,
                                onValueChange = { createLineReferer = it },
                                label = { Text("Header Referer Crear Línea") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = packageNormal,
                                    onValueChange = { packageNormal = it },
                                    label = { Text("ID Paquete Normal") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = packageAdults,
                                    onValueChange = { packageAdults = it },
                                    label = { Text("ID Paquete Adultos") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = packageDurationNormal,
                                    onValueChange = { packageDurationNormal = it },
                                    label = { Text("Duración Normal") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = packageDurationAdults,
                                    onValueChange = { packageDurationAdults = it },
                                    label = { Text("Duración Adultos") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = bouquetsDefault,
                                onValueChange = { bouquetsDefault = it },
                                label = { Text("Bouquets por Defecto (separados por coma)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bouquetAdults,
                                onValueChange = { bouquetAdults = it },
                                label = { Text("ID Bouquet Adultos") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 4. Delete Line Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "🗑️ Petición Eliminar Usuario",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GeoOnBackground
                            )
                            OutlinedTextField(
                                value = deleteLineUrl,
                                onValueChange = { deleteLineUrl = it },
                                label = { Text("URL Borrado ({id} se reemplaza por el ID)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = deleteLineReferer,
                                onValueChange = { deleteLineReferer = it },
                                label = { Text("Header Referer Borrado") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 5. Table / List Lines Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "📋 Petición Obtener Tabla de Usuarios",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GeoOnBackground
                            )
                            OutlinedTextField(
                                value = tableUrl,
                                onValueChange = { tableUrl = it },
                                label = { Text("URL Tabla") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tableReferer,
                                onValueChange = { tableReferer = it },
                                label = { Text("Header Referer Tabla") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 6. Extra Custom Headers (JSON)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "⚙️ Headers Personalizados Adicionales (JSON)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GeoOnBackground
                            )
                            OutlinedTextField(
                                value = customHeadersJson,
                                onValueChange = { customHeadersJson = it },
                                label = { Text("Formato JSON: {\"Header-Key\": \"Value\"}") },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancelar", color = GeoOnSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newConfig = HttpConfig(
                                ignoreSslErrors = ignoreSslErrors,
                                userAgent = userAgent.trim(),
                                loginUrl = loginUrl.trim(),
                                loginReferer = loginReferer.trim(),
                                loginOrigin = loginOrigin.trim(),
                                createLineUrl = createLineUrl.trim(),
                                createLineReferer = createLineReferer.trim(),
                                packageAdults = packageAdults.trim(),
                                packageNormal = packageNormal.trim(),
                                packageDurationAdults = packageDurationAdults.trim(),
                                packageDurationNormal = packageDurationNormal.trim(),
                                bouquetsDefault = bouquetsDefault.trim(),
                                bouquetAdults = bouquetAdults.trim(),
                                deleteLineUrl = deleteLineUrl.trim(),
                                deleteLineReferer = deleteLineReferer.trim(),
                                tableUrl = tableUrl.trim(),
                                tableReferer = tableReferer.trim(),
                                customHeadersJson = customHeadersJson.trim()
                            )
                            HttpConfigStorage.saveConfig(context, newConfig)
                            Toast.makeText(context, "Configuración HTTP guardada correctamente", Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GeoPrimary)
                    ) {
                        Text("Guardar Configuración", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
