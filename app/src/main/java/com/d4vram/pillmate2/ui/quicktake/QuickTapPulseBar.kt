# Quick-Tap Pulse Bar

// Compose UI Component for Quick-Tap Pulse Bar
// This component adds a floating bar at the top with quick access to the most
// frequently used medications.

@Composable
fun QuickTapPulseBar(
    medications: List<Medication>, 
    onQuickTap: (Medication) -> Unit,
    selectedMedications: List<Medication> = emptyList()
) {
    val pulseColors = listOf(Color.Blue, Color.Green, Color.Red, Color.Yellow)
    val selectedIndices = selectedMedications.mapIndexed { index, _ -> index }
    
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(60.dp)
        .background(Color.White)
        .border(1.dp, Color.LightGray)
        .padding(8.dp)
    ) {
        val items = medications.take(4).mapIndexed { index, medication ->
            val isSelected = index in selectedIndices
            val color = if (isSelected) Color.Blue else pulseColors[index % pulseColors.size]
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(
                        indication = Indication.None,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            if (index < 4) { // Solo permite pulsar los primeros 4
                                onQuickTap(medications[index])
                            }
                        }
                    )
                    .apply {
                        if (isSelected) {
                            Modifier.animateColor(
                                animationSpec = tween(200, easing = LinearEasing),
                                target = Color.LightBlue,
                                label = "pulse_animation"
                            )
                        }
                    }
            )
        }
    }
}