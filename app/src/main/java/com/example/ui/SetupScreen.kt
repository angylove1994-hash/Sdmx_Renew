package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SetupScreen(viewModel: MainViewModel, onSetupComplete: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeoBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SDMX Setup",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = GeoOnBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Configure your reseller credentials.",
            style = MaterialTheme.typography.bodyMedium,
            color = GeoOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = GeoSurface,
                unfocusedContainerColor = GeoSurface,
                focusedIndicatorColor = GeoPrimary,
                unfocusedIndicatorColor = GeoOutline
            ),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Password") },
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = GeoSurface,
                unfocusedContainerColor = GeoSurface,
                focusedIndicatorColor = GeoPrimary,
                unfocusedIndicatorColor = GeoOutline
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                if (user.isNotBlank() && pass.isNotBlank()) {
                    viewModel.saveCredentials(user.trim(), pass.trim())
                    onSetupComplete()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GeoPrimary,
                contentColor = GeoSurface
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Text("SAVE & CONTINUE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}
