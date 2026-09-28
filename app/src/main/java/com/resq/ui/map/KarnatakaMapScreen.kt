package com.resq.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.resq.data.model.MapMarker
import com.resq.map.KarnatakaMapState
import com.resq.ui.components.ResQCard
import com.resq.ui.components.ResQHeader
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.io.File

@Composable
fun KarnatakaMapScreen(
    state: KarnatakaMapState,
    markers: List<MapMarker>,
    mapFile: File?,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onGetLocation: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    schematicMap: @Composable () -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    if (state.installed && mapFile != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            ResQHeader("Karnataka Offline Map")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.Default.CloudDone, null) },
                    label = { Text("Offline • ${formatSize(state.sizeBytes)}") }
                )
                TextButton(onClick = { picker.launch(arrayOf("application/octet-stream", "application/x-pmtiles", "*/*")) }) {
                    Icon(Icons.Default.FileOpen, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Replace map")
                }
            }
            RealKarnatakaMap(
                file = mapFile,
                markers = markers,
                currentLatitude = currentLatitude,
                currentLongitude = currentLongitude,
                onGetLocation = onGetLocation,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Text("© OpenStreetMap contributors • Protomaps • markers stored by ResQ", fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ResQHeader("Karnataka Offline Map")
                ResQCard(Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Import the Karnataka map package", style = MaterialTheme.typography.titleMedium)
                    Text("Select karnataka.pmtiles (maximum 1 GB). It is copied into ResQ and then works without internet.")
                    if (state.importing) {
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Importing ${state.progressPercent}% • ${formatSize(state.sizeBytes)}", fontSize = 12.sp)
                    } else {
                        Button(
                            onClick = { picker.launch(arrayOf("application/octet-stream", "application/x-pmtiles", "*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileOpen, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Import Karnataka Map")
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
                Text("The Milestone 7 schematic map remains available below until a real map is imported.", fontSize = 11.sp)
            }
            Box(Modifier.weight(1f)) { schematicMap() }
        }
    }
}

private data class RescuePoi(val name: String, val category: String, val latitude: Double, val longitude: Double)

private val rescueLayerIds = arrayOf("poi-medical", "poi-fire", "poi-police", "poi-supplies", "poi-support")

@Composable
private fun RealKarnatakaMap(
    file: File,
    markers: List<MapMarker>,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onGetLocation: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedPoi by remember { mutableStateOf<RescuePoi?>(null) }
    val mapView = remember(file.absolutePath) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { readyMap ->
                readyMap.setStyle(Style.Builder().fromJson(karnatakaStyle(file))) {
                    val start = if (currentLatitude != null && currentLongitude != null) {
                        LatLng(currentLatitude, currentLongitude)
                    } else {
                        LatLng(12.9716, 77.5946)
                    }
                    readyMap.cameraPosition = CameraPosition.Builder()
                        .target(start)
                        .zoom(12.5)
                        .build()
                    readyMap.addOnMapClickListener { point ->
                        val screenPoint = readyMap.projection.toScreenLocation(point)
                        val feature = readyMap.queryRenderedFeatures(screenPoint, *rescueLayerIds).firstOrNull()
                        if (feature != null) {
                            val kind = if (feature.hasProperty("kind")) {
                                feature.getStringProperty("kind")
                            } else {
                                "rescue_service"
                            }
                            val name = if (feature.hasProperty("name")) {
                                feature.getStringProperty("name").takeIf { it.isNotBlank() }
                            } else {
                                null
                            } ?: kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
                            selectedPoi = RescuePoi(name, rescueCategory(kind), point.latitude, point.longitude)
                            true
                        } else {
                            selectedPoi = null
                            false
                        }
                    }
                    map = readyMap
                }
            }
        }
    }
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(map, markers, currentLatitude, currentLongitude) {
        map?.let { mapLibre ->
            mapLibre.clear()
            if (currentLatitude != null && currentLongitude != null) {
                mapLibre.addMarker(
                    MarkerOptions()
                        .position(LatLng(currentLatitude, currentLongitude))
                        .title("Your location")
                        .snippet("GPS position")
                )
            }
            markers.forEach { marker ->
                mapLibre.addMarker(
                    MarkerOptions()
                        .position(LatLng(marker.lat, marker.lng))
                        .title(marker.title)
                        .snippet("${marker.markerType}: ${marker.description}")
                )
            }
        }
    }
    LaunchedEffect(map, currentLatitude, currentLongitude) {
        if (currentLatitude != null && currentLongitude != null) {
            map?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    LatLng(currentLatitude, currentLongitude),
                    13.5
                )
            )
        }
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        RescueLegend(Modifier.align(Alignment.TopStart).padding(8.dp))
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            selectedPoi?.let { poi ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 5.dp,
                    modifier = Modifier.widthIn(max = 280.dp).padding(bottom = 8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(poi.name, fontWeight = FontWeight.Bold)
                        Text(poi.category, color = MaterialTheme.colorScheme.primary)
                        Text("${"%.5f".format(poi.latitude)}, ${"%.5f".format(poi.longitude)}", fontSize = 11.sp)
                    }
                }
            }
            SmallFloatingActionButton(onClick = onGetLocation) {
                Icon(Icons.Default.MyLocation, "Centre map on my location")
            }
        }
    }
}

