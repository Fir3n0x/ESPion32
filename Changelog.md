# Changelog

Toutes les modifications notables de l'application Android sont documentées ici.
Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

## [Unreleased]

### Fixed
- **File d'attente GATT sérialisée** : les commandes BLE sont désormais mises en
  file et envoyées une par une, chaque opération n'étant dépilée qu'après le
  callback de complétion (avec timeout de sécurité). Android n'autorisant qu'une
  opération GATT à la fois, les écritures envoyées en rafale (ex. arrêt des
  attaques à la déconnexion) étaient auparavant perdues silencieusement.
  `BleManager.kt`.
- **Bug CRC des captures** : le CRC était parsé avec `toIntOrNull(16)`, qui
  débordait pour tout CRC > 0x7FFFFFFF et retournait `null` (→ `crc=0`), faisant
  échouer à tort ~50 % des transferts. Parsé et comparé en 32 bits non signés
  (`Long`). `BleManager.kt`, `PcapManager.kt`, `BleEvent.kt`.
- **Vérification du retour d'écriture** : `writeCharacteristic`/`writeDescriptor`
  sont désormais contrôlés (succès/échec) au lieu d'être ignorés.
- **Double décodage base64** supprimé : le `ByteArray` du chunk PCAP est passé
  directement au `PcapManager` au lieu d'être ré-encodé puis re-décodé.
- **PcapManager unique et partagé** : la librairie de captures lit désormais le
  même gestionnaire que la réception BLE (avant : instance séparée jamais mise à
  jour). `BleManager.kt`, `PcapLibraryScreen.kt`.

### Changed
- **Connexion BLE** : `autoConnect=false` pour la connexion initiale (connexion
  plus rapide et déterministe) avec retry ciblé sur le statut **133** ; le GATT
  n'est plus fermé immédiatement après `disconnect()`. `BleManager.kt`.
- **Nettoyage GATT** : `BleViewModel.onCleared()` déconnecte proprement pour
  éviter la fuite du client GATT. `BleViewModel.kt`.

### Added
- **Détection de trous PCAP + sauvegarde partielle** : les chunks manquants sont
  détectés par index ; en cas de CRC invalide ou de trous, la capture partielle
  est sauvegardée et les indices manquants sont rapportés (au lieu de tout
  jeter). `PcapManager.kt`.
- **Progression de transfert et état d'attaque pilotés par ACK** : l'UI reflète
  les `STATUS|DEAUTH|value=STARTED|STOPPED` de l'ESP32 et la progression PCAP.
- **Modes de capture** : nouveaux modes `Passive Capture` et `PMKID` en plus de
  `Auth Stealer`, alignés sur le firmware. `Command.kt`, `DeauthScreen.kt`.

### Phase 2 — Permissions & UX

#### Fixed
- **Scan WiFi vide sur API 31+** : `ACCESS_FINE_LOCATION` n'était déclaré que
  `maxSdkVersion=30`, alors que `wifiManager.scanResults` exige FINE_LOCATION à
  tous les niveaux d'API. Le plafond est retiré et la permission est demandée au
  runtime sur toutes les versions. `AndroidManifest.xml`, `MainActivity.kt`.

#### Changed
- **Gestion des refus de permission** : le BLE s'initialise même si la
  localisation est refusée ; messages (Toast) explicites pour le BT et pour le
  scan WiFi. `MainActivity.kt`.
- **Evil Twin** : le panneau est marqué « non implémenté (firmware stub) » et le
  bouton de lancement est désactivé (au lieu d'envoyer une commande sans effet).
  `EvilTwinScreen.kt`.

### Phase 3 — Documentation, tests & nettoyage

#### Added
- **README** complet : build, architecture, fiabilité BLE, permissions, workflow
  de capture, renvoi vers la spec protocole du firmware.
- **Tests unitaires** (JVM, `app/src/test`) :
  - `CommandTest` fige le protocole émis (`toPayload`) — dont les 4 `ATTACKMODE`.
  - `BleMessageParserTest` couvre le parsing, avec une **régression explicite du
    bug CRC** (`crc=0xFFFFFFFF` ne doit plus déborder à 0).

#### Changed
- **Parsing extrait** dans un objet pur `ble/BleMessageParser` (sans dépendance
  Android, décodage base64 injecté) pour être testable ; `BleManager` délègue.
