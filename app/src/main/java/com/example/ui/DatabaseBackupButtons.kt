package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserModel
import com.example.ui.theme.GeoPrimary
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun DatabaseBackupButtons(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gson = Gson()

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val users = viewModel.users.value
                val json = gson.toJson(users)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                Toast.makeText(context, "Base de datos exportada con éxito (${users.size} usuarios)", Toast.LENGTH_SHORT).show()
                viewModel.addLog("💾 BD exportada exitosamente (${users.size} usuarios).")
            } catch (e: Exception) {
                Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.addLog("❌ Error al exportar BD: ${e.message}")
            }
        }
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val json = reader.readText()
                    val listType = object : TypeToken<List<UserModel>>() {}.type
                    val importedUsers: List<UserModel>? = gson.fromJson(json, listType)
                    if (importedUsers != null) {
                        viewModel.replaceUsers(importedUsers)
                        Toast.makeText(context, "Importados ${importedUsers.size} usuarios", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Formato JSON no válido", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.addLog("❌ Error al importar BD: ${e.message}")
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(
            onClick = { exportLauncher.launch("sdmx_users_backup.json") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = "Exportar",
                modifier = Modifier.size(16.dp),
                tint = GeoPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Exportar",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GeoPrimary
            )
        }

        OutlinedButton(
            onClick = { importLauncher.launch("application/json") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = "Importar",
                modifier = Modifier.size(16.dp),
                tint = GeoPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Importar",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GeoPrimary
            )
        }
    }
}