@Composable
private fun RescueLegend(modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("Rescue services", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Zoom in • tap a marker", fontSize = 10.sp)
            LegendItem("Medical", ComposeColor(0xFFD92D3A))
            LegendItem("Fire station", ComposeColor(0xFFF47B20))
            LegendItem("Police", ComposeColor(0xFF1976D2))
            LegendItem("Supplies", ComposeColor(0xFF168A55))
            LegendItem("Shelter / support", ComposeColor(0xFF7B4BB7))
        }
    }
}

@Composable
private fun LegendItem(label: String, color: ComposeColor) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.size(9.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.sp)
    }
}

private fun rescueCategory(kind: String): String = when (kind) {
    "hospital", "clinic", "doctors", "dentist" -> "Medical care"
    "fire_station" -> "Fire and rescue"
    "police" -> "Police station"
    "pharmacy", "drinking_water", "water_point", "fuel" -> "Emergency supplies"
    else -> "Shelter and rescue support"
}

private fun karnatakaStyle(file: File): String {
    val source = JSONObject.quote("pmtiles://file://${file.absolutePath}")
    return """
        {
          "version": 8,
          "name": "ResQ Karnataka Offline",
          "sources": {"karnataka": {"type": "vector", "url": $source, "attribution": "© OpenStreetMap contributors • Protomaps"}},
          "layers": [
            {"id":"background","type":"background","paint":{"background-color":"#eef3e8"}},
            {"id":"earth","type":"fill","source":"karnataka","source-layer":"earth","paint":{"fill-color":"#eef3e8"}},
            {"id":"landuse","type":"fill","source":"karnataka","source-layer":"landuse","paint":{"fill-color":"#dcebd2","fill-opacity":0.70}},
            {"id":"water","type":"fill","source":"karnataka","source-layer":"water","paint":{"fill-color":"#91cfe8"}},
            {"id":"buildings","type":"fill","source":"karnataka","source-layer":"buildings","minzoom":12,"paint":{"fill-color":"#d5cec4","fill-outline-color":"#beb5a8"}},
            {"id":"roads-case","type":"line","source":"karnataka","source-layer":"roads","paint":{"line-color":"#c3b9aa","line-width":["interpolate",["linear"],["zoom"],6,0.6,14,5.5]}},
            {"id":"roads","type":"line","source":"karnataka","source-layer":"roads","paint":{"line-color":"#ffffff","line-width":["interpolate",["linear"],["zoom"],6,0.3,14,3.5]}},
            {"id":"boundaries","type":"line","source":"karnataka","source-layer":"boundaries","paint":{"line-color":"#78848c","line-width":1.0,"line-dasharray":[3,2]}},
            {"id":"poi-support","type":"circle","source":"karnataka","source-layer":"pois","minzoom":11,"filter":["in","kind","shelter","social_facility","community_centre","ranger_station","emergency_phone","ambulance_station","helipad"],"paint":{"circle-color":"#7b4bb7","circle-radius":["interpolate",["linear"],["zoom"],11,4,16,9],"circle-stroke-color":"#ffffff","circle-stroke-width":2,"circle-opacity":0.95}},
            {"id":"poi-supplies","type":"circle","source":"karnataka","source-layer":"pois","minzoom":11,"filter":["in","kind","pharmacy","drinking_water","water_point","fuel"],"paint":{"circle-color":"#168a55","circle-radius":["interpolate",["linear"],["zoom"],11,4,16,9],"circle-stroke-color":"#ffffff","circle-stroke-width":2,"circle-opacity":0.95}},
            {"id":"poi-police","type":"circle","source":"karnataka","source-layer":"pois","minzoom":11,"filter":["==","kind","police"],"paint":{"circle-color":"#1976d2","circle-radius":["interpolate",["linear"],["zoom"],11,5,16,10],"circle-stroke-color":"#ffffff","circle-stroke-width":2,"circle-opacity":0.95}},
            {"id":"poi-fire","type":"circle","source":"karnataka","source-layer":"pois","minzoom":11,"filter":["==","kind","fire_station"],"paint":{"circle-color":"#f47b20","circle-radius":["interpolate",["linear"],["zoom"],11,5,16,10],"circle-stroke-color":"#ffffff","circle-stroke-width":2,"circle-opacity":0.95}},
            {"id":"poi-medical","type":"circle","source":"karnataka","source-layer":"pois","minzoom":10,"filter":["in","kind","hospital","clinic","doctors","dentist"],"paint":{"circle-color":"#d92d3a","circle-radius":["interpolate",["linear"],["zoom"],10,5,16,11],"circle-stroke-color":"#ffffff","circle-stroke-width":2,"circle-opacity":0.95}}
          ]
        }
    """.trimIndent()
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "${bytes / 1024} KB"
}
