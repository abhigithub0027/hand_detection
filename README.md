# Finger Detector

Android assessment project for palm and finger detection using CameraX and MediaPipe Hand Landmarker.

## Repository

GitHub repository: [hand_detection](https://github.com/abhigithub0027/hand_detection.git)

## Features

- Camera-driven palm and finger capture flow
- Live hand-side detection and finger counting
- Palm overlay and finger alignment overlay
- Brightness classification and on-device status guidance
- Palm record creation and simulated finger matching flow
- Validation for wrong hand, dorsal side, blur, and mismatch cases
- Result screen with saved captures and telemetry summary
- Export of generated data into `Pictures/Finger Data`

## Build And Run

### Requirements

- Android Studio Hedgehog or newer
- Android SDK 34
- JDK 17
- A physical Android device running Android 7.0+ with camera access

### Android Studio

1. Clone the repository:
   ```bash
   git clone https://github.com/abhigithub0027/hand_detection.git
   ```
2. Open the project in Android Studio.
3. Allow Gradle to sync and download dependencies.
4. Connect a real Android device and enable USB debugging.
5. Run the `app` configuration.
6. Grant camera and storage/media permissions when prompted.

### Command Line

1. Clone the repository:
   ```bash
   git clone https://github.com/abhigithub0027/hand_detection.git
   cd hand_detection
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows PowerShell use:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
3. The generated APK will be available at:
   `app/build/outputs/apk/debug/app-debug.apk`

## Approach

### Android

The Android app is built in Kotlin with a fragment-based flow and a shared `ViewModel` to keep palm capture, finger capture, validation, and results in one session state. CameraX is used for preview, capture, autofocus, and lightweight frame analysis. MediaPipe Hand Landmarker runs on-device to detect hand landmarks, from which the app derives hand side, finger count, palm-facing heuristics, and centered finger guidance. The captured outputs and telemetry are then stored and shown on the result screen.

### Platform Note

This repository currently contains the Android implementation only. There is no separate iOS module in this submission. To keep the solution portable, the hand-analysis logic is separated into focused vision and data layers, which would make it easier to reuse the same capture rules and validation flow in a future second-platform implementation.

## Challenges And Fixes

- Real-time detection can become unstable if every frame is processed, so frame analysis is throttled and limited to the latest image buffer.
- Camera behavior varies across devices, so autofocus and exposure compensation are applied defensively with fallbacks.
- The assignment expects palm and finger validation behavior without a full biometric backend, so a simulated matching layer was built from landmark geometry to demonstrate the complete flow.
- Hand orientation and dorsal-side checks are not directly provided by MediaPipe, so heuristic calculations were added on top of landmark positions and depth relationships.

## Tech Stack

- Kotlin
- Android Fragments
- ViewBinding
- ViewModel and StateFlow
- CameraX
- MediaPipe Tasks Vision Hand Landmarker

## Notes

- Best results are achieved on a physical device using the rear camera in stable lighting.
- Finger matching in this project is a simulated validation flow derived from hand landmark geometry and intended for assignment evaluation.
