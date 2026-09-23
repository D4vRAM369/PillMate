# Daily Rhythm Timeline

// Composable function to display the daily rhythm timeline of medication intake

@Composable
fun DailyRhythmTimeline(
    historyEntries: List<HistoryEntry>, 
    medications: List<Medication>
) {
    val timelineData = remember(historyEntries, medications) {
        historyEntries.groupBy { it.date }
    }
    
    val timelineColors = medications.associateWith { med -> Color.withAlphaChannel(med.color, 0.7f) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = "🕒 Ritmo Diario",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Timeline for the current day
        if (timelineData.containsKey(LocalDateTime.now().toLocalDate())) {
            val dailyEntries = timelineData[LocalDateTime.now().toLocalDate()] ?: emptyList()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f), CircleShape)
            ) {
                // Timeline markers for each medication intake
                dailyEntries.sortedBy { it.time }
                    .forEach { entry ->
                        val color = timelineColors[entry.medicationId] ?: Color.Gray
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .offset(x = (entry.time.hour * 24 / 24).dp, y = 0.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
            }
        }
    }
}