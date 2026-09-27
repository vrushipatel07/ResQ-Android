# ResQ Android - Milestone 5

Milestone 5 preserves the proven Bluetooth A -> B -> C path and adds a real Wi-Fi Direct/TCP alternative plus a transparent adaptive communication decision engine.

## Added in this milestone

- Wi-Fi Direct peer discovery and group hosting
- TCP emergency transfer over the Wi-Fi P2P link
- Same packet validation, Room storage, duplicate rejection, acknowledgements, hop counts, Rescue Mode, and forwarding log used by Bluetooth
- Actual device battery percentage
- Deterministic Bluetooth / Wi-Fi Local / Store-Retry decision
- Decision inputs: priority, serialized packet size, battery, Bluetooth peer, Wi-Fi peer, and recent failures
- Unit tests for key decision branches

Wi-Fi Direct does not use normal internet. Android requires the `INTERNET` manifest permission because the local P2P transport uses Java sockets.

## Install

1. Extract `ResQ_Milestone_5_Android.zip`.
2. Open the inner `resq-android` folder in Android Studio.
3. Use JVM 21 and sync Gradle.
4. Install the same build on all test phones.
5. Keep the working Milestone 4 ZIP as a Bluetooth-only fallback.

## Permissions and phone settings

- Grant **Nearby devices** and **Nearby Wi-Fi devices** when prompted.
- Keep phone Location enabled during Wi-Fi Direct peer discovery. Android's Wi-Fi P2P discovery APIs require Location Mode even on newer phones.
- Wi-Fi must be enabled, but the phones do not need a router, hotspot, mobile data, or internet.

## Wi-Fi Direct test: A -> B

### Phone B - receiver/group owner

1. Open **Wi-Fi Local**.
2. Grant permission.
3. Tap **HOST / RECEIVE**.
4. Wait for `Wi-Fi receiver listening on port 8988`.
5. Leave ResQ open.

### Phone A - sender/client

1. Create a fresh SOS or report.
2. Open **Wi-Fi Local**.
3. Select the packet.
4. Tap **DISCOVER**.
5. Select Phone B and tap **SEND**.
6. Accept any system Wi-Fi Direct connection dialog.

### Verify

- A receives an acknowledgement.
- B stores the packet with hop 1.
- Messages shows `WIFI LOCAL` forwarding activity.
- Sending again is rejected as a duplicate.
- Normal internet can remain disabled.

## Wi-Fi Direct A -> B -> C/Rescue

1. Complete A -> B above.
2. Disconnect/remove the A-B Wi-Fi Direct group in phone Wi-Fi Direct settings if Android keeps it active.
3. On C, enable Rescue Mode and tap **HOST / RECEIVE** in Wi-Fi Local.
4. On B, open Wi-Fi Local and select the received packet.
5. Discover C and send.
6. C stores `DELIVERED`, hop 2; B records `ACK DELIVERED`.

For the most reliable 3-5 minute presentation, Bluetooth remains the primary multi-hop path. Demonstrate Wi-Fi Direct as the working alternative path separately unless all three phone models handle P2P group switching consistently.

## Decision Engine test

1. Create/select an emergency packet.
2. Open Bluetooth Mesh once so paired peers are loaded.
3. Open Wi-Fi Local and run Discover once so Wi-Fi peers are loaded.
4. Open **Decision Engine** from Home.
5. Inspect the displayed priority, byte size, battery, peer availability, and failure counts.

Expected rules:

- No peer -> `STORE RETRY`
- Critical compact packet + paired Bluetooth peer -> `BLUETOOTH`
- Two recent Bluetooth failures + Wi-Fi peer -> `WIFI LOCAL`
- Low battery + Bluetooth peer -> `BLUETOOTH`
- Packet larger than 8,192 bytes + Wi-Fi peer -> `WIFI LOCAL`
- Repeated Wi-Fi failure + Bluetooth peer -> `BLUETOOTH`

All normal emergency packets are deliberately capped at 200 text characters and are compact, so Bluetooth usually remains recommended unless it fails repeatedly.

## Important source paths

```text
app/src/main/java/com/resq/
  ai/decision/CommunicationDecisionEngine.kt
  mesh/wifi/WifiDirectManager.kt
  mesh/bluetooth/BluetoothMeshManager.kt
  mesh/packet/MeshProtocol.kt
  ui/decision/DecisionScreen.kt
  ui/wifi/WifiDirectScreen.kt
  ui/ResQViewModel.kt
```

## Troubleshooting

- **No Wi-Fi peers:** enable Wi-Fi and phone Location on both devices; receiver taps Host first, then sender taps Discover.
- **Error reason 2 / BUSY:** turn Wi-Fi off/on, remove the old Wi-Fi Direct group, reopen ResQ, and retry.
- **Sender becomes group owner:** remove the existing group, make the receiver tap Host first, then connect from sender.
- **TCP connection refused:** wait until receiver explicitly shows `listening on port 8988`.
- **Permission denied:** Android Settings > Apps > ResQ > Permissions > Nearby Wi-Fi devices > Allow.
- **Phone vendor blocks P2P:** keep Bluetooth as the demo path; Wi-Fi Direct support varies by vendor firmware.
- **Decision still says Store Retry:** load peers by opening/scanning the relevant transport screen first.
- **Gradle JVM error:** select JVM 21, not JVM 25.

## Scope note

Milestone 5 makes the communication choice deterministic and transparent. Automatic background retry and route scheduling are not enabled yet; the packet remains safe in Room and the user initiates retry from the relevant transport screen.
