package com.tabslify.tabs.virustotal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary

@Composable
fun VirusTotalScanBar(onOpenReport: (String) -> Unit, modifier: Modifier = Modifier) {
    val allJobs by VirusTotalScanManager.jobs.collectAsState()
    val context = LocalContext.current
    val jobs = allJobs.filter { !it.hiddenInBar }

    if (jobs.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(BgCard, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        jobs.forEach { job ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (job.state is VirusTotalState.Result) {
                            onOpenReport(job.id)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    when (val state = job.state) {
                        is VirusTotalState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF6B6BFF),
                                strokeWidth = 2.dp
                            )
                        }
                        is VirusTotalState.Result -> {
                            val color = when {
                                state.stats.malicious > 0 -> Color(0xFFF44336)
                                state.stats.suspicious > 0 -> Color(0xFFFFAB00)
                                else -> Color(0xFF4CAF50)
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(color, CircleShape)
                            )
                        }
                        is VirusTotalState.Error -> {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFF44336), CircleShape)
                            )
                        }
                        else -> {
                            Spacer(modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = job.label,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        val subText = when (val state = job.state) {
                            is VirusTotalState.Loading -> stringResource(R.string.virustotal_wird_gescannt)
                            is VirusTotalState.Result -> stringResource(R.string.virustotal_ergebnis, state.stats.malicious, state.stats.total)
                            is VirusTotalState.Error -> stringResource(R.string.virustotal_scan_fehler)
                            else -> ""
                        }
                        if (subText.isNotEmpty()) {
                            Text(
                                text = subText,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (job.state !is VirusTotalState.Loading) {
                    IconButton(
                        onClick = { VirusTotalScanManager.dismiss(context, job.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.virustotal_ausblenden),
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
