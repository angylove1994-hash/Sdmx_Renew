package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.PreferencesManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.security.SecureRandom

@Composable
fun NtfyTopicDialog(
    currentTopic: String,
    currentBaseUrl: String = PreferencesManager.DEFAULT_NTFY_BASE_URL,
    currentLinkEnabled: Boolean = PreferencesManager.DEFAULT_LINK_ENABLED,
    currentLinkCmd: String = PreferencesManager.DEFAULT_LINK_TOPIC_CMD,
    currentLinkResp: String = PreferencesManager.DEFAULT_LINK_TOPIC_RESP,
    currentLinkSecret: String = "",
    currentLinkToken: String = "",
    onDismissRequest: () -> Unit,
    onSaveTopic: ((String) -> Unit)? = null,
    onSaveAll: ((
        reportTopic: String,
        baseUrl: String,
        linkEnabled: Boolean,
        cmdTopic: String,
        respTopic: String,
        secret: String,
        token: String
    ) -> Unit)? = null,
    onTestPush: () -> Unit = {},
    onTestLinkPush: (suspend (baseUrl: String, respTopic: String, token: String) -> Boolean)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var topicInput by remember { mutableStateOf(currentTopic) }
    var baseUrlInput by remember { mutableStateOf(currentBaseUrl) }
    var linkEnabledInput by remember { mutableStateOf(currentLinkEnabled) }
    var linkCmdInput by remember { mutableStateOf(currentLinkCmd) }
    var linkRespInput by remember { mutableStateOf(currentLinkResp) }
    var linkSecretInput by remember { mutableStateOf(currentLinkSecret) }
    var linkTokenInput by remember { mutableStateOf(currentLinkToken) }

    var showSecret by remember { mutableStateOf(false) }
    var isTestingLink by remember { mutableStateOf(false) }
    var testLinkStatus by remember { mutableStateOf<String?>(null) }

    fun cleanTopic(raw: String): String {
        return raw.trim()
            .removePrefix("https://ntfy.sh/")
            .removePrefix("http://ntfy.sh/")
            .removePrefix("ntfy.sh/")
            .replace(" ", "_")
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    fun generateRandomSecret(): String {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }

    fun validateAndSave() {
        val cleanRepTopic = cleanTopic(topicInput).ifEmpty { PreferencesManager.DEFAULT_NTFY_TOPIC }
        val cleanBaseUrl = baseUrlInput.trim().trimEnd('/')
        val cleanCmd = linkCmdInput.trim().ifEmpty { PreferencesManager.DEFAULT_LINK_TOPIC_CMD }
        val cleanResp = linkRespInput.trim().ifEmpty { PreferencesManager.DEFAULT_LINK_TOPIC_RESP }
        val cleanSecret = linkSecretInput.trim()
        val cleanToken = linkTokenInput.trim()

        if (!cleanBaseUrl.startsWith("http://", ignoreCase = true) && !cleanBaseUrl.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(context, "⚠️ La Base URL debe comenzar con http:// o https://", Toast.LENGTH_LONG).show()
            return
        }

        if (linkEnabledInput) {
            if (cleanCmd.equals(cleanResp, ignoreCase = true)) {
                Toast.makeText(context, "⚠️ Los tópicos CMD y RESP deben ser diferentes.", Toast.LENGTH_LONG).show()
                return
            }
            if (cleanSecret.length < 32) {
                Toast.makeText(context, "⚠️ El secreto debe tener al menos 32 caracteres (actual: ${cleanSecret.length}).", Toast.LENGTH_LONG).show()
                return
            }
        }

        if (onSaveAll != null) {
            onSaveAll(cleanRepTopic, cleanBaseUrl, linkEnabledInput, cleanCmd, cleanResp, cleanSecret, cleanToken)
        } else if (onSaveTopic != null) {
            onSaveTopic(cleanRepTopic)
        }
        Toast.makeText(context, "Configuración NTFY guardada", Toast.LENGTH_SHORT).show()
        onDismissRequest()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
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
                // Header
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
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Push",
                                tint = GeoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Configuración NTFY & Enlace Bot",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeoOnBackground
                            )
                            Text(
                                "Reportes push y comunicación con bot",
                                fontSize = 11.sp,
                                color = GeoOnSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.dpadAndTabNav(focusManager, onEnter = onDismissRequest)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GeoOnSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GeoOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: REPORT PUSH (CANAL ACTUAL)
                    Text(
                        "📡 CANAL DE REPORTES DE RENOVACIÓN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeoPrimary,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Tópico de Reportes (ej: Gato_Negro_Reportes)") },
                        leadingIcon = {
                            Icon(Icons.Default.Tag, contentDescription = "Canal", tint = GeoPrimary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (topicInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { topicInput = "" },
                                    modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { topicInput = "" })
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Borrar", tint = GeoOnSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .textFieldKeyNavigation(focusManager),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = GeoSurfaceVariant,
                            unfocusedContainerColor = GeoSurfaceVariant,
                            focusedIndicatorColor = GeoPrimary,
                            unfocusedIndicatorColor = GeoOutline
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // URL Preview Box
                    val sanitizedTopic = remember(topicInput) { cleanTopic(topicInput).ifEmpty { PreferencesManager.DEFAULT_NTFY_TOPIC } }
                    val currentBaseClean = remember(baseUrlInput) { baseUrlInput.trim().trimEnd('/') }
                    val fullUrl = "$currentBaseClean/$sanitizedTopic"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F141C))
                            .padding(10.dp)
                    ) {
                        Text(
                            "URL COMPLETA DE REPORTES:",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            fullUrl,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    // Test push for reports
                    Button(
                        onClick = {
                            val targetTopic = cleanTopic(topicInput).ifEmpty { PreferencesManager.DEFAULT_NTFY_TOPIC }
                            if (onSaveTopic != null) onSaveTopic(targetTopic)
                            onTestPush()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadAndTabNav(focusManager),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoSurfaceVariant,
                            contentColor = GeoPrimary
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Probar", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Probar Alerta Push de Reportes Ahora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = GeoOutline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // SECTION 2: ENLACE CON BOT
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (linkEnabledInput) GeoPrimaryContainer.copy(alpha = 0.25f) else GeoSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (linkEnabledInput) GeoPrimary.copy(alpha = 0.7f) else GeoOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Toggle Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SyncAlt,
                                        contentDescription = "Bot Link",
                                        tint = if (linkEnabledInput) GeoPrimary else GeoOnSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "Enlace con Bot WhatsApp",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = GeoOnBackground
                                        )
                                        Text(
                                            if (linkEnabledInput) "🟢 Activo (Escucha CMD / Responde RESP)" else "🔴 Desactivado",
                                            fontSize = 10.sp,
                                            color = if (linkEnabledInput) Color(0xFF4ADE80) else GeoOnSurfaceVariant
                                        )
                                    }
                                }

                                Switch(
                                    checked = linkEnabledInput,
                                    onCheckedChange = { linkEnabledInput = it },
                                    modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { linkEnabledInput = !linkEnabledInput })
                                )
                            }

                            // Base URL Input
                            OutlinedTextField(
                                value = baseUrlInput,
                                onValueChange = { baseUrlInput = it },
                                label = { Text("Servidor NTFY (Base URL)") },
                                placeholder = { Text("https://ntfy.sh o http://100.122.88.44:8080") },
                                leadingIcon = {
                                    Icon(Icons.Default.Dns, contentDescription = "Servidor", tint = GeoPrimary, modifier = Modifier.size(18.dp))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .textFieldKeyNavigation(focusManager),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = GeoSurfaceVariant,
                                    unfocusedContainerColor = GeoSurfaceVariant,
                                    focusedIndicatorColor = GeoPrimary,
                                    unfocusedIndicatorColor = GeoOutline
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // CMD & RESP Topics Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = linkCmdInput,
                                    onValueChange = { linkCmdInput = it },
                                    label = { Text("Tópico CMD (Bot→App)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    modifier = Modifier
                                        .weight(1f)
                                        .textFieldKeyNavigation(focusManager),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = GeoSurfaceVariant,
                                        unfocusedContainerColor = GeoSurfaceVariant,
                                        focusedIndicatorColor = GeoPrimary,
                                        unfocusedIndicatorColor = GeoOutline
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = linkRespInput,
                                    onValueChange = { linkRespInput = it },
                                    label = { Text("Tópico RESP (App→Bot)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    modifier = Modifier
                                        .weight(1f)
                                        .textFieldKeyNavigation(focusManager),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = GeoSurfaceVariant,
                                        unfocusedContainerColor = GeoSurfaceVariant,
                                        focusedIndicatorColor = GeoPrimary,
                                        unfocusedIndicatorColor = GeoOutline
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            // Secret Input with Show/Hide toggle
                            OutlinedTextField(
                                value = linkSecretInput,
                                onValueChange = { linkSecretInput = it },
                                label = { Text("Secreto HMAC-SHA256 (Mín. 32 caracteres)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = "Secreto", tint = GeoPrimary, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showSecret = !showSecret },
                                        modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { showSecret = !showSecret })
                                    ) {
                                        Icon(
                                            imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showSecret) "Ocultar" else "Mostrar",
                                            tint = GeoOnSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .textFieldKeyNavigation(focusManager),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = GeoSurfaceVariant,
                                    unfocusedContainerColor = GeoSurfaceVariant,
                                    focusedIndicatorColor = GeoPrimary,
                                    unfocusedIndicatorColor = GeoOutline
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Secret Action Buttons Row: Generar secreto & Copiar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val genSecretAction = {
                                    val newSec = generateRandomSecret()
                                    linkSecretInput = newSec
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("ntfy secret", newSec))
                                        Toast.makeText(context, "Secreto generado (64 hex) y copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Secreto generado", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                OutlinedButton(
                                    onClick = genSecretAction,
                                    modifier = Modifier
                                        .weight(1f)
                                        .dpadAndTabNav(focusManager, onEnter = genSecretAction),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Casino, contentDescription = "Generar", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Generar Secreto (32B Hex)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                val copySecretAction = {
                                    if (linkSecretInput.isNotEmpty()) {
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("ntfy secret", linkSecretInput))
                                            Toast.makeText(context, "Secreto copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error al copiar: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No hay secreto para copiar", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                OutlinedButton(
                                    onClick = copySecretAction,
                                    modifier = Modifier
                                        .weight(1f)
                                        .dpadAndTabNav(focusManager, onEnter = copySecretAction),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copiar Secreto", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Optional Bearer Token Input
                            OutlinedTextField(
                                value = linkTokenInput,
                                onValueChange = { linkTokenInput = it },
                                label = { Text("Token Bearer NTFY (Opcional si tu server lo requiere)") },
                                placeholder = { Text("tk_...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = "Token", tint = GeoPrimary, modifier = Modifier.size(18.dp))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .textFieldKeyNavigation(focusManager),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = GeoSurfaceVariant,
                                    unfocusedContainerColor = GeoSurfaceVariant,
                                    focusedIndicatorColor = GeoPrimary,
                                    unfocusedIndicatorColor = GeoOutline
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Test Link Button (Sends test message to RESP topic)
                            val testLinkAction: () -> Unit = {
                                val cleanBaseUrl = baseUrlInput.trim()
                                val cleanResp = linkRespInput.trim().ifEmpty { PreferencesManager.DEFAULT_LINK_TOPIC_RESP }
                                val cleanToken = linkTokenInput.trim()

                                if (!cleanBaseUrl.startsWith("http://", ignoreCase = true) && !cleanBaseUrl.startsWith("https://", ignoreCase = true)) {
                                    Toast.makeText(context, "⚠️ Base URL debe iniciar con http:// o https://", Toast.LENGTH_SHORT).show()
                                } else {
                                    coroutineScope.launch {
                                        isTestingLink = true
                                        testLinkStatus = "Enviando push de prueba al canal RESP ($cleanResp)..."
                                        try {
                                            val ok = if (onTestLinkPush != null) {
                                                onTestLinkPush(cleanBaseUrl, cleanResp, cleanToken)
                                            } else {
                                                com.example.notifications.NtfyManager.publish(
                                                    context = context,
                                                    topic = cleanResp,
                                                    message = "🔔 [SDMX RENEW] Mensaje de prueba de enlace en $cleanResp",
                                                    title = "Prueba de Enlace RESP",
                                                    priority = 3
                                                )
                                            }
                                            if (ok) {
                                                testLinkStatus = "✅ Push de prueba publicado con éxito en RESP ($cleanResp)"
                                                Toast.makeText(context, "✅ Publicación exitosa en canal RESP", Toast.LENGTH_SHORT).show()
                                            } else {
                                                testLinkStatus = "❌ Error al publicar en canal RESP"
                                                Toast.makeText(context, "❌ Error al publicar en canal RESP", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            testLinkStatus = "❌ Excepción: ${e.message}"
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isTestingLink = false
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = testLinkAction,
                                enabled = !isTestingLink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadAndTabNav(focusManager, onEnter = testLinkAction),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GeoPrimary,
                                    contentColor = GeoSurface
                                )
                            ) {
                                if (isTestingLink) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = GeoSurface, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enviando...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "Probar RESP", modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Probar Enlace en Tópico RESP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (testLinkStatus != null) {
                                Text(
                                    text = testLinkStatus!!,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (testLinkStatus!!.startsWith("✅")) Color(0xFF4ADE80) else Color(0xFFFF6B6B),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = GeoOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.dpadAndTabNav(focusManager, onEnter = onDismissRequest)
                    ) {
                        Text("Cancelar", color = GeoOnSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val saveAction = { validateAndSave() }

                    Button(
                        onClick = saveAction,
                        modifier = Modifier.dpadAndTabNav(focusManager, onEnter = saveAction),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoPrimary,
                            contentColor = GeoSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar Configuración", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
