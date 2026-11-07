# MotoVault ANPR — Two-Wheeler Parking Stand & Anti-Theft Cash Ledger

**MotoVault ANPR** is a two-role (**Worker** & **Admin/Owner**) Android application built with **Kotlin** and **Jetpack Compose (Material 3)** for 24/7 commercial two-wheeler parking stands. It replaces manual paper notebooks to prevent employee cash skimming, duration disputes, and uncollected customer credit (*Udhaar*).

---

## Key Features

### 1. Dual-Mode License Plate Capture (ANPR + Manual Fallback)
- **Top `Entry / Exit` Mode Switcher**:
  - **Entry (`உள்ளே`)**: Scanning or entering a vehicle plate captures a watermarked entry photo and creates a new parking record.
  - **Exit (`வெளியே`)**: Scanning or selecting an active vehicle automatically fetches its entry timestamp, displays the entry photo proof, calculates 24-hour rolling charges, and adds any prior unpaid credit.
- **3-Tier Recognition Pipeline**:
  1. **On-Device Live ANPR**: CameraX `ImageAnalysis` + optional **LiteRT (`.tflite`)** bounding-box detector (`assets/yolov8n_plate.tflite`) + offline **Google ML Kit Text Recognition v2** + **Indian License Plate Regex Validator** (`^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$`) with common OCR character normalization (`O↔0`, `I↔1`, `S↔5`, `B↔8`).
  2. **AI Camera / Photo OCR Fallback**: Uses **Firebase AI Logic** to read stylized, mud-splattered, or hand-painted number plates.
  3. **Manual Input + Local RTO Quick-Prefix Bar**: One-tap chips for local RTO prefixes (`TN39`, `TN42`, `TN33`, `TN37`, `TN38`, `TN66`, `TN86`, `KA01`) for damaged or rusted plates.
- **Mandatory Watermarked Photo Proof**: Every vehicle entry automatically saves a compressed JPEG snapshot with a burned-in footer (`MOTOVAULT ENTRY • PLATE • TIMESTAMP`) to resolve customer duration disputes.

### 2. Smart Low-Light Auto-Torch
- Calculates average pixel luminosity directly from the `YUV_420_888` Y-plane on every camera frame.
- Automatically enables the camera flashlight (`camera.cameraControl.enableTorch(true)`) when ambient brightness drops below the configured threshold, alongside an on-screen `Auto Light` toggle and manual Torch button.

### 3. 24-Hour Rolling Billing Engine, Passes & "Pay Tomorrow" Credit
- **Configurable Tariffs (Owner Controlled)**:
  - **Base Daily Rate**: Default `₹20` per 24-hour block (`ceil(elapsedHours / 24.0)`, minimum 1 day).
  - **Weekly Pass (`WEEKLY_PASS`)**: Default `₹100` (7 days).
  - **Monthly Pass (`MONTHLY_PASS`)**: Default `₹350` (30 days).
- **Pass Validation**:
  - Scanning an active pass holder displays a green **`VALID PASS - X DAYS LEFT`** banner and applies `₹0` daily fee.
  - Scanning an expired pass holder alerts the worker and offers one-tap renewal.
- **Exit Payment Options & Credit (*Udhaar*) Tracking**:
  - **Paid — Cash (`ரொக்கம்`)**: Added directly to the active shift's **Expected Cash in Drawer**.
  - **Paid — UPI / GPay**: Logged separately under digital collections so workers cannot mix digital payments with physical cash.
  - **Pay Tomorrow / Credit (`நாளை தருகிறேன்`)**: Records an `UNPAID_CREDIT` linked to the license plate.
- **Automatic Debt Alert**:
  - The next time a vehicle with an unpaid credit balance is scanned or typed at Entry or Exit, a red warning banner flashes: **`PREVIOUS PENDING BALANCE: ₹[Amount] from [Date]`** and automatically adds the prior balance to the current bill.

### 4. Anti-Theft Audit & Owner Dashboard (PIN-Protected)
- **Strict Role Separation**:
  - **Workers** can log entries, process exits, and issue passes. Workers **cannot** delete records, edit past timestamps, or alter tariff rates.
  - **Owners** unlock the **Shift Ledger** and **Audit Reports** via a 4–8 digit PIN (default: `1234`).
- **Expected Cash in Drawer Card**: Real-time calculation of physical cash collected from exits and cash passes so the owner knows the exact rupee amount that must be in the drawer at shift handover.
- **AI Shift Fraud Audit & Customer Receipt Explanation**:
  - **Owner Deep Audit**: Analyzes shift logs for unusual credit spikes, cash-to-UPI anomalies, or overstaying bikes.
  - **Customer Bill Breakdown**: Generates a clear explanation of the stay duration and fee in **Tamil** or **English**.

### 5. Offline-First Room Database & Automatic Firestore Sync Queue
- All vehicle entries, exits, passes, credits, and rate changes are written immediately to the local **Room SQLite Database** (`ParkingDatabase`).
- Every local write enqueues a `SyncQueueEntity` record (`PENDING` / `FAILED`).
- `NetworkSyncMonitor` observes `ConnectivityManager` network availability and automatically flushes queued mutations to **Firebase Firestore** once cellular or Wi-Fi connectivity returns, while live Firestore snapshot listeners sync records across Worker and Owner devices in real time.

### 6. Instant Bilingual UI (`தமிழ்` / `EN`)
- One-tap **`தமிழ்` / `EN`** toggle button in the top bar translates all Worker and Owner screens, buttons, alerts, and AI billing explanations between Tamil and English.

