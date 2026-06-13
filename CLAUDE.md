# NFC Check-In — Project Guide

## What this is

An NFC-based guest check-in system for tour buses and events.  
An Android app (Kotlin) reads NFC cards → POSTs to a Python/Flask server → the server pushes real-time events via SSE to a TV display (browser).

## Architecture

```
[Android app]  →  POST /checkin  →  [Flask server]  →  SSE /stream  →  [TV browser]
                                         ↕
                                     guests.csv
```

**Components:**
| File | Role |
|---|---|
| `server.py` | Flask REST + SSE server |
| `static/index.html` | TV display — idle / welcome / guest list screens |
| `guests.csv` | Guest database (nfc_id, ho_ten, loi_chao, ghi_chu) |
| `Android App/` | Kotlin source files (copy into Android Studio project) |

## Running the server

```bash
pip install flask
python server.py
# Runs on http://0.0.0.0:5000
```

**On Android/Termux (hotspot host):**
```bash
pkg install python
pip install flask
python server.py
# TV connects to http://192.168.43.1:5000
```

## API

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | TV display (index.html) |
| `GET` | `/stream` | SSE event stream for TV |
| `POST` | `/checkin` | `{ nfc_id }` → broadcasts to TV |
| `GET` | `/guests` | List all guests |
| `POST` | `/guests` | Add guest |
| `PUT` | `/guests/<nfc_id>` | Update guest |
| `DELETE` | `/guests/<nfc_id>` | Delete guest |
| `GET` | `/history` | Check-in log (newest first, resets on restart) |

## Android App

Source files in `Android App/` — copy into an Android Studio project under `app/src/main/java/com/example/nfccheckin/`.

**Before building:** set the server IP in `ApiClient.kt`:
```kotlin
var BASE_URL = "http://192.168.43.1:5000"  // ← your server IP
```

The `build.gradle.kts` namespace and applicationId must be `com.example.nfccheckin`.

**NFC flow:**
- Known card → shows welcome screen on TV for 8 seconds, then returns to idle
- Unknown card → opens `GuestManagerActivity` pre-filled with the scanned ID
- While Add/Edit dialog is open, tapping another card fills the ID field automatically

## Guest CSV schema

```
nfc_id,ho_ten,loi_chao,ghi_chu
AABBCCDD,Nguyễn Văn A,Chào mừng anh A!,Ghế số 1
```

- `nfc_id` — tag UID in uppercase hex (e.g. `AABBCCDD`)
- `ho_ten` — full name shown on TV
- `loi_chao` — greeting message shown on TV
- `ghi_chu` — optional note (seat number shown as 🪑 badge on TV)

## Known constraints

- `guests.csv` is not thread-safe for concurrent writes — acceptable for single-bus use
- Check-in history (`/history`) is in-memory only; it resets when the server restarts
- SSE clients are cleaned up on disconnect via generator `finally` block (no memory leak)
