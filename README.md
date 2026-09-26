# ResQ Android - Milestone 4

Milestone 4 completes the manual three-phone Bluetooth demo path:

```text
Phone A -> Phone B (relay) -> Phone C (Rescue)
```

It keeps all Milestone 1-3 functionality and adds packet selection, hop-count updates, per-hop acknowledgements, persisted forwarding activity, Rescue Mode, and final delivery status.

## Status rules

- New local SOS/report: `STORED`, hop 0
- A sends to B and B acknowledges: A becomes `FORWARDED`, hop 1
- B stores the received packet: `STORED`, hop 1
- B sends to C/Rescue: B becomes `DELIVERED`, hop 2 after Rescue acknowledgement
- C in Rescue Mode stores the packet: `DELIVERED`, hop 2
- Failed send: local packet remains available for retry
- Duplicate message ID: receiver rejects the second insert

Every SENT/RECEIVED/FAILED/ACK event is persisted in `forwarding_log` and shown under **Messages > Forwarding activity**.

## Install the same build on all three phones

1. Extract `ResQ_Milestone_4_Android.zip`.
2. Open its inner `resq-android` folder in Android Studio.
3. Select JVM 21 and wait for Gradle sync.
4. Install using Android Studio **Run** on Phone A.
5. Repeat on Phone B.
6. Repeat on Phone C.
7. Pair A with B and pair B with C in Android Bluetooth settings.

Pairing A directly with C is optional. Keeping only the required neighbor pairings makes the multi-hop demonstration clearer.

## Prepare the phones

### Phone C - Rescue

1. Open ResQ.
2. Open **Rescue Node Mode**.
3. Grant Nearby devices permission.
4. Turn on the **Rescue node active** switch.
5. Open **Bluetooth Mesh Network**, tap **BE VISIBLE**, and confirm it says Listening.
6. Keep ResQ open.

### Phone B - relay

1. Open **Bluetooth Mesh Network**.
2. Grant permission, tap **BE VISIBLE**, and confirm Listening.
3. Keep Rescue Mode switched off.

### Phone A - source

1. Get GPS and create a fresh SOS/report.
2. Open **Bluetooth Mesh Network**.
3. Select that packet in **Packet ready to send**.

## Exact A -> B -> C test

### Hop 1: A -> B

1. On A, tap Scan.
2. Find paired Phone B.
3. Tap Send.
4. A should show that B acknowledged the packet.
5. On B, open Messages and confirm the same packet is `STORED`, hop 1.

### Hop 2: B -> C/Rescue

1. On B, return to Bluetooth Mesh.
2. Select the received packet.
3. Tap Scan and find paired Phone C.
4. Tap Send.
5. B should show `delivered to Rescue`.
6. On C, open Rescue Mode.
7. Confirm the emergency card appears as delivered, hop 2.

### Audit trail

Open **Messages** on B and C. The forwarding timeline should show:

```text
B: RECEIVED -> ACK DELIVERED
C: RESCUE RECEIVED
```

Phone A records `ACK FORWARDED` for the first hop.

## Acknowledgement protocol

Each RFCOMM connection exchanges one packet envelope and one acknowledgement envelope:

```text
PACKET { forwarderId, payload }
ACK { messageId, accepted, finalDelivery, receiverId, reason }
```

The sender updates its Room record only after receiving a valid acknowledgement with the matching message ID. An acknowledgement timeout closes the socket after 15 seconds and leaves the packet available for retry.

## Failure tests

- C not listening: B fails and keeps its packet.
- C not in Rescue Mode: C stores the packet as a relay; B receives a normal forwarded acknowledgement rather than final delivery.
- Duplicate send: receiver rejects the duplicate `messageId`.
- Internet disabled on all phones: the complete A -> B -> C path still works.
- Restart any phone: packets and forwarding logs remain in Room.

## Important source paths

```text
app/src/main/java/com/resq/
  mesh/bluetooth/BluetoothMeshManager.kt
  mesh/packet/MeshProtocol.kt
  ui/mesh/MeshScreen.kt
  ui/rescue/RescueModeScreen.kt
  ui/messages/PacketHistoryScreen.kt
  data/db/EmergencyPacketDao.kt
  data/db/SupportDao.kt
  data/repository/EmergencyRepository.kt
```

## Troubleshooting

- **Peer missing:** pair phones in Android settings, make the receiving phone visible, then scan again.
- **Connection refused:** open ResQ Bluetooth Mesh on the receiving phone and wait for Listening.
- **Receiver rejected packet:** check whether that message ID already exists on the receiver.
- **Phone C stores instead of delivering:** enable Rescue Node Mode before B sends.
- **Wrong packet selected:** choose the intended message ID with the radio button on the Mesh screen.
- **Nearby devices permission denied:** Android Settings > Apps > ResQ > Permissions > Nearby devices > Allow.
- **Gradle JVM error:** use JVM 21, not JVM 25.

## Scope note

Milestone 4 uses deliberate manual forwarding on Phone B so the judges can clearly observe store-and-forward behavior. Automated retry/forwarding and adaptive Bluetooth/Wi-Fi selection begin in Milestone 5.
