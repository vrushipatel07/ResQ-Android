# ResQ Android - Milestone 7

Milestone 7 adds a persistent offline disaster map while preserving all verified GPS, Room, Bluetooth, Wi-Fi Direct, multi-hop, Rescue, adaptive-decision, speech, and AI-classification functionality.

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

1. Extract `ResQ_Milestone_7_Android.zip`.
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
