# ResQ Android - Milestone 7.2 — Karnataka Rescue Services Map

Milestone 7.2 turns the real MapLibre/PMTiles Karnataka map into a rescue-focused map while preserving every previously verified feature.

The map now displays color-coded hospitals and clinics, fire stations, police stations, emergency supplies, and shelters/support points. Rescue POIs work offline, can be tapped for their name/category/coordinates, and the location button centres the map on the phone's current position. Most POIs appear only after zooming to street/city level.

An older zoom-14 archive can show hospitals, but detailed services such as fire and police stations may not be stored in it. Generate a zoom-15 archive for the full rescue layer.

## What is new in 7.2

- Offline rescue-service POI layers with a clear color legend
- Tap-to-inspect rescue place details
- GPS-centred street-level map view
- Existing imported map remains usable; zoom 15 is recommended for complete POI coverage

## What was added in 7.1

- Real pan-and-zoom vector map rendered with MapLibre Android 13.6.1
- Local `karnataka.pmtiles` import with a strict 1 GB maximum
- Map file stored in ResQ's app-specific storage and available without internet
- Roads, water, land use, buildings and administrative boundaries
- All Room-backed ResQ SOS/medical/hazard/safe/rescue markers over the real map
- No API key and no Google Maps billing account
- Original schematic map remains available before import

## Create `karnataka.pmtiles` on Windows

1. Download the Windows `pmtiles.exe` CLI from the official Protomaps/go-pmtiles GitHub Releases page.
2. Visit `https://maps.protomaps.com/builds` and copy the URL of a recent Version 4 daily `.pmtiles` build.
3. In the folder containing `pmtiles.exe`, open PowerShell and run:

```powershell
.\pmtiles.exe extract "PASTE_DAILY_BUILD_URL_HERE" karnataka.pmtiles --bbox=74.05,11.50,78.60,18.80 --maxzoom=15
```

The bounding box covers Karnataka with a small safety margin. Zoom 15 is required for detailed fire, police, clinic, shelter, and supply POIs. If the result exceeds the app's 1 GB limit, keep the existing zoom-14 file for hospitals and major places, or make a smaller district/city archive at zoom 15. Each additional zoom level can substantially increase archive size.

4. Verify the archive:

```powershell
.\pmtiles.exe verify karnataka.pmtiles
```

5. Copy `karnataka.pmtiles` to the phone with USB, Google Drive, or another file-transfer method.
6. In ResQ open **Map** > **Import Karnataka Map**, select the file and wait for 100%.
7. Turn off Wi-Fi/mobile data and reopen Map to prove it is offline.

The PMTiles basemap is distributed under the ODbL Produced Work terms and requires OpenStreetMap attribution. ResQ displays attribution under the map.

## Milestone 7 added

- Fully offline, code-rendered map with no map API or internet dependency
- Persisted Room markers for SOS, hazard, safe zone, medical, and rescue locations
- Emergency packets automatically become tappable map markers
- Marker category filters and saved detail cards
- Current GPS location used as the local map center
- Built-in demonstration markers saved near the current location
- Deterministic coordinate projection unit tests

## Milestone 6 retained

- Deterministic local emergency text classification
- FLOOD, FIRE, MEDICAL, BLOCKED ROAD, DAMAGED BRIDGE, SOS, and manual OTHER fallback
- CRITICAL, URGENT, and NORMAL prioritization
- Visible five-stage AI analysis screen
- Actual classifier explanation and matched keywords
- Android on-device speech recognition on supported Android 12+ devices
- Offline-preferred system recognition fallback
- Typed text always remains available
- Unit tests for the required flood/trapped demo sentence and fallback rules

No cloud AI call is made by the classifier. No model is trained. The rule set is deliberately transparent and dependable for the hackathon demo.

## Install

1. Extract `ResQ_Milestone_7_2_Rescue_Map_Android.zip`.
2. Open its inner `resq-android` folder in Android Studio.
3. Use JVM 21 and sync Gradle.
4. Install the same build on the test phones.
5. Preserve Milestone 4 as the stable Bluetooth baseline and Milestone 5 as the Wi-Fi/decision baseline.

