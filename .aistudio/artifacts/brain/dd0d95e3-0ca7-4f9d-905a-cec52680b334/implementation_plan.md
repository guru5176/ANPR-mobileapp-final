# MotoVault ANPR — Two-Wheeler Parking & Cash Audit System

MotoVault ANPR is a high-contrast, offline-first and cloud-synced Android application designed for 24/7 commercial two-wheeler parking stands. It replaces manual paper notebooks with automated license plate recognition (ANPR), burned-in timestamp photo proof, strict 24-hour rolling fee calculation, "Pay Tomorrow" (Udhaar) debt tracking, pass management, and a live anti-theft Owner cash drawer ledger.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and product choices have been confirmed from your answers and integrated into this plan:

- **Confirmed Decision 1 — Role Access & Security**: Users authenticate via **Google Sign-In** (Jetpack Credential Manager), and switching from the **Worker Mode** to the **Owner / Admin Mode** is protected by an **Owner Security PIN** (default `1234`, configurable by the Owner) so workers cannot alter tariff rates, delete logs, or tamper with audit records.
- **Confirmed Decision 2 — Hybrid ANPR + Gemini Vision Pipeline**: The live CameraX scanner combines a **YOLOv8 `.tflite` plate bounding-box detector hook** (with automatic center-ROI crop fallback when no custom `.tflite` file is loaded in assets) + **Google ML Kit Text Recognition v2 (Offline)** + **Indian License Plate Regex validation**, backed by **Gemini Vision (`gemini-4-argon`)** photo analysis for rusted, mud-covered, or stylized "ghost" plates.
- **Confirmed Decision 3 — 24-Hour Rolling Billing Math**: Parking duration is calculated in strict **24-hour rolling blocks** from the exact `entryTimestamp` (`ceil(elapsedMillis / 86_400_000.0)`, minimum 1 day charge) with automatic addition of any prior unpaid credit (`UNPAID_CREDIT`) and automatic ₹0 daily fee exemption for active Weekly or Monthly Pass holders.

---

### 1. Overview & Core Concept

- **What It Does**: Provides a dual-role mobile terminal for commercial bike parking stands. **Workers** rapidly log vehicle entries and exits using live camera ANPR or a one-tap local RTO keypad, capture mandatory timestamp-watermarked vehicle photos, issue Weekly/Monthly passes, and record Cash, UPI, or "Pay Tomorrow" (Credit) payments. **Owners** monitor live shift metrics, expected physical cash in the drawer, pending customer credits, entry photo proofs, and AI-powered fraud/anomaly audits synced in real time across devices.
- **Target Audience / Persona**:
  - **Parking Stand Workers**: Need a rugged, one-handed, sunlight-readable interface with large touch targets, automatic low-light torch activation, and instant plate entry for peak commuter rush hours.
  - **Parking Stand Owners / Admins**: Need tamper-proof visibility into how many bikes are inside, exact expected cash in the drawer vs. UPI collections, unpaid customer debts, and protection against worker cash skimming or duration disputes.
