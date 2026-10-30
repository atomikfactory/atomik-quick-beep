# Atomik Quick Beep 💳🔒

[![CI](https://github.com/atomikfactory/atomik-quick-beep/actions/workflows/ci.yml/badge.svg)](https://github.com/atomikfactory/atomik-quick-beep/actions/workflows/ci.yml)

A premium, privacy-first, on-device digital vault for loyalty cards, barcodes, QR codes, and scanned documents (ID cards, driver's licenses, and PDFs). Built natively for Android using Jetpack Compose, Room, CameraX, and hardware-backed keystore encryption.

---

## 🔒 Security Architecture (Privacy-First)

Atomik Quick Beep is designed under a **Zero-Trust** philosophy. It requires no accounts, no cloud sync, and keeps 100% of your data encrypted locally on your device.

* **Biometric Lockout**: Access to the vault is gated by Android's `BiometricPrompt` (Fingerprint/Face Unlock). The vault locks automatically when the app is backgrounded or after 5 minutes of inactivity.
* **Hardware-Backed Cryptography**: All document scans (JPEGs) and imported PDFs are encrypted using AES-GCM-256. Cryptographic keys are dynamically managed by the **Android KeyStore** system and mathematically bound to the user's secure lock screen authentication.
* **In-Memory Decryption**: Sensitive images and document streams are decrypted directly in-memory and rendered as Compose `ImageBitmap`s. Raw decrypted bytes are never cached or leaked to temporary files on disk.

---

## 🌟 Key Features

### 1. Vector Barcode Generator
* **Exact-Pixel Rendering**: Renders high-contrast, pure black-and-white barcodes using a custom ZXing canvas vector engine with a mandatory 10% quiet zone margin to guarantee immediate register scanning.
* **Supported Formats**: Code 128, Code 39, QR Code, EAN-13, EAN-8, UPC-A, UPC-E, PDF417, Aztec, and Data Matrix.

### 2. Full-Screen PDF Viewer
* **Direct Grid Access**: Tap a PDF item from your vault list to open it directly in a dedicated, full-screen interactive reader.
* **Smooth Zoom & Pan**: Pinch-to-zoom (up to 5x) and drag-to-pan with boundary constraints.
* **Gesture Interception**: Features a custom `PointerEventPass.Initial` listener that automatically disables vertical list scrolling when pinch-to-zoom gestures are active, preventing layout jank.
* **White-Canvas Compositing**: Automatically composites transparent PDF pages onto an opaque white canvas so that black watermarks and text remain fully legible.

### 3. High-Readability Document Viewer
* **Recto/Verso Sequential Pages**: Securely stores and renders two-sided documents (like driver's licenses or insurance cards) in a vertical scrolling layout.
* **Maximized Screen Real Estate**: Bleed-to-edge layout styling (minimized 4dp padding) ensures document scans occupy maximum screen width and height, making small text easy to read.

### 4. Portable Backup & Restore
* **Self-Contained Backups**: Export your entire vault (including encrypted JPEGs and PDFs) into a portable JSON backup file. Images are decrypted on-the-fly, converted to secure Base64 segments, and packaged into the JSON format.
* **Secure Restoring**: Restoring a backup on a different device decrypts the Base64 payloads and re-encrypts them using the new device's local hardware KeyStore, ensuring seamless portability.

### 5. Tag & Category Management
* Group and filter your vault items dynamically with custom category chips.

---

## 🛠️ Technology Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material Design 3)
* **Local Storage**: Room Database (with SQL version migrations)
* **Local Cryptography**: `AndroidKeyStore` (AES/GCM/NoPadding)
* **Scanner Integrations**: Google ML Kit (Barcode Scanning & Google Play Services Document Scanner)
* **Generators**: ZXing (Zebra Crossing)

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio Koala** (or newer)
* **JDK 17** (or newer)
* **Android SDK 34** (Target SDK)
* **Device**: Android 8.0 (API 26) or newer with working biometrics

### Developer Build Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/atomikfactory/atomik-quick-beep.git
   cd atomik-quick-beep
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run on a connected device/emulator:
   ```bash
   ./gradlew installDebug
   ```

### Secure Release Signing
To generate a signed release APK without hardcoding passwords in Git:

1. Copy the example configuration template:
   ```bash
   cp keystore.properties.example keystore.properties
   ```
2. Populate `keystore.properties` with your local Java Keystore credentials:
   ```properties
   storeFile=path/to/release.jks
   storePassword=your_keystore_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```
3. Compile the signed release build:
   ```bash
   ./gradlew assembleRelease
   ```
   *Note: `keystore.properties` and `*.jks` files are ignored by `.gitignore` and will never be committed to your public repository.*

---

## 📦 Releases

### Download and install
1. Open the [Releases page](https://github.com/atomikfactory/atomik-quick-beep/releases) and download `atomik-quick-beep-<version>-debug.apk`.
2. Optionally verify it against `SHA256SUMS.txt` from the same release:
   ```bash
   sha256sum -c SHA256SUMS.txt --ignore-missing
   ```
3. Sideload the APK on a device running Android 8.0 (API 26) or newer.

*Note: release APKs are currently **debug-signed**, and the `.aab` is **unsigned** (reserved for a future Play Store release). Once a real release key is introduced, the signing key will change and you will need to uninstall the app before installing the new build, so back up your vault first.*

### Cutting a release (maintainers)
Versions are driven by git tags. Pushing a `vX.Y.Z` tag builds the app and publishes a GitHub Release:
```bash
git tag v1.2.3 && git push origin v1.2.3
```
The version name comes from the tag, and the version code is `major*10000 + minor*100 + patch`. You can reproduce a release build locally with `./gradlew assembleDebug bundleRelease -PappVersionName=1.2.3`.

To also publish a signed APK and AAB, add these repository secrets (the signed step is skipped if any is missing):
* `RELEASE_KEYSTORE_BASE64`: your keystore, base64-encoded
* `RELEASE_KEYSTORE_PASSWORD`
* `RELEASE_KEY_ALIAS`
* `RELEASE_KEY_PASSWORD`

---

## ⚖️ License

Governed by the **GNU General Public License v3 (GPL v3)**. See [LICENSE](./LICENSE) for details. Copycats or redistributors of modified versions of this application are legally required to open-source all modifications.
