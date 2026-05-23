# Aegis

A citizen-owned, local-first personal health profile system for Android.

Aegis solves fragmented health records by letting patients own, store, and share their medical history entirely on-device — no server, no Aadhaar, no hospital registration required. Documents are processed by an on-device multimodal LLM, encrypted at rest, and shared only on explicit user action.

M.Tech Integrated CSE (MID) capstone project (TARP) at VIT Vellore.  
Authors: K. R. Balasubramanian (22MID0098), Ahammed Ruzaim T.K. (22MID0337)  
Advisor: Dr. Sendhil Kumar K.S.

---

## Key differentiators from ABDM

- No Aadhaar or government ID required
- No hospital registration or onboarding friction
- Fully offline — no network dependency for core features
- Patient controls all data sharing explicitly, per export

---

## Features

- **Encrypted storage** — Room database encrypted with SQLCipher; key protected by Android Keystore
- **Biometric lock** — BiometricPrompt gate on every app open
- **Document vault** — Upload prescriptions, lab reports, discharge summaries, scans, insurance docs; view by type, condition, provider, or date
- **On-device AI extraction** — Gemma 4 E2B via LiteRT-LM reads document images directly (no OCR) and outputs structured JSON: lab values, medications, conditions, provider, date
- **Health profile** — Conditions, medications, allergies, blood type; populated during onboarding and kept current
- **Visit log** — Group documents by visit (provider + date + conditions + notes) — in progress
- **QR emergency snapshot** — Blood type, allergies, current medications; no network required
- **Selective PDF export** — User picks exactly which records leave the device per export

---

## Tech stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin | 2.2.0 |
| UI | Jetpack Compose + Material 3 | BOM 2026.02.01 |
| DI | Hilt | 2.57 |
| Database | Room + SQLCipher | 2.7.1 / 4.5.4 |
| On-device LLM | LiteRT-LM | 0.12.0 |
| Auth | AndroidX Biometric | 1.2.0-alpha05 |
| Async | Kotlin Coroutines | 1.9.0 |
| Build | AGP + KSP | 8.7.3 / 2.2.0-2.0.2 |
| Min SDK | Android 8.0 | API 26 |
| Target SDK | Android 15 | API 35 |

---

## Architecture

Single-activity MVVM. One `NavController` manages all destinations; `BottomNavBar` is shown only on the four main tabs (Home, Vault, Visits, Export). ViewModels are injected per-screen via `hiltViewModel()`.

The ML pipeline:
1. User picks an image or PDF from storage
2. `GemmaExtractionService` decodes the image to a PNG bitmap and passes it directly to Gemma 4 E2B via the LiteRT-LM multimodal API
3. Gemma returns a structured JSON object (document type, provider, lab values, medications, conditions, summary)
4. Parsed fields are stored in the encrypted Room database and surfaced in the document detail screen

---

## Build setup

**Prerequisites**

- Android Studio Hedgehog or later
- A physical device running Android 8.0+ (Gemma requires a GPU; emulators will not work)
- The model file pushed to the device (see below)

**Model file**

Download `gemma4e2b.litertlm` from `huggingface.co/litert-community/gemma-4-E2B-it-litert-lm` and push it to the device:

```
adb push gemma4e2b.litertlm /sdcard/Android/data/com.example.aegis/files/gemma4e2b.litertlm
```

The model file is not included in this repository (3+ GB binary).

**Build**

```
./gradlew assembleDebug
```

No API keys or network configuration required for the core app. The Gemini API key (Phase 11 chatbot only) will be documented when that phase ships.

---

## Project status

| Phase | Scope | Status |
|---|---|---|
| 1 | Project structure, Hilt, NavGraph, Material 3 theme | Done |
| 2 | Encrypted DB — Room + SQLCipher, all entities and DAOs | Done |
| 3 | Biometric lock — Keystore key, BiometricPrompt, lock/unlock flow | Done |
| 4 | Onboarding — multi-step form, profile/conditions/medications/allergies | Done |
| 5 | Document vault UI — upload, list, detail views with extracted data display | Done |
| 6 | Gemma 4 E2B image extraction via LiteRT-LM, multimodal, no OCR | Partial — session conflict fix pending |
| 7 | Document classifier | N/A — Gemma infers document type as part of extraction |
| 8 | Visit log — add, list, detail screens | Pending |
| 9 | Export — QR generator, selective PDF, share UI | Pending |
| 10 | Drug interaction checker | Pending |
| 11 | Chatbot — on-device RAG + Gemini API | Pending |
| 12 | Encrypted cloud backup | Pending |