## Required Milestone 7 demo test

1. Open **Map** from Home or the bottom navigation.
2. Tap the location button and grant location permission.
3. Tap the add-marker button once.
4. Confirm Safe Zone, Medical Point, Flooded Road, and Rescue Point appear.
5. Tap each marker and verify its details, coordinates, source, and saved time.
6. Turn off internet and reopen the app; confirm the markers remain available.
7. Create an SOS or analyzed emergency packet and return to Map.
8. Confirm the new emergency appears as an SOS/medical marker.

## Milestone 6 regression test

1. Enable GPS and open **Report Emergency**.
2. Select Flood (this remains the manual fallback).
3. Tap **Speak** and grant microphone permission.
4. Say: `There are people trapped near the flooded road.`
5. Confirm the transcript appears in the text box.
6. Tap **Analyze Emergency**.
7. Watch all five stages complete.
8. Confirm the result is:

```text
Type: FLOOD
Priority: CRITICAL
```

9. Tap **Create Emergency Packet**.
10. Open Messages and confirm the stored packet contains the transcript, GPS, timestamp, FLOOD type and CRITICAL priority.
11. Send the packet through the verified Bluetooth A -> B -> C/Rescue path.

## Typed fallback test

1. Disable internet/mobile data.
2. Type the same sentence manually.
3. Analyze and create the packet.
4. Verify the result is still FLOOD + CRITICAL.

This proves classification is local and speech failure cannot block emergency reporting.

## Additional classification tests

| Input | Expected type | Expected priority |
| --- | --- | --- |
| `Smoke and fire near a house` | FIRE | URGENT |
| `Person is unconscious and needs medical help` | MEDICAL | CRITICAL |
| `Road blocked by landslide and debris` | BLOCKED ROAD | URGENT |
| `Bridge collapse, people cannot escape` | DAMAGED BRIDGE | CRITICAL |
| `Minor road issue requires inspection` with Other selected | OTHER | NORMAL |

## Speech behavior

- Android 12/API 31+ devices use `createOnDeviceSpeechRecognizer()` when an on-device service is available.
- Otherwise ResQ uses the system recognizer with offline preference requested.
- Some phones require downloading an offline language pack in Google voice typing settings.
- If speech reports an unavailable model/service, type the emergency and continue through the identical classifier and packet pipeline.

## Important source paths

```text
app/src/main/java/com/resq/
  ai/classifier/EmergencyClassifier.kt
  ai/speech/SpeechInputManager.kt
  ui/ai/AiAnalysisScreen.kt
  ui/report/ReportScreen.kt
  ui/ResQViewModel.kt

app/src/test/java/com/resq/ai/classifier/
  EmergencyClassifierTest.kt
```

## Troubleshooting

- **Microphone denied:** Android Settings > Apps > ResQ > Permissions > Microphone > Allow.
- **Offline speech unavailable:** install/download the phone's offline English speech pack, or use typed text.
- **Speech transcription is inaccurate:** edit the transcript before tapping Analyze.
- **No GPS:** tap Get Current Location; packet creation remains disabled without a location.
- **Analysis result differs:** check exact wording and matched-keyword line; select the intended type as manual fallback.
- **Classifier test fails after editing rules:** restore deterministic precedence: critical phrases first, then urgent phrases, then normal.
- **Gradle JVM error:** use JVM 21, not JVM 25.

## Important source paths

```text
app/src/main/java/com/resq/
  map/OfflineMapProjector.kt
  ui/map/OfflineMapScreen.kt
  data/model/SupportEntities.kt
  data/db/SupportDao.kt
  ui/ResQViewModel.kt

app/src/test/java/com/resq/map/
  OfflineMapProjectorTest.kt
```

The map intentionally uses a schematic offline disaster layout instead of online map tiles. This guarantees the demonstration still works during a network outage.
