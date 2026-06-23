package com.example.aegis.ui.screens.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aegis.data.db.entity.AllergyEntity
import com.example.aegis.data.db.entity.ConditionEntity
import com.example.aegis.data.db.entity.MedicationEntity
import com.example.aegis.data.db.entity.PatientEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BgColor      = Color(0xFF0D0D0D)
private val CardColor    = Color(0xFF1C1C1E)
private val RedColor     = Color(0xFFCC0000)
private val RedAccent    = Color(0xFFFF453A)
private val WhiteColor   = Color.White
private val MutedColor   = Color(0xFF8E8E93)
private val DividerColor = Color(0xFF2C2C2E)

@Composable
fun EmergencyInfoScreen(
    patient: PatientEntity?,
    allergies: List<AllergyEntity>,
    conditions: List<ConditionEntity>,
    medications: List<MedicationEntity>,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = BgColor) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // ── Red header ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RedColor)
                    .padding(vertical = 20.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.LocalHospital, null, tint = WhiteColor, modifier = Modifier.size(26.dp))
                        Text(
                            "MEDICAL ID",
                            color = WhiteColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 3.sp,
                        )
                    }
                    if (patient != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(patient.name, color = WhiteColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                // ── Blood type + DOB ──────────────────────────────────────────
                if (patient != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCard(
                            label = "BLOOD TYPE",
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = patient.bloodType.ifBlank { "Unknown" },
                                color = WhiteColor,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (patient.dateOfBirth != null || patient.gender != null) {
                            InfoCard(label = "INFO", modifier = Modifier.weight(1f)) {
                                if (patient.dateOfBirth != null) {
                                    Text(
                                        text = patient.dateOfBirth.toDateString(),
                                        color = WhiteColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (patient.gender != null) {
                                    Text(patient.gender, color = MutedColor, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // ── Allergies ─────────────────────────────────────────────────
                SectionCard(
                    title = "ALLERGIES",
                    icon = Icons.Outlined.Warning,
                    iconTint = if (allergies.isEmpty()) MutedColor else RedAccent,
                    borderColor = if (allergies.isEmpty()) DividerColor else RedAccent,
                ) {
                    if (allergies.isEmpty()) {
                        Text("No known allergies", color = MutedColor, fontSize = 15.sp)
                    } else {
                        allergies.forEachIndexed { i, allergy ->
                            if (i > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(allergy.substance, color = WhiteColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (allergy.severity != "Unknown") {
                                    SeverityBadge(allergy.severity)
                                }
                            }
                            if (allergy.reaction.isNotBlank()) {
                                Text(allergy.reaction, color = MutedColor, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ── Conditions ────────────────────────────────────────────────
                if (conditions.isNotEmpty()) {
                    SectionCard(title = "CONDITIONS", icon = Icons.Outlined.MonitorHeart) {
                        conditions.forEachIndexed { i, condition ->
                            if (i > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 8.dp))
                            Text(condition.name, color = WhiteColor, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        }
                    }
                }

                // ── Medications ───────────────────────────────────────────────
                if (medications.isNotEmpty()) {
                    SectionCard(title = "MEDICATIONS", icon = Icons.Outlined.Medication) {
                        medications.forEachIndexed { i, med ->
                            if (i > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 8.dp))
                            Text(med.name, color = WhiteColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            if (med.dosage.isNotBlank() || med.frequency.isNotBlank()) {
                                Text(
                                    listOfNotNull(
                                        med.dosage.takeIf { it.isNotBlank() },
                                        med.frequency.takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                    color = MutedColor,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                // ── Emergency contact ─────────────────────────────────────────
                if (patient?.emergencyContactName?.isNotBlank() == true) {
                    SectionCard(title = "EMERGENCY CONTACT", icon = Icons.Outlined.Call) {
                        Text(patient.emergencyContactName!!, color = WhiteColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (!patient.emergencyContactPhone.isNullOrBlank()) {
                            Text(patient.emergencyContactPhone, color = MutedColor, fontSize = 15.sp)
                        }
                    }
                }

                // ── No profile fallback ───────────────────────────────────────
                if (patient == null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "No medical profile found.\nOpen Aegis to set up your profile.",
                            color = MutedColor,
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                        )
                    }
                }

                // ── Close ─────────────────────────────────────────────────────
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MutedColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WhiteColor),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = CardColor, modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = MutedColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    iconTint: Color = MutedColor,
    borderColor: Color = DividerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
                Text(title, color = MutedColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SeverityBadge(severity: String) {
    val color = when (severity) {
        "Severe"   -> RedAccent
        "Moderate" -> Color(0xFFFF9F0A)
        else       -> Color(0xFF30D158)
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            severity,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun Long.toDateString(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFmt)
