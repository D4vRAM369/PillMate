package com.d4vram.pillmate2.ui.components

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.accompanist.permissions.*
import java.io.File

/**
 * 📸 Componente para capturar/seleccionar fotos de medicamentos
 *
 * Características:
 * - Captura con cámara
 * - Selección desde galería
 * - Vista previa de la imagen
 * - Toggle para usar foto en lugar de emoji
 * - Manejo automático de permisos
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ImagePickerSection(
    currentPhotoPath: String?,
    usePhotoInstead: Boolean,
    onPhotoSelected: (Uri) -> Unit,
    onPhotoDeleted: () -> Unit,
    onUsePhotoToggled: (Boolean) -> Unit,
    medicationId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Permisos necesarios
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // URI temporal para foto de cámara
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher para galería
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPhotoSelected(it) }
    }

    // Launcher para cámara
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedImageUri != null) {
            onPhotoSelected(capturedImageUri!!)
        }
    }

    // Función para lanzar cámara
    val launchCamera = {
        if (cameraPermission.status.isGranted) {
            // Crear archivo temporal
            val photoFile = File.createTempFile(
                "medication_${medicationId}_",
                ".jpg",
                context.cacheDir
            )
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                photoFile
            )
            capturedImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermission.launchPermissionRequest()
        }
    }

    // Galería: GetContent abre el selector del sistema, no necesita permiso de almacenamiento
    val launchGallery = {
        galleryLauncher.launch("image/*")
    }

    // Diálogo de selección
    var showPickerDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Encabezado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.photo_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.photo_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Vista previa de la imagen si existe
            if (!currentPhotoPath.isNullOrEmpty() && File(currentPhotoPath).exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = currentPhotoPath,
                        contentDescription = stringResource(R.string.photo_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Botón de eliminar en la esquina
                    IconButton(
                        onClick = onPhotoDeleted,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.photo_delete),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Toggle para usar foto en lugar de emoji
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.photo_use_as_id),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(if (usePhotoInstead) R.string.photo_use_on else R.string.photo_use_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = usePhotoInstead,
                        onCheckedChange = onUsePhotoToggled
                    )
                }
            }

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón de cámara
                OutlinedButton(
                    onClick = launchCamera,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_camera))
                }

                // Botón de galería
                OutlinedButton(
                    onClick = launchGallery,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_gallery))
                }
            }

            // Información adicional
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.photo_resize_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Manejar resultado de permisos
    LaunchedEffect(cameraPermission.status) {
        if (cameraPermission.status.isGranted && capturedImageUri != null) {
            // Si se acaba de conceder el permiso, relanzar cámara
        }
    }
}
