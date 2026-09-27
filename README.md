# ResQ Android - Milestone 6

Milestone 6 adds the on-device intelligence layer while preserving all verified GPS, Room, Bluetooth, Wi-Fi Direct, multi-hop, Rescue, and adaptive-decision functionality.

## Added

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

1. Extract `ResQ_Milestone_6_Android.zip`.
2. Open its inner `resq-android` folder in Android Studio.
3. Use JVM 21 and sync Gradle.
4. Install the same build on the test phones.
5. Preserve Milestone 4 as the stable Bluetooth baseline and Milestone 5 as the Wi-Fi/decision baseline.

## Required demo test

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

## Scope note

Image understanding remains optional and is not included. Milestone 7 adds the offline disaster map, persisted SOS/hazard/help/medical/safe markers, and location visualization.