---

## Technical Stack

| Layer | Technology |
| :--- | :--- |
| **UI Framework** | Jetpack Compose (Material 3), Adaptive `NavigationBar` / `NavigationRail`, Dark High-Contrast Outdoor Theme |
| **Camera & Hardware** | AndroidX CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) |
| **On-Device Vision & OCR** | Google Play Services ML Kit Text Recognition (`19.0.1`) + LiteRT (`com.google.ai.edge.litert:litert:1.4.0`, 16 KB ELF page-aligned) |
| **Cloud AI** | Firebase AI Logic (`firebase-ai`) with `gemini-2.5-flash` & `gemini-3.1-pro-preview` |
| **Local Persistence** | AndroidX Room (`2.7.0` with KSP) + Offline Sync Queue (`SyncQueueEntity`) |
| **Cloud Sync & Auth** | Firebase Firestore (real-time listeners + batch sync) & Firebase Auth with Google Sign-In (`CredentialManager`) |
| **Testing** | JUnit 4, Robolectric, & Firebase Emulator Security Rules Test Suite |

---

## Project & Package Structure

```text
MotoVaultANPR/
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt                  # App entry point, TopBar, adaptive NavBar/NavRail, Worker Log screen
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── ParkingEntities.kt       # Room entities: VehicleLogEntity, PassEntity, CreditEntity, RateConfigEntity, SyncQueueEntity
│       │   │   │   │   ├── ParkingDao.kt            # Room DAO queries, reactive Flows, & sync queue operations
│       │   │   │   │   └── ParkingDatabase.kt       # Room database configuration
│       │   │   │   └── repository/
│       │   │   │       ├── BillingRepository.kt     # 24h billing engine, Entry/Exit processor, & Room <-> Firestore sync queue
│       │   │   │       └── FirestoreErrorHandler.kt # Structured Firestore error diagnostics
│       │   │   ├── ui/
│       │   │   │   ├── auth/
│       │   │   │   │   └── AuthScreen.kt            # Google Sign-In via Jetpack Credential Manager
│       │   │   │   ├── i18n/
│       │   │   │   │   └── AppStrings.kt            # English (EN) & Tamil (தமிழ்) localization state & helper
│       │   │   │   ├── owner/
│       │   │   │   │   └── OwnerDashboardScreen.kt  # Owner Shift Ledger, Expected Cash in Drawer, Audit Reports, & Tariff editor
│       │   │   │   ├── scanner/
│       │   │   │   │   └── CameraAnprScreen.kt      # CameraX viewfinder, Entry/Exit toggle, RTO prefix bar, Exit Billing & Pass dialogs
│       │   │   │   ├── theme/
│       │   │   │   │   ├── Color.kt                 # Outdoor high-contrast dark color tokens
│       │   │   │   │   ├── Theme.kt                 # Material 3 theme configuration
│       │   │   │   │   └── Type.kt                  # Bundled Inter, Space Grotesk, & JetBrains Mono typography
│       │   │   │   └── viewmodel/
│       │   │   │       └── ParkingViewModel.kt      # UI state management, role/PIN guard, connectivity sync, & AI tasks
│       │   │   ├── util/
│       │   │   │   ├── AppCheckHelper.kt            # Firebase App Check setup
│       │   │   │   └── NetworkSyncMonitor.kt        # ConnectivityManager observer for auto-flushing offline queue
│       │   │   └── vision/
│       │   │       ├── AnprAnalyzer.kt              # CameraX frame analyzer, Y-plane luminosity, LiteRT hook, ML Kit OCR, & Regex
│       │   │       └── PhotoWatermarkUtil.kt        # Compresses snapshot & burns timestamp + plate watermark
│       │   └── res/
│       │       ├── drawable/                        # Adaptive launcher icon & visual assets
│       │       ├── font/                            # Bundled Google Fonts (.ttf)
│       │       └── values/
│       │           ├── strings.xml
│       │           └── firebase_applet_config.xml
│       └── test/java/com/example/                   # Unit & Robolectric Firestore rules tests
├── firebase-blueprint.json
├── firestore.rules
└── firestore.rules.spec.js
```

---

## Getting Started & Building

### 1. Prerequisites
- Android Studio Ladybug (or newer) / JDK 17+
- Android SDK 36 (`minSdk = 24`, `targetSdk = 36`)

### 2. Optional Custom YOLOv8 `.tflite` Plate Detector
- By default, `AnprFrameAnalyzer` runs full-frame offline ML Kit Text Recognition with Indian plate Regex validation.
- To enable custom YOLOv8 bounding-box plate cropping before OCR, place your quantized or float16 model at:
  `app/src/main/assets/yolov8n_plate.tflite`
  `AnprFrameAnalyzer` automatically detects the model at startup and runs it via the 16 KB page-aligned **LiteRT Interpreter**.

### 3. Build & Run Unit Tests
```bash
gradle assembleDebug
gradle :app:testDebugUnitTest
```

### 4. Exporting / Pushing to GitHub
- In **Google AI Studio**, click the **GitHub / Export** icon in the top-right toolbar (or open the **Settings** menu) to connect your GitHub account and push this repository directly to a new or existing GitHub repo.
- If exporting as a ZIP to push from your local machine:
  ```bash
  git init
  git add .
  git commit -m "Initial commit: MotoVault ANPR Two-Wheeler Parking Management App"
  git branch -M main
  git remote add origin https://github.com/<your-username>/<your-repo>.git
  git push -u origin main
  ```
