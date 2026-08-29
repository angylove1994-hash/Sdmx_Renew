package com.example.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusDirection
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
import com.example.data.UserModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EditUserDialog(
    user: UserModel,
    onDismissRequest: () -> Unit,
    onUpdateUser: (old: UserModel, new: UserModel) -> Unit,
    onDeleteUser: (UserModel) -> Unit
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf(user.usuario) }
    var password by remember { mutableStateOf(user.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var expirationDate by remember { mutableStateOf(user.vencimiento.substringBefore("T").trim()) }
    var lineId by remember { mutableStateOf(user.id) }
    var adultos by remember { mutableStateOf(user.adultos) }
    
    var showConfirmDelete by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    fun parseCurrentDate(): Calendar {
        val cal = Calendar.getInstance()
        try {
            val date = sdf.parse(expirationDate.trim())
            if (date != null) {
                cal.time = date
            }
        } catch (_: Exception) {}
        return cal
    }

    fun openDatePicker() {
        val cal = parseCurrentDate()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val dpd = DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val newCal = Calendar.getInstance()
                newCal.set(selectedYear, selectedMonth, selectedDay)
                expirationDate = sdf.format(newCal.time)
            },
            year,
            month,
            day
        )
        dpd.show()
    }

    fun setDateFromToday(months: Int) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, months)
        expirationDate = sdf.format(cal.time)
    }

    fun addDays(days: Int) {
        val cal = parseCurrentDate()
        cal.add(Calendar.DAY_OF_MONTH, days)
        expirationDate = sdf.format(cal.time)
    }

    fun addMonths(months: Int) {
        val cal = parseCurrentDate()
        cal.add(Calendar.MONTH, months)
        expirationDate = sdf.format(cal.time)
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("¿Eliminar usuario?", fontWeight = FontWeight.Bold) },
            text = { Text("Esta acción eliminará la cuenta '${user.usuario}' tanto de la base de datos local como del panel SDMX.\n\n¿Deseas continuar?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(user)
                        showConfirmDelete = false
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Sí, Eliminar", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f),
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
                    Column {
                        Text(
                            "Modificar Usuario", 
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoOnBackground
                        )
                        Text(
                            "Edita credenciales, fecha de vencimiento o canales",
                            fontSize = 11.sp,
                            color = GeoOnSurfaceVariant
                        )
                    }
                    if (user.id.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GeoPrimaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "ID: ${user.id}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = GeoOnPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = GeoOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Usuario (IPTV)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Usuario (IPTV)") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = "Usuario", tint = GeoPrimary, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
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

                    // Contraseña
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = "Contraseña", tint = GeoPrimary, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.dpadAndTabNav(focusManager)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar" else "Mostrar",
                                    tint = GeoOnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
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

                    // Fecha Vencimiento Section with Calendar Picker & Steppers
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GeoOutline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📅 FECHA DE VENCIMIENTO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoPrimary,
                                    letterSpacing = 1.sp
                                )

                                Button(
                                    onClick = { openDatePicker() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .dpadAndTabNav(focusManager, onEnter = { openDatePicker() }),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GeoPrimary,
                                        contentColor = GeoSurface
                                    )
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Calendario", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Elegir en Calendario", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = expirationDate,
                                onValueChange = { expirationDate = it },
                                label = { Text("Fecha Manual (AAAA-MM-DD)") },
                                leadingIcon = {
                                    IconButton(
                                        onClick = { openDatePicker() },
                                        modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { openDatePicker() })
                                    ) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Vencimiento", tint = GeoPrimary, modifier = Modifier.size(18.dp))
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .textFieldKeyNavigation(focusManager),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = GeoSurface,
                                    unfocusedContainerColor = GeoSurface,
                                    focusedIndicatorColor = GeoPrimary,
                                    unfocusedIndicatorColor = GeoOutline
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Day adjustments
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Días:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GeoOnSurfaceVariant, modifier = Modifier.width(36.dp))

                                OutlinedButton(
                                    onClick = { addDays(-1) },
                                    modifier = Modifier.weight(1f).dpadAndTabNav(focusManager, onEnter = { addDays(-1) }),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("-1 Día", fontSize = 9.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        expirationDate = sdf.format(Calendar.getInstance().time)
                                    },
                                    modifier = Modifier.weight(1f).dpadAndTabNav(focusManager, onEnter = {
                                        expirationDate = sdf.format(Calendar.getInstance().time)
                                    }),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Hoy", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { addDays(1) },
                                    modifier = Modifier.weight(1f).dpadAndTabNav(focusManager, onEnter = { addDays(1) }),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("+1 Día", fontSize = 9.sp)
                                }

                                OutlinedButton(
                                    onClick = { addDays(7) },
                                    modifier = Modifier.weight(1f).dpadAndTabNav(focusManager, onEnter = { addDays(7) }),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("+7 Días", fontSize = 9.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Month adjustments
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Meses:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GeoOnSurfaceVariant, modifier = Modifier.width(36.dp))

                                OutlinedButton(
                                    onClick = { addMonths(-1) },
                                    modifier = Modifier.weight(1f).dpadAndTabNav(focusManager, onEnter = { addMonths(-1) }),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("-1 M", fontSize = 9.sp)
                                }

                                OutlinedButton(
                                    onClick = { addMonths(1) },
                                    modifier = Modifier.weight(1f).dpadAndTabNav(focusManager, onEnter = { addMonths(1) }),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GeoPrimary)
                                ) {
                                    Text("+1 M", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { addMonths(3) },
                                    modifier = Modifier.weight(1f).dpadAndTabNav(focusManager, onEnter = { addMonths(3) }),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("+3 M", fontSize = 9.sp)
                                }

                                OutlinedButton(
                                    onClick = { addMonths(12) },
                                    modifier = Modifier.weight(1f).dpadAndTabNav(focusManager, onEnter = { addMonths(12) }),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("+1 Año", fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    // ID in SDMX panel (optional edit/clear)
                    OutlinedTextField(
                        value = lineId,
                        onValueChange = { lineId = it },
                        label = { Text("ID de línea en panel SDMX (Opcional)") },
                        leadingIcon = {
                            Icon(Icons.Default.Tag, contentDescription = "ID", tint = GeoPrimary, modifier = Modifier.size(18.dp))
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

                    // Adult Content Switch
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GeoSurfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "¿Incluir canal adultos? (18+)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = GeoOnBackground
                                )
                                Text(
                                    if (adultos) "Habilitado (Paquete adultos)" else "Deshabilitado (Paquete normal)",
                                    fontSize = 11.sp,
                                    color = GeoOnSurfaceVariant
                                )
                            }
                            Switch(
                                checked = adultos, 
                                onCheckedChange = { adultos = it },
                                modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { adultos = !adultos }),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GeoPrimary,
                                    uncheckedThumbColor = Color.DarkGray,
                                    uncheckedTrackColor = Color.LightGray,
                                    uncheckedBorderColor = Color.Gray
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = GeoOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showConfirmDelete = true },
                        modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { showConfirmDelete = true }),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE57373))
                    ) {
                        Text("Borrar", fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.dpadAndTabNav(focusManager, onEnter = onDismissRequest)
                        ) {
                            Text("Cancelar", color = GeoOnSurfaceVariant)
                        }
                        
                        Button(
                            onClick = {
                                if (username.isNotBlank() && password.isNotBlank()) {
                                    val cleanedDate = expirationDate.trim().ifEmpty {
                                        sdf.format(Calendar.getInstance().time)
                                    }
                                    val updated = user.copy(
                                        id = lineId.trim(),
                                        usuario = username.trim(),
                                        password = password.trim(),
                                        vencimiento = cleanedDate,
                                        adultos = adultos
                                    )
                                    onUpdateUser(user, updated)
                                    Toast.makeText(context, "Usuario y vencimiento ($cleanedDate) guardados", Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                }
                            },
                            modifier = Modifier.dpadAndTabNav(focusManager),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GeoPrimary,
                                contentColor = GeoSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Guardar Cambios", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
