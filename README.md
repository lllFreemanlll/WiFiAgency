# WiFi Body Detector

Android app that detects human movement behind walls using WiFi signal (RSSI) analysis.

## How It Works

The app scans nearby WiFi networks every 1.5 seconds and analyzes signal fluctuations using z-score anomaly detection. When a human body moves through walls, it disrupts WiFi signals, causing detectable anomalies in RSSI patterns.

## Build APK via GitHub Actions

### Step 1: Create a GitHub Repository

1. Go to https://github.com/new
2. Create a new repository (name it anything, e.g., `wifi-body-detector`)
3. Don't initialize with README

### Step 2: Push This Project

Open PowerShell and run:

```powershell
cd C:\Users\abdul\Desktop\WiAgency\WifiBodyDetector
git init
git add .
git commit -m "Initial commit: WiFi Body Detector app"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
git push -u origin main
```

Replace `YOUR_USERNAME` and `YOUR_REPO_NAME` with your actual GitHub username and repo name.

### Step 3: Wait for Build

1. Go to your repo on GitHub
2. Click **Actions** tab
3. The workflow will automatically build the APK
4. Takes ~3-5 minutes

### Step 4: Download APK

**Option A — Artifacts (quick):**
- Go to Actions > click the workflow run > scroll to **Artifacts**
- Download `wifi-body-detector-apk`

**Option B — Release (persistent):**
- Go to **Releases** on the right sidebar
- Download the APK from the latest release

## Install APK

1. Transfer the APK to your Android device
2. Enable "Install from Unknown Sources" in Settings
3. Open the APK and install

## Features

- Real-time RSSI signal monitoring
- Statistical anomaly detection (z-score based)
- Adjustable sensitivity slider
- Live signal chart with anomaly highlighting
- Dark theme UI
- Background scanning service

## Permissions Required

- WiFi access (scan networks)
- Location (required by Android for WiFi scanning)
- Notifications (for background service indicator)

## Limitations

- Uses RSSI (not CSI) — lower precision than research-grade systems
- Detects general movement, not precise positioning
- Best results with multiple strong WiFi networks in the area
- Wall material and thickness affect detection range
