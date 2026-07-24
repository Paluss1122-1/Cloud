package com.tabslify.tabs

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.APP_COLOR
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

@Composable
fun DateCalculatorContent(modifier: Modifier = Modifier) {
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var daysDifference by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.tage_rechner),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        DateSelectionCard(
            label = stringResource(R.string.startdatum),
            selectedDate = startDate,
            onDateSelected = { date ->
                startDate = date
                if (endDate != null) {
                    daysDifference = ChronoUnit.DAYS.between(date, endDate)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        DateSelectionCard(
            label = stringResource(R.string.enddatum),
            selectedDate = endDate,
            onDateSelected = { date ->
                endDate = date
                if (startDate != null) {
                    daysDifference = ChronoUnit.DAYS.between(startDate, date)
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (daysDifference != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = APP_COLOR
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.differenz),
                        fontSize = 18.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pluralStringResource(R.plurals.tage,
                            daysDifference!!.toInt(), daysDifference!!),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.wochen_und_tage,
                            daysDifference!! / 7,
                            daysDifference!! % 7
                        ),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                startDate = null
                endDate = null
                daysDifference = null
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = APP_COLOR
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.zurucksetzen), fontSize = 16.sp, color = Color.Gray)
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
            containerColor = APP_COLOR
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
                        calendar.set(
                            selectedDate.year,
                            selectedDate.monthValue - 1,
                            selectedDate.dayOfMonth
                        )
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (selectedDate != null) {
                        String.format(
                            "%02d.%02d.%d",
                            selectedDate.dayOfMonth,
                            selectedDate.monthValue,
                            selectedDate.year
                        )
                    } else {
                        stringResource(R.string.datum_auswahlen)
                    },
                    fontSize = 18.sp
                )
            }
        }
    }
}