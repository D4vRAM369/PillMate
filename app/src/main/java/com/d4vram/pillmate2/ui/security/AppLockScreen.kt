package com.d4vram.pillmate2.ui.security

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun AppLockScreen(
    isBiometricEnabled: Boolean,
    onSubmitPin: (String) -> Unit,
    onUseBiometric: () -> Unit,
    errorMessage: String? = null
) {
    var pin by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Efecto que se lanza una sola vez cuando la pantalla aparece.
    // Prioridad: biometría primero (privacidad), PIN como respaldo.
    LaunchedEffect(Unit) {
        if (isBiometricEnabled) {
            // Lanzar biometría directamente, sin mostrar teclado de PIN
            onUseBiometric()
        } else {
            // Solo PIN disponible, poner foco en el campo
            focusRequester.requestFocus()
        }
    }

    // Efecto que se lanza si hay un error (ej: PIN incorrecto)
    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            pin = "" // Limpia el campo del PIN
            focusRequester.requestFocus() // Vuelve a poner el foco para un nuevo intento
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .widthIn(min = 280.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔒",
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8) pin = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.lock_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.focusRequester(focusRequester)
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onSubmitPin(pin) },
                    enabled = pin.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.vn_unlock))
                }

                // Ya no necesitamos el botón "Usar huella", porque la petición de
                // biometría ahora es automática. La pantalla del PIN sirve como
                // el método de respaldo si el usuario cancela la biometría.
            }
        }
    }
}
