package com.rakesh.dualnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectAsState

private val Navy = Color(0xFF0B1220)
private val Panel = Color(0xFF151F30)
private val Blue = Color(0xFF60A5FA)
private val Green = Color(0xFF34D399)
private val Muted = Color(0xFF9AA9BD)

class MainActivity : ComponentActivity() {
    private lateinit var monitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = NetworkMonitor(applicationContext)
        monitor.start()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Blue,
                    secondary = Green,
                    background = Navy,
                    surface = Panel
                )
            ) {
                val networks by monitor.networks.collectAsState()
                Dashboard(networks = networks)
            }
        }
    }

    override fun onDestroy() {
        monitor.stop()
        super.onDestroy()
    }
}

@Composable
private fun Dashboard(networks: List<NetworkSnapshot>) {
    val wifi = networks.firstOrNull { it.transport == "Wi-Fi" }
    val cellular = networks.firstOrNull { it.transport == "Mobile data" }
    val totalDown = networks.sumOf { it.rxMbps ?: 0.0 }
    val totalUp = networks.sumOf { it.txMbps ?: 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Navy),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NetworkCheck, null, tint = Blue, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("DUALNET", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("NETWORK INTELLIGENCE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                Surface(color = Color(0xFF123B32), shape = RoundedCornerShape(50)) {
                    Text("ANALYZER", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(18.dp)
            ) {
                Text("OBSERVED DOWNLOAD", color = Muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.2f".format(totalDown), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Text(" Mbps", color = Muted, modifier = Modifier.padding(bottom = 7.dp))
                }
                HorizontalDivider(color = Color(0xFF29364A), modifier = Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("UPLOAD", "%.2f Mbps".format(totalUp))
                    Metric("ACTIVE LINKS", "${networks.size}")
                }
                Text(
                    "Sum of available interface-counter rates. These are observations, not proof of bonded traffic.",
                    color = Muted, fontSize = 10.sp, lineHeight = 14.sp
                )
            }
        }
        item { NetworkCard("Wi-Fi", wifi, Blue, true) }
        item { NetworkCard("Mobile data", cellular, Green, false) }
        item {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("WHAT THIS TEST MEANS", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "DUALNET listens for available internet-capable networks and samples Android interface counters. Android may expose both Wi-Fi and cellular, but background traffic, OEM restrictions and partial interface statistics can affect readings.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Muted, fontSize = 10.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NetworkCard(title: String, item: NetworkSnapshot?, accent: Color, isWifi: Boolean) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isWifi) Icons.Rounded.Wifi else Icons.Rounded.SignalCellularAlt,
                contentDescription = null, tint = accent, modifier = Modifier.size(25.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text(if (item == null) "NOT DETECTED" else "AVAILABLE", color = if (item == null) Muted else accent,
                fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("DOWNLOAD", item?.rxMbps?.let { "%.2f Mbps".format(it) } ?: "--")
            Metric("UPLOAD", item?.txMbps?.let { "%.2f Mbps".format(it) } ?: "--")
        }
        Spacer(Modifier.height(10.dp))
        Text("Interface: ${item?.interfaceName ?: "—"}", color = Muted, fontSize = 11.sp)
        Text("Estimated link: ${item?.downstreamKbps?.let { "$it Kbps" } ?: "—"}",
            color = Muted, fontSize = 11.sp)
        Text("Metered: ${item?.metered?.let { if (it) "Yes" else "No" } ?: "—"}",
            color = Muted, fontSize = 11.sp)
    }
}
