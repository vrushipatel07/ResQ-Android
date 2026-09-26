# ResQ Android - Milestone 2

Milestone 2 extends the verified Compose foundation with the real local emergency-data pipeline: GPS, packet creation, validation, JSON serialization, Room persistence, duplicate rejection, and packet history.

Bluetooth, Wi-Fi peer communication, forwarding, AI classification, and the offline map intentionally remain scheduled for later milestones.

## Architecture

```text
Compose UI -> ResQViewModel -> EmergencyRepository
                                  |-> PacketValidator / PacketJsonCodec
                                  |-> Room emergency_packets
                                  `-> Fused Location Provider
```

Room is the local source of truth. SOS and detailed reports create the same `EmergencyPacket` model that the Bluetooth layer will transmit in Milestone 3.

## Frozen packet schema

```text
messageId, priority, type, text, latitude, longitude, timestamp,
senderId, status, hopCount, lastForwardedAt
```

The Room database also reserves the final `forwarding_log`, `map_markers`, and `device_state` tables.

## Requirements

- Android Studio with Android SDK Platform 35
- Gradle JDK 17 or 21 (do not use JDK 25 with Gradle 8.11.1)
- vivo V40e or another Android 8.0+ physical phone
- GPS/location enabled

## Open and run

1. Extract `ResQ_Milestone_2_Android.zip`.
2. In Android Studio choose **File > Open**.
3. Select the inner **resq-android** folder containing `app` and `settings.gradle.kts`.
4. Wait for Gradle sync. New Room and Google Location dependencies download automatically.
5. Select the connected vivo device and press **Run**.

The app replaces Milestone 1 because it keeps the same application ID. Normal Android Studio installation preserves the locally stored temporary device ID.

## Windows build commands

If your existing Milestone 1 project generated the wrapper, copy these generated wrapper files into this project before using its terminal:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
```

Then run:

```powershell
.\gradlew.bat clean assembleDebug
.\gradlew.bat installDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Milestone 2 test checklist

1. Launch the app and tap **Refresh GPS location**.
2. Accept location permission. Precise location is preferable for the demo.
3. Confirm coordinates replace “Location not captured.”
4. Tap **SOS**, verify coordinates, and press **Confirm SOS**.
5. Open **Messages** and confirm a new CRITICAL/SOS packet is stored.
6. Create a Flood report with: `There are people trapped near the flooded road.`
7. Confirm the report appears with an ID, priority, type, coordinates, timestamp, STORED status, and hop 0.
8. Force-close and reopen ResQ. Both packets must remain visible.
9. Disable internet and repeat SOS creation. It must still persist.
10. Deny location once and verify packet creation stays disabled until a location is available.

Expected result: GPS-backed SOS/report packets survive restart and appear in local packet history. No network is required after the first dependency build.

## Important source paths

```text
app/src/main/java/com/resq/
  data/model/EmergencyPacket.kt
  data/db/ResQDatabase.kt
  data/db/EmergencyPacketDao.kt
  data/repository/EmergencyRepository.kt
  location/LocationProvider.kt
  mesh/packet/PacketValidator.kt
  mesh/packet/PacketJsonCodec.kt
  ui/ResQViewModel.kt
  ui/messages/PacketHistoryScreen.kt
```

## Troubleshooting

- **Location stays unavailable:** turn on phone Location, grant ResQ location permission, then tap Refresh GPS outdoors or near a window.
- **Permission denied permanently:** open Android Settings > Apps > ResQ > Permissions > Location and allow it.
- **Incompatible Gradle JVM:** set Gradle JDK to 17 or 21.
- **SDK 35 missing:** install Android 15/API 35 in SDK Manager.
- **Old app still visible:** press Run again or uninstall ResQ once and reinstall. Uninstalling clears its Room data and temporary device ID.
- **Room schema error after experimenting:** uninstall the development app to clear the database; do not use destructive migration in the final demo build.
