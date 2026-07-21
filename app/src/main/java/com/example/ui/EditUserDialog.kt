package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    var username by remember { mutableStateOf(user.usuario) }
    var password by remember { mutableStateOf(user.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var expirationDate by remember { mutableStateOf(user.vencimiento) }
    var adultos by remember { mutableStateOf(user.adultos) }
    
    var showConfirmDelete by remember { mutableStateOf(false) }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("¿Eliminar usuario?", fontWeight = FontWeight.Bold) },
            text = { Text("Esta acción eliminará a ${user.usuario} localmente y del panel (si la línea existe).") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(user)
                        showConfirmDelete = false
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GeoSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Modificar Usuario", 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GeoOnBackground
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario (IPTV)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GeoSurface,
                        unfocusedContainerColor = GeoSurface,
                        focusedIndicatorColor = GeoPrimary,
                        unfocusedIndicatorColor = GeoOutline
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = GeoOnSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GeoSurface,
                        unfocusedContainerColor = GeoSurface,
                        focusedIndicatorColor = GeoPrimary,
                        unfocusedIndicatorColor = GeoOutline
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = expirationDate,
                    onValueChange = { expirationDate = it },
                    label = { Text("Vencimiento (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GeoSurface,
                        unfocusedContainerColor = GeoSurface,
                        focusedIndicatorColor = GeoPrimary,
                        unfocusedIndicatorColor = GeoOutline
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Establecer vigencia desde hoy:", color = GeoOnSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(1, 2, 3, 6, 12).forEach { m ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.MONTH, m)
                                expirationDate = sdf.format(cal.time)
                            },
                            label = { Text("+$m M") },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = GeoPrimary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("¿Incluir canal adultos?", fontWeight = FontWeight.Medium, color = GeoOnBackground)
                    Switch(
                        checked = adultos, 
                        onCheckedChange = { adultos = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GeoPrimary,
                            uncheckedThumbColor = Color.DarkGray,
                            uncheckedTrackColor = Color.LightGray,
                            uncheckedBorderColor = Color.Gray
                        )
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons: Delete, Cancel, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Borrar", fontWeight = FontWeight.Bold)
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text("Cancelar", color = GeoOnSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (username.isNotBlank() && password.isNotBlank() && expirationDate.isNotBlank()) {
                                    val updated = user.copy(
                                        usuario = username.trim(),
                                        password = password.trim(),
                                        vencimiento = expirationDate.trim(),
                                        adultos = adultos
                                    )
                                    onUpdateUser(user, updated)
                                    onDismissRequest()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GeoPrimary,
                                contentColor = GeoSurface
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
