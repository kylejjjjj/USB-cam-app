# USB Webcam Viewer for Android

A native Android app that displays live video from **two or more USB webcams** simultaneously using the UVC (USB Video Class) protocol.

---

## Features

- 🎥 **Multi-camera support** — display 2, 3, 4+ USB webcams at once
- 📐 **Smart grid layout** — auto-adjusts columns based on camera count
- 🔌 **Hot-plug detection** — cameras are detected on plug/unplug
- 🔒 **USB permission handling** — prompts user to grant access per device
- 📟 **Status indicators** — live/offline indicator per camera feed
- 🔄 **Retry on error** — per-camera retry button

---

## Hardware Requirements

| Requirement | Details |
|---|---|
| Android device | Android 5.0+ (API 21+) |
| USB OTG support | Required (most modern phones/tablets) |
| USB Hub (optional) | For 3+ cameras, use a powered USB hub |
| Webcams | UVC-compatible (most USB webcams) |

> ⚠️ **Not all Android devices support USB Host mode.** Check your device specs.  
> 💡 **Recommended:** Use a powered USB hub for 2+ cameras to avoid power issues.

---

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11+
- Android SDK with API 34

### Steps

1. **Clone / open the project** in Android Studio

2. **Sync Gradle** — it will pull the `libausbc` UVC camera library from JitPack

3. **Enable USB Debugging** on your Android device  
   *(Settings → Developer Options → USB Debugging)*

4. **Build & Run:**
   ```
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## How to Use

1. Connect a USB OTG adapter to your Android device
2. Plug in one or more USB webcams (or use a USB hub)
3. Open **USB Webcam Viewer**
4. When prompted, tap **"OK"** to grant USB permission for each camera
5. Live previews appear in a grid layout
6. Tap **Scan** to re-detect cameras after plugging in new ones
7. Tap **Grid/Stack** to toggle layout mode

---

## Project Structure

```
app/src/main/
├── java/com/example/usbwebcam/
│   ├── MainActivity.kt          # Main UI, USB permission handling
│   ├── UsbCameraManager.kt      # Camera open/close, device scanning
│   ├── CameraAdapter.kt         # RecyclerView adapter for camera grid
│   └── CameraGridLayoutManager.kt  # Dynamic grid span logic
├── res/
│   ├── layout/
│   │   ├── activity_main.xml    # Main layout with grid + status bar
│   │   └── item_camera.xml      # Single camera cell layout
│   └── xml/
│       └── device_filter.xml    # USB device filter (UVC class 0x0E)
└── AndroidManifest.xml
```

---

## Dependencies

| Library | Purpose |
|---|---|
| `com.github.jiangdongguo.AndroidUSBCamera:libausbc:3.3.3` | UVC USB camera driver |
| `androidx.recyclerview` | Camera grid |
| Material Components | UI |
| Kotlin Coroutines | Async camera state updates |

---

## Troubleshooting

| Issue | Solution |
|---|---|
| No cameras detected | Ensure webcam is UVC-compatible; try a different USB cable |
| Black preview | Grant USB permission when prompted; try unplugging and replugging |
| App crashes on open | Check USB OTG is enabled in your device settings |
| Only 1 camera works | Use a **powered** USB hub; unpowered hubs may not supply enough current |
| Permission dialog doesn't appear | Tap "Scan" button to manually trigger detection |

---

## UVC Compatibility

This app supports any webcam that implements the **USB Video Class (UVC)** standard, including:
- Logitech C270, C310, C920, C922
- Microsoft LifeCam series
- Razer Kiyo
- Generic USB webcams
- Industrial USB cameras (most models)

---

## License

MIT License — free to use and modify.
