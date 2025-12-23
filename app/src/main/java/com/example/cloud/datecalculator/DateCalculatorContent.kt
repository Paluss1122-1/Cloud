package com.example.cloud.datecalculator

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@Composable
fun DateCalculatorContent(modifier: Modifier = Modifier) {
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var daysDifference by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tage-Rechner",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Startdatum auswählen
        DateSelectionCard(
            label = "Startdatum",
            selectedDate = startDate,
            onDateSelected = { date ->
                startDate = date
                if (endDate != null) {
                    daysDifference = ChronoUnit.DAYS.between(date, endDate)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Enddatum auswählen
        DateSelectionCard(
            label = "Enddatum",
            selectedDate = endDate,
            onDateSelected = { date ->
                endDate = date
                if (startDate != null) {
                    daysDifference = ChronoUnit.DAYS.between(startDate, date)
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Ergebnis anzeigen
        if (daysDifference != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Differenz",
                        fontSize = 18.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$daysDifference Tage",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Zusätzliche Informationen
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${(daysDifference!! / 7)} Wochen und ${(daysDifference!! % 7)} Tage",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reset Button
        Button(
            onClick = {
                startDate = null
                endDate = null
                daysDifference = null
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF666666)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zurücksetzen", fontSize = 16.sp)
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun DateSelectionCard(
    label: String,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF333333)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = {
                    val calendar = Calendar.getInstance()
                    if (selectedDate != null) {
                        calendar.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                    }

                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (selectedDate != null) {
                        String.format("%02d.%02d.%d",
                            selectedDate.dayOfMonth,
                            selectedDate.monthValue,
                            selectedDate.year)
                    } else {
                        "Datum auswählen"
                    },
                    fontSize = 18.sp
                )
            }
        }
    }
}