package com.d4vram.pillmate2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDateTime

// Import necessary Compose UI components
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.animateColorAsState
import androidx.compose.ui.draw.animateDpAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp

// Import for animations
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing

// Import for date handling
import java.time.LocalDate

// Import for color
import androidx.compose.ui.graphics.Color

// Import for Material Theme
import androidx.compose.material3.MaterialTheme