- **Key Value**: Eliminates revenue leakage from unrecorded entries, under-reported multi-day stays, forgotten "Pay Tomorrow" debts, and mixing of UPI and physical cash.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Authentication & Role Gate**: On launch, users sign in with Google via Jetpack Credential Manager. The app opens in **Worker Mode** by default. Tapping the **Owner Shield** icon prompts for the 4-digit Owner PIN to unlock the **Owner Dashboard & Rate Config**.
  2. **Live ANPR Entry & Exit Scanner**:
     - Full-width CameraX viewfinder with a high-visibility neon targeting reticle, real-time luminosity meter, automatic low-light Torch trigger, and manual Torch override button.
     - As frames stream through the analyzer, detected plates matching `^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$` automatically populate the plate display banner and trigger an instant lookup of the vehicle's current state (`INSIDE`, `VALID PASS`, or `PREVIOUS PENDING BALANCE`).
     - For damaged or non-standard "ghost" plates, the worker can tap **RTO Quick-Prefix Chips** (`TN39`, `TN42`, `TN33`, `TN37`, `TN38`, `TN66`) to complete manual entry in seconds, or tap **Gemini Vision Scan** (via camera capture or Android Photo Picker) to let `gemini-4-argon` read stylized/angled plates from an image.
  3. **Vehicle Entry Flow & Burned-In Photo Proof**:
     - If the scanned plate has an unpaid credit (`UNPAID_CREDIT`), a flashing high-contrast red alert banner displays: `"PREVIOUS PENDING BALANCE: ₹[Amount] from [Date]"`.
     - If the plate holds an active Weekly/Monthly Pass, an emerald badge shows: `"VALID PASS — X DAYS LEFT (₹0 Daily Fee)"`.
     - Tapping **Log Vehicle Entry** captures a compressed snapshot, burns the exact date/time and plate number onto the bottom of the image canvas, saves it locally, and syncs the record to Room and Firestore.
  4. **Vehicle Exit & Non-Negotiable Billing Sheet**:
     - Scanning or selecting a currently parked (`INSIDE`) vehicle opens the **Exit & Settlement Sheet** displaying the original burned-in entry photo thumbnail, exact entry timestamp, elapsed 24-hour blocks, base parking fee, any carried-over `UNPAID_CREDIT` debt, and total non-negotiable amount due.
     - Worker selects one of three settlement actions:
       - **Paid — Cash**: Increments the expected physical Cash Drawer total.
       - **Paid — UPI / GPay**: Logs digital collection separately so physical cash totals remain pure.
       - **Pay Tomorrow (Credit / Udhaar)**: Logs the exit and attaches/increments an `UNPAID_CREDIT` record against the license plate for automatic recovery on their next visit.
  5. **Pass Registration & Renewal Flow**:
     - Workers or Owners can register or renew a **7-Day Weekly Pass** or **30-Day Monthly Pass** for any plate and record whether the pass fee was paid via **Cash** (added to Cash Drawer) or **UPI**.
  6. **Owner Anti-Theft Dashboard & High-Thinking Audit**:
     - Displays real-time KPI cards: **Expected Cash in Drawer**, **Bikes Currently Inside**, **Exits Today**, **UPI Collected**, and **Total Pending Credits**.
     - Includes a chronological **Live Shift Audit Ledger** with entry photo thumbnails, filterable by status (`Inside`, `Paid Cash`, `Paid UPI`, `Credit`, `Pass`), plus tariff rate configuration and a **Gemini Deep Audit (`gemini-4-argon` High Thinking)** panel that inspects shift logs for anomalies, long-overstay vehicles, and credit risk.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Industrial Night-Ops Utilitarian** — engineered for outdoor glare during midday sun and low-glare legibility during night shifts.
  - *Color Palette & Mood*:
    - Background Surface: Deep Asphalt Obsidian (`#0B0F14`) and Elevated Slate Card (`#151C24`)
    - Primary Accent: High-Visibility Safety Amber / Gold (`#FFB300`) for primary actions, targeting reticles, and license plate badges
    - Semantic Status Tokens:
      - Cash / Valid Pass Emerald (`#00E676`)
      - UPI Digital Cyan (`#00B0FF`)
      - Debt / Overdue Alert Crimson (`#FF3D00`)
      - Text Hierarchy: Pure Crisp White (`#F8FAFC`) for primary metrics and plates; Muted Steel (`#94A3B8`) for secondary labels
  - *Typography & Hierarchy*:
    - **Plate & Metric Display**: Bundled `JetBrains Mono` / `Roboto Mono` (`font-util`) with bold tabular numerals and wide letter-spacing so license plates (`TN39 AB 1234`) and rupee amounts (`₹140`) are unmistakable at arm's length.
    - **UI & Headlines**: Bundled `Space Grotesk` and `Inter` for crisp, high-legibility industrial headers and action buttons.
  - *Component Styling & Layout*:
    - High-contrast 1.5dp bordered cards for active vehicle status and debt alerts.
    - Minimum `48dp` to `56dp` touch targets on all scanner controls, RTO prefix chips, and payment buttons.
    - Adaptive layout supporting both compact handheld phones (bottom navigation bar) and wider tablets/foldables (two-pane scanner + live ledger).

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Offline-First Room Database + Real-Time Firestore Bi-Directional Sync**
  - *Chosen Approach*: Every Entry, Exit, Pass, Credit, and Rate Config is written immediately to the local **Room SQLite database** for zero-latency offline operation at the parking stand, and synchronized with **Firebase Firestore** (scoped under the authenticated user's parking stand workspace) using real-time snapshot listeners.
  - *Why*: Parking stands often have patchy cellular reception; workers cannot wait for network round-trips during rush-hour bike queues, while the Owner still gets instant real-time updates whenever connectivity is available.
  - *Alternatives Considered*: Cloud-only Firestore reads/writes (rejected due to potential latency in offline basements/sheds) or Room-only local storage (rejected because the Owner needs live remote visibility).

- **Decision 2: Resilient 3-Tier ANPR Architecture (TFLite + ML Kit OCR + Gemini Vision)**
  - *Chosen Approach*:
    1. **Tier 1 (Real-Time Frame Analyzer)**: Runs luminance analysis (`Y` plane of `YUV_420_888`) to auto-enable the flashlight in low light, passes the frame through a `YoloPlateDetector` (which loads a custom YOLOv8 `.tflite` model from assets if present, or applies a smart aspect-ratio plate ROI crop), and feeds the cropped bitmap into **Google ML Kit Text Recognition v2 (Offline)** with Indian RTO regex filtering (`^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$`).
    2. **Tier 2 (AI Vision Assistance via `gemini-4-argon` & `gemini-3.5-flash-lite`)**: For mud-splattered, hand-painted, or two-line plates, tapping **AI Plate Read** or uploading a photo via the Android Photo Picker invokes `gemini-4-argon` multimodal image understanding to extract the plate number and vehicle description, while `gemini-3.5-flash-lite` provides instant low-latency RTO district identification and fuzzy OCR correction.
    3. **Tier 3 (One-Tap RTO Prefix Bar)**: Instant buttons (`TN39`, `TN42`, `TN33`, `TN37`, etc.) let the worker complete a plate in 4 digit taps if a bike has no front plate.
  - *Why*: Guarantees 100% operational reliability in the field regardless of plate condition or lighting.

- **Decision 3: Burned-In Timestamp Watermarking on Entry Snapshots**
  - *Chosen Approach*: Upon vehicle entry, the captured frame is compressed and drawn onto a mutable `Bitmap` canvas where a high-contrast dark footer bar with the plate number, exact date/time (`dd MMM yyyy, hh:mm:ss a`), and stand ID is permanently burned into the pixels before saving to app storage.
  - *Why*: Customers frequently dispute multi-day charges claiming they parked "just this morning." Showing the burned-in timestamp photo on the Exit billing sheet resolves disputes immediately.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        UI LAYER (Jetpack Compose M3)                        │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌─────────────────────┐  │
│  │   Auth & Role Gate   │ │  CameraAnprScreen    │ │OwnerDashboardScreen │  │
│  │ (Google Sign-In +    │ │ (CameraX + Torch +   │ │(Cash Drawer Audit + │  │
│  │  Owner PIN Modal)    │ │  RTO Bar + Billing)  │ │ Rates + HighThink)  │  │
│  └──────────┬───────────┘ └──────────┬───────────┘ └──────────┬──────────┘  │
└─────────────┼────────────────────────┼────────────────────────┼─────────────┘
              │                        │                        │
              ▼                        ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     VIEWMODEL & DOMAIN STATE LAYER                          │
│  ┌──────────────────────────────────┐  ┌─────────────────────────────────┐  │
│  │        ParkingViewModel          │  │          AiAuditViewModel       │  │
│  │ (Scanner State, Entry/Exit Flow, │  │ (Gemini 4 Argon Image Analysis, │  │
│  │  Pass Check, Debt Banner, Shift) │  │  Flash-Lite RTO, High Thinking) │  │
│  └─────────────────┬────────────────┘  └─────────────────┬───────────────┘  │
└────────────────────┼─────────────────────────────────────┼──────────────────┘
                     │                                     │
                     ▼                                     ▼
┌──────────────────────────────────────────┐ ┌────────────────────────────────┐
│       BILLING & SYNC REPOSITORY          │ │    AI & VISION PIPELINE        │
│ ┌──────────────────────────────────────┐ │ │ ┌────────────────────────────┐ │
│ │         BillingRepository            │ │ │ │ YoloPlateDetector (.tflite)│ │
│ │ • 24h Rolling Block Fee Calculator   │ │ │ │ + ML Kit Text Recognition  │ │
│ │ • Active Pass Validator (0 Fee)      │ │ │ │ + Luminosity Torch Analyzer│ │
│ │ • Pay Tomorrow Debt Accumulator      │ │ │ └────────────────────────────┘ │
│ │ • Expected Cash-in-Drawer Aggregator │ │ │ ┌────────────────────────────┐ │
│ └──────┬────────────────────────┬──────┘ │ │ │     Firebase AI Logic      │ │
│        ▼                        ▼        │ │ │ • gemini-4-argon (Vision & │ │
│ ┌──────────────┐      ┌────────────────┐ │ │ │   ThinkingLevel.HIGH)      │ │
│ │ Room SQLite  │◄────►│   Firestore    │ │ │ │ • gemini-3.5-flash-lite    │ │
│ │ (Offline DB) │ Sync │ (Live Cloud)   │ │ │ │ • gemini-3.8-flash         │ │
│ └──────────────┘      └────────────────┘ │ │ └────────────────────────────┘ │
└──────────────────────────────────────────┘ └────────────────────────────────┘
```

#### Data Model & State

1. **`VehicleLogEntity` (Room `@Entity(tableName = "vehicle_logs")` & Firestore `/users/{uid}/vehicle_logs/{logId}`)**:
   - `id: String` (Primary Key / UUID)
   - `plateNumber: String` (Indexed, normalized uppercase e.g. `TN39AB1234`)
   - `entryTimestamp: Long` (Epoch millis)
   - `exitTimestamp: Long?` (Null while `status == "INSIDE"`)
   - `photoPath: String` (Local path to compressed snapshot with burned-in timestamp)
   - `status: String` (`"INSIDE"`, `"EXITED"`)
   - `paymentStatus: String` (`"PENDING"`, `"PAID_CASH"`, `"PAID_UPI"`, `"UNPAID_CREDIT"`, `"PASS_COVERED"`)
   - `daysCharged: Int` (Calculated 24-hour blocks)
   - `baseFee: Double` (Calculated parking fee for this stay)
   - `previousCreditAdded: Double` (Prior debt bundled into this exit bill)
   - `totalAmountDue: Double` (`baseFee + previousCreditAdded`)
   - `amountCollected: Double` (Actual amount collected on exit)
   - `workerName: String` (Identifier of logged-in operator)

2. **`PassEntity` (Room `@Entity(tableName = "passes")` & Firestore `/users/{uid}/passes/{plateNumber}`)**:
   - `plateNumber: String` (Primary Key)
   - `passType: String` (`"WEEKLY_PASS"`, `"MONTHLY_PASS"`)
   - `startDate: Long` (Epoch millis)
   - `expiryDate: Long` (Epoch millis)
   - `amountPaid: Double`
   - `paymentMode: String` (`"PAID_CASH"`, `"PAID_UPI"`)
   - `createdAt: Long`

3. **`CreditEntity` (Room `@Entity(tableName = "credits")` & Firestore `/users/{uid}/credits/{plateNumber}`)**:
   - `plateNumber: String` (Primary Key)
   - `pendingAmount: Double`
   - `lastUnpaidTimestamp: Long` (Epoch millis of when the credit was incurred)
   - `notes: String`
   - `isSettled: Boolean`

4. **`RateConfig` (Room `@Entity(tableName = "rate_config")` & Firestore `/users/{uid}/config/rates`)**:
   - `id: Int = 1`
   - `dailyRate: Double = 20.0` (Default ₹20 / 24h block)
   - `weeklyPassRate: Double = 100.0`
   - `monthlyPassRate: Double = 350.0`
   - `ownerPin: String = "1234"`
   - `lowLightThreshold: Double = 45.0` (Average Y-plane pixel luminance threshold for auto-torch)

#### Interactive Component & State Mapping

- **Camera & Luminosity Analyzer (`CameraAnprScreen`)**:
  - Extracts the `Y` plane byte buffer from each `ImageProxy` (`YUV_420_888`), computes average pixel brightness (`0..255`), and updates `currentLuminosity`. When `autoTorchEnabled && currentLuminosity < rateConfig.lowLightThreshold`, calls `camera.cameraControl.enableTorch(true)`.
  - Crops the candidate plate region via `YoloPlateDetector` and runs `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`. Raw text is stripped of spaces/hyphens, common OCR character confusions (`O`↔`0`, `I`↔`1` in numeric/alpha positions) are normalized, and the string is validated against `^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$`.
- **Instant Lookup & Alert Banner (`BillingRepository`)**:
  - Whenever the plate field changes (via ANPR, RTO prefix tap, or Gemini Vision), `BillingRepository.inspectPlateStatus(plate)` runs reactively to return:
    - Active `VehicleLogEntity` if the bike is currently `INSIDE` (switches primary CTA to **Process Exit & Bill**).
    - Active `PassEntity` with remaining days (`VALID PASS — X DAYS LEFT`) or expired notice.
    - Active `CreditEntity` if `pendingAmount > 0` (triggers the red **PREVIOUS PENDING BALANCE** warning banner).
- **Exact Exit & Cash-in-Drawer Math (`BillingRepository`)**:
  - `daysElapsed = max(1, ceil((exitTimestamp - entryTimestamp) / 86_400_000.0).toInt())`
  - `baseFee = if (hasValidPass) 0.0 else daysElapsed * rateConfig.dailyRate`
  - `totalDue = baseFee + existingCredit.pendingAmount`
  - Selecting **Paid — Cash** marks `paymentStatus = "PAID_CASH"`, clears `CreditEntity` for that plate, and increments **Expected Cash in Drawer** (`sum(PAID_CASH vehicle exits today) + sum(PAID_CASH passes sold today)`).
  - Selecting **Pay Tomorrow (Credit)** marks `paymentStatus = "UNPAID_CREDIT"` and upserts `CreditEntity(plateNumber, pendingAmount = totalDue, lastUnpaidTimestamp = exitTimestamp)`.
- **Gemini AI Integration (`AiAuditViewModel`)**:
  - **Image Analysis (`gemini-4-argon`)**: Accepts a camera frame or gallery photo (`PickVisualMedia`) and returns structured JSON with `plateNumber`, `vehicleDescription`, `plateCondition`, and `confidence`.
  - **Fast RTO & Plate Helper (`gemini-3.5-flash-lite`)**: Instant low-latency lookup of Tamil Nadu / Indian RTO codes and voice/shorthand plate normalization.
  - **Owner Deep Fraud & Revenue Audit (`gemini-4-argon` with `ThinkingLevel.HIGH`)**: Analyzes the shift's entries, exits, cash-to-UPI ratio, and credit accounts to flag suspicious patterns (e.g., excessive "Pay Tomorrow" credits or unusually short/long stays) for the Owner.
