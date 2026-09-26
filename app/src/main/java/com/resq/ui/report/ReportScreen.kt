package com.resq.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resq.data.model.EmergencyDraft
import com.resq.data.model.EmergencyType
import com.resq.ui.components.ResQCard
import com.resq.ui.components.ResQHeader
import com.resq.ui.theme.ResQDimens

@Composable
fun ReportScreen(
    locationText: String,
    hasLocation: Boolean,
    onGetLocation: () -> Unit,
    onBack: () -> Unit,
    onContinue: (EmergencyDraft) -> Unit
) {
    var selected by remember { mutableStateOf(EmergencyType.FLOOD) }
    var description by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ResQDimens.page)
    ) {
        ResQHeader("Report an Emergency", onBack)
        Text("Select Emergency Type", fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        val reportTypes = listOf(
            EmergencyType.MEDICAL,
            EmergencyType.FIRE,
            EmergencyType.FLOOD,
            EmergencyType.OTHER
        )
        reportTypes.chunked(2).forEach { rowTypes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTypes.forEach { type ->
                    FilterChip(
                        selected = selected == type,
                        onClick = { selected = type },
                        label = { Text("${type.emoji}  ${type.label}") },
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected == type,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 2.dp
                        )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Describe the Emergency", fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= 200) description = it },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            placeholder = { Text("Example: There are people trapped near the flooded road...") },
            supportingText = { Text("${description.length}/200") },
            shape = RoundedCornerShape(14.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("Speak")
            }
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("Add Image")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Location", fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        ResQCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(locationText, fontWeight = FontWeight.Bold)
                    Text("GPS coordinates are stored inside the emergency packet.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!hasLocation) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onGetLocation, modifier = Modifier.fillMaxWidth()) { Text("GET CURRENT LOCATION") }
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onContinue(EmergencyDraft(selected, description.trim())) },
            enabled = description.isNotBlank() && hasLocation,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("SAVE EMERGENCY  →", fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.height(24.dp))
    }
}
