package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

@Composable
fun AddUserDialog(
    onDismissRequest: () -> Unit,
    onAddUser: (String, String, Int, Boolean) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var meses by remember { mutableIntStateOf(1) }
    var adultos by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    val submitAction = {
        if (username.isNotBlank() && password.isNotBlank()) {
            onAddUser(username.trim(), password.trim(), meses, adultos)
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GeoSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Agregar Usuario Nuevo", 
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GeoOnBackground
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario (IPTV)") },
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
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Down) }),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.dpadAndTabNav(focusManager)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = GeoOnSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .textFieldKeyNavigation(focusManager),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GeoSurface,
                        unfocusedContainerColor = GeoSurface,
                        focusedIndicatorColor = GeoPrimary,
                        unfocusedIndicatorColor = GeoOutline
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))

                Text("Vigencia (Meses)", color = GeoOnSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(1, 2, 3, 6, 12).forEach { m ->
                        FilterChip(
                            selected = meses == m,
                            onClick = { meses = m },
                            label = { Text("$m") },
                            modifier = Modifier.dpadAndTabNav(focusManager, onEnter = { meses = m }),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeoPrimaryContainer,
                                selectedLabelColor = GeoOnPrimaryContainer
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("¿Incluir canal adultos?", fontWeight = FontWeight.Medium, color = GeoOnBackground)
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
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.dpadAndTabNav(focusManager, onEnter = onDismissRequest)
                    ) {
                        Text("Cancelar", color = GeoOnSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = submitAction,
                        modifier = Modifier.dpadAndTabNav(focusManager, onEnter = submitAction),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoPrimary,
                            contentColor = GeoSurface
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Guardar Usuario", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
