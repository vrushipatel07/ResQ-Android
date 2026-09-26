# ResQ Android - Milestone 3

Milestone 3 adds real Bluetooth Classic packet transfer between two Android phones. It keeps Milestone 2 GPS, Room persistence, packet validation, JSON serialization, and history.

## What works

- Nearby-device runtime permissions on Android 12+
- Paired-device list and nearby Bluetooth discovery
- Discoverable mode request
- RFCOMM receiver listening on both phones
- Send latest Room packet from Phone A to Phone B
- Receive, deserialize, validate, and persist on Phone B
- Duplicate rejection by `messageId`
- Forwarding log entry for sent, received, and failed attempts
- Sender packet status changes to `FORWARDED` after a successful write
- Failed transfers remain locally stored

Wi-Fi Direct, automatic relaying to Phone C, and background retry remain later milestones.

## Transport contract

Both phones use the same service UUID:

```text
4c0f2f96-907d-4a2b-a537-4dbd9bd5b278
```

Packets are newline-terminated compact JSON. Incoming data is capped at 8,192 characters, decoded to the frozen `EmergencyPacket`, validated, and inserted with Room `OnConflictStrategy.IGNORE`.

## Install on Phone A and Phone B

1. Extract `ResQ_Milestone_3_Android.zip`.
2. Open the inner `resq-android` folder in Android Studio.
3. Choose **JVM 21** if prompted.
4. Wait for Gradle sync.
5. Connect Phone A, select it in Android Studio, and press **Run**.
6. Disconnect Phone A, connect Phone B, select it, and press **Run** again.
7. Keep the same build installed on both phones.

Each phone creates its own temporary ResQ device ID.

## Pair the phones first

Use Android Settings for the most reliable hackathon demo:

1. Turn on Bluetooth on both phones.
2. Open **Settings > Bluetooth** on both.
3. Make Phone B visible/searchable.
4. From Phone A, select Phone B.
5. Confirm the same pairing code on both phones.
6. Verify each phone appears under **Paired devices**.

ResQ discovery can see unpaired phones, but Milestone 3 sends only to paired peers so Android does not interrupt an emergency transfer with a pairing dialog.

## Exact A -> B test

### Phone B - receiver

1. Open ResQ.
2. Tap **Bluetooth Mesh Network**.
3. Grant **Nearby devices** permission.
4. Turn on Bluetooth if requested.
5. Tap **BE VISIBLE** and approve 5 minutes.
6. Confirm the status says **Listening for emergency packets**.
7. Leave ResQ open on the Mesh screen.

### Phone A - sender

1. Open ResQ and create a new SOS or report.
2. Open **Bluetooth Mesh Network**.
3. Grant **Nearby devices** permission.
4. Tap **SCAN**.
5. Find Phone B; it should say **Paired**.
6. Tap **SEND** beside Phone B.
7. Wait for `EMG-... sent to Phone B`.

### Verify Phone B

1. Phone B should show `Received EMG-... from ...`.
2. Open **Messages**.
3. Verify the same message ID, type, text, coordinates, and timestamp appear.
4. From Phone A, send the same latest packet again.
5. Phone B must report **Duplicate message ID rejected**, and Messages must still contain only one copy.

## Failure tests

- Turn off Bluetooth on Phone B and send from A: the transfer fails and A retains the packet.
- Select a paired phone without ResQ listening: connection fails and the packet remains stored.
- Restart Phone B: received packets remain in Messages.
- Disable internet on both phones: Bluetooth transfer still works.

## Important source paths

```text
app/src/main/java/com/resq/
  mesh/bluetooth/BluetoothMeshManager.kt
  mesh/packet/PacketJsonCodec.kt
  mesh/packet/PacketValidator.kt
  ui/mesh/MeshScreen.kt
  ui/ResQViewModel.kt
  data/repository/EmergencyRepository.kt
  data/db/EmergencyPacketDao.kt
```

## Troubleshooting

- **Phone B not listed:** pair both phones in Android Settings, then reopen the Mesh screen and tap Scan.
- **Nearby devices denied:** Android Settings > Apps > ResQ > Permissions > Nearby devices > Allow.
- **Connection refused/read failed:** keep ResQ open on Phone B's Mesh screen until it says Listening.
- **Bluetooth permission error after updating:** uninstall the old ResQ build once, reinstall Milestone 3, and grant permissions again. This clears test packets.
- **Cannot discover on Android 10/11:** grant Location permission and turn on the phone's Location service during scanning.
- **Transfer sends but packet is missing:** compare message IDs; the receiver deliberately rejects an existing ID.
- **Gradle JVM error:** select JVM 21, not JVM 25.

## Scope note

This milestone proves a real A -> B hop. Milestone 4 will add forwarding controls, hop-count changes, delivery acknowledgement, and the complete A -> B -> C / Rescue flow.
