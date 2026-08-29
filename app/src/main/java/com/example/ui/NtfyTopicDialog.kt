package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.PreferencesManager
import com.example.ui.theme.*

@Composable
fun NtfyTopicDialog(
    currentTopic: String,
    onDismissRequest: () -> Unit,
    onSaveTopic: (String) -> Unit,
    onTestPush: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var topicInput by remember { mutableStateOf(currentTopic) }
    val scrollState = rememberScrollState()

    fun cleanTopic(raw: String): String {
        return raw.trim()
            .removePrefix("https://ntfy.sh/")
            .removePrefix("http://ntfy.sh/")
            .removePrefix("ntfy.sh/")
            .replace(" ", "_")
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
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
                                "Canal de Push Remoto",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeoOnBackground
                            )
                            Text(
                                "Alertas en tiempo real vía ntfy.sh",
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

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = GeoOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Info card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "ℹ️ ¿Cómo recibir las alertas en tu teléfono o PC?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = GeoPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "1. Instala la app gratuita 'ntfy' (Android / iOS) o abre la web ntfy.sh en tu navegador.\n" +
                                "2. Suscríbete al nombre de canal que elijas aquí abajo.\n" +
                                "3. Recibirás alertas instantáneas cuando se renueven cuentas o si ocurre algún error.",
                                fontSize = 11.sp,
                                color = GeoOnSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Topic Input
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Nombre del Canal (Tópico)") },
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

                    // URL Preview Box
                    val sanitizedTopic = remember(topicInput) { cleanTopic(topicInput).ifEmpty { PreferencesManager.DEFAULT_NTFY_TOPIC } }
                    val fullUrl = "https://ntfy.sh/$sanitizedTopic"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F141C))
                            .padding(12.dp)
                    ) {
                        Text(
                            "URL COMPLETA DE SUSCRIPCIÓN:",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            fullUrl,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val copyUrlAction = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("ntfy url", fullUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al copiar: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            OutlinedButton(
                                onClick = copyUrlAction,
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadAndTabNav(focusManager, onEnter = copyUrlAction),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar URL", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }

                            val defaultAction = {
                                topicInput = PreferencesManager.DEFAULT_NTFY_TOPIC
                            }

                            OutlinedButton(
                                onClick = defaultAction,
                                modifier = Modifier
                                    .weight(1f)
                                    .dpadAndTabNav(focusManager, onEnter = defaultAction),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Por defecto", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Por Defecto", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Test push button inside dialog
                    Button(
                        onClick = {
                            val targetTopic = cleanTopic(topicInput).ifEmpty { PreferencesManager.DEFAULT_NTFY_TOPIC }
                            onSaveTopic(targetTopic)
                            onTestPush()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadAndTabNav(focusManager),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoSurfaceVariant,
                            contentColor = GeoPrimary
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Probar", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar y Probar Alerta Push Ahora", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = GeoOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

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

                    Button(
                        onClick = {
                            val targetTopic = cleanTopic(topicInput).ifEmpty { PreferencesManager.DEFAULT_NTFY_TOPIC }
                            onSaveTopic(targetTopic)
                            Toast.makeText(context, "Canal de push guardado: $targetTopic", Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        },
                        modifier = Modifier.dpadAndTabNav(focusManager),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoPrimary,
                            contentColor = GeoSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar Canal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
