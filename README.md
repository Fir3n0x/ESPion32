# ESPion32 – OffSec WiFi Tool

> **ESPion32** est une application Android (Kotlin / Jetpack Compose) qui pilote
> en BLE un ESP32 (firmware **ESPion32-firmware**, en C) pour réaliser des
> opérations d'audit WiFi : énumération de clients, deauth, capture de
> handshakes / PMKID et beacon spam.
>
> Projet à but éducatif / red team **autorisé uniquement**. Le scan ou l'attaque
> de réseaux sans permission explicite est interdit. À n'utiliser que sur des
> systèmes dont vous êtes propriétaire ou pour lesquels vous avez une
> autorisation écrite.

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="ESPion32 Logo" width="180">
</p>

---

## Build

```bash
./gradlew assembleDebug        # APK debug
./gradlew testDebugUnitTest    # tests unitaires (protocole)
```

- `minSdk 23`, `targetSdk 35`, `compileSdk 36`. JDK 17 recommandé (AGP 8.13).
- Le BLE nécessite un **appareil physique** (l'émulateur ne suffit pas).

## Architecture

- UI : Jetpack Compose + Navigation, une seule `MainActivity`.
- État : `BleViewModel` (BLE) et `WifiViewModel` (scan WiFi local).
- `ble/BleManager` : pilote GATT natif (scan, connexion, file d'attente
  d'opérations, parsing des notifications). Contient un `PcapManager` unique.
- `ble/BleMessageParser` : parsing pur des notifications (testé en JVM).
- `pcap/PcapManager` : réassemblage des captures, détection de trous, CRC,
  sauvegarde `.pcap` (partielle si transfert imparfait).
- `model/Command` : sérialisation des commandes vers l'ESP32.

## Fiabilité BLE (points clés)

- **File d'attente GATT sérialisée** : une opération à la fois, dépilée sur
  callback — indispensable car Android n'autorise qu'une opération GATT en vol.
- **Connexion** `autoConnect=false` + retry ciblé sur le statut 133.
- **Transfert PCAP** : CRC32 comparé en 32 bits non signés (`Long`), détection
  de chunks manquants, sauvegarde partielle, progression affichée.
- **État d'attaque piloté par ACK** `STATUS|DEAUTH|value=STARTED|STOPPED`.

## Permissions

- Android 12+ : `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`.
- **`ACCESS_FINE_LOCATION`** demandée au runtime sur toutes les versions : elle
  est requise par `wifiManager.scanResults` (sinon la liste WiFi est vide).

## Protocole BLE

Le protocole (UUIDs, commandes, événements, modes de capture) est décrit de
façon **faisant autorité** dans `ESPion32-firmware/README.md`. Côté app, il est
implémenté dans `model/Command.kt` (émission) et `ble/BleMessageParser.kt`
(réception), tous deux couverts par des tests unitaires (`app/src/test`).

## Workflow de capture

1. Scanner le WiFi, sélectionner le réseau cible, aller dans le panneau Deauth.
2. Choisir le mode (`Auth Stealer`, `Passive Capture` ou `PMKID`) et lancer.
3. À la fin, le `.pcap` apparaît dans la **PCAP Library** (partage possible).
4. Sur le poste d'analyse : `hcxpcapngtool` → `hashcat -m 22000`.

Voir `Changelog.md` pour le détail des évolutions.
