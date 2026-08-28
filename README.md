<p align="center">
  <img width="120" height="120" alt="ChatGPT Image Jun 6, 2026, 03_23_56 PM" src="https://github.com/user-attachments/assets/257d7e4c-491f-4213-ae4a-601f0dde9166" />
</p>

<h1 align="center">Launch</h1>

<p align="center">
  A clean, efficient, and minimalist Android launcher built for focus and productivity.
</p>

<p align="center">
  <a href="https://deepwiki.com/guruswarupa/launch">
    <img src="https://deepwiki.com/badge.svg" height="45" />
  </a>
</p>

<!-- Primary distribution -->
<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.guruswarupa.launch">
    <img
      src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      height="80"
    />
  </a>

<!-- Alternative / OSS-friendly -->
<p align="center">
  <a href="https://f-droid.org/packages/com.guruswarupa.launchh/">
    <img
      src="https://f-droid.org/badge/get-it-on.png"
      height="80"
      alt="Get it on F-Droid"
    />
  </a>
</p>

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.guruswarupa.launch%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fguruswarupa%2Flaunch%22%2C%22author%22%3A%22guruswarupa%22%2C%22name%22%3A%22Launch%22%2C%22preferredApkIndex%22%3A0%2C%22overrideSource%22%3A%22GitHub%22%7D">
    <img
      src="https://github.com/user-attachments/assets/54fbe89c-d3ad-4bba-b613-2686688e567f"
      height="55"
      alt="Get it on Obtainium"
    />
  </a>
</p>

## 📋 Table of Contents

- [Features](#-features)
- [Getting Started](#-getting-started)
- [Development](#-development)
- [Contributing](#-contributing)
- [Privacy & Permissions](#-privacy--permissions)
- [License](#-license)

---

## ✨ Features

### 🔍 Smart App Management
- Usage-aware app list with weekly and daily usage insights
- Favorite apps pinned to the top of the list
- Hidden apps support with a dedicated hidden-apps settings page
- Long-press app actions for uninstall, app info, APK sharing, timers, favorites, and hide/unhide
- Grid and list layouts with customizable icon size/style, grayscale mode, and fast scroller
- Workspaces and work profile support for separating personal and work apps

### 🔎 Universal Search & Actions
- Search apps, contacts, and web results from a single search bar
- Built-in calculator in the search box
- Quick actions for Play Store, browser, YouTube, and Google Maps
- Contact actions for call, SMS, and WhatsApp
- Voice search and voice-command shortcuts

### 🔕 Focus, Locking & Usage Control
- Focus Mode with allowed-app configuration and session countdown
- Pomodoro support for timed work sessions
- App Lock with PIN and biometric unlock support
- Daily app limits, quick session timers, and over-limit visual dimming
- Automatic return-to-home behavior when time limits are reached

### 🧰 Widgets & Productivity Tools
- Todo widget with due times, priorities, recurring tasks, and alarms
- Calculator widget with basic, scientific, and converter modes
- Finance tracker widget with income/expense logging and transaction history
- Notes widget with editor support
- Calendar events widget with list and calendar views
- Countdown widget with manual timers and calendar-event countdowns
- Weather forecast widget with hourly and daily forecasts
- Workout widget and physical activity tracking widgets
- Media controller widget
- Year progress widget
- Battery and battery health widgets
- Device info and network stats widgets
- Compass, pressure, temperature, and noise meter widgets
- DNS / Private DNS widget with preset and custom providers
- GitHub contributions widget
- Standard Android third-party widget hosting

### 🌐 Web, Documents & Content
- Progressive-style web apps with icon fetching and settings management
- Built-in web app ad blocking support
- RSS feed page and feed settings
- Document viewer support for PDF, Office, and other common file formats
- APK sharing for installed apps

### 🔐 Privacy, Vault & Security
- Encrypted vault for protected files and private content
- Privacy dashboard activity
- App data disclosure screen
- Local-first settings/data backup support

### 📱 Sensors, Gestures & Automation
- Shake to toggle torch
- Back tap support
- Flip-to-DND service
- Screen dimmer and night mode services
- Screen recording shortcut flow
- Calendar, steps, temperature, pressure, compass, and ambient noise integrations where hardware is available

### 🎨 Customization
- Wallpaper customization
- Widget theming and translucency controls
- Downloadable font support and typography options
- Workspace configuration and control center shortcut configuration
- Interactive feature tutorial for onboarding

---

## 💰 Finance Tracker
- Add income and expense entries directly from the launcher
- Track monthly balance, savings, and recent transactions
- Add descriptions and review history from the widget UI
- Designed to work offline with local storage

---

## ⚙️ Settings Features
- Import/export launcher settings and local data
- Change display mode, icon appearance, and wallpaper
- Manage app locks, hidden apps, favorites, timers, and widgets
- Configure workspaces, control center shortcuts, web apps, RSS feeds, and vault settings
- Adjust weather location, background translucency, and privacy-related options

---

## 🤏 Gestures Guide

| Gesture                     | Result                                      |
|-----------------------------|---------------------------------------------|
| **Tap Time Widget**         | Opens Clock app                             |
| **Tap Date Widget**         | Opens Calendar app                          |
| **Long Press Search Bar**   | Opens Google in browser                     |
| **Long Press App Icon**     | Opens app context menu (Uninstall, Share, etc.)|
| **Long Press Dock App**     | Remove or rename app                        |
| **Type in Search Bar**      | Instant calculator                          |
| **Long Press Focus Icon**   | Enter Focus Mode setup                      |
| **Long Press Balance**      | View transaction history                    |
| **Shake Device (2x)**       | Toggle torch/flashlight                     |
| **Tap Weekly Usage Day**    | View detailed daily usage breakdown         |
| **Tap Hidden App**      | Launch hidden app from Hidden Apps settings |

---

## 🎙️ Voice Commands

Use voice in the search bar for fast interactions.

| Command Example           | Action                                         |
|---------------------------|------------------------------------------------|
| `Call Swaroop`            | Starts a phone call                           |
| `Message Swaroop`         | Opens SMS app                                 |
| `WhatsApp Swaroop`        | Opens WhatsApp chat                           |
| `Send hi to Swaroop`      | Sends WhatsApp message                        |
| `Search weather tomorrow` | Google search                                 |
| `Open YouTube`            | Launches YouTube                              |
| `Uninstall WhatsApp`      | Opens uninstall screen                        |
| `Bangalore to Mysore`     | Opens Google Maps with route                  |

> **Permissions Required**: Contacts and microphone.
---

## 🔐 Privacy & Permissions

Launch requires **minimal permissions**:
- **Contacts** → for contact search and quick actions
- **Calendar** → for calendar events and countdown integration
- **Storage / Media** → for notes, wallpapers, documents, vault files, and backups
- **Usage Stats** → for app usage tracking, daily limits, and sorting (optional)
- **Activity Recognition** → for steps and physical activity widgets (optional)
- **Audio** → for voice search and noise meter functionality (optional)
- **Camera** → for torch and related quick actions (optional)
- **Biometric** → for fingerprint-based app lock and vault access (optional)

All permissions are optional except for core launcher functionality. You can use the launcher with minimal permissions and enable additional features as needed.

---

## 🚀 Getting Started

1. Install the app  
2. Grant requested permissions  
3. Set "Launch" as your default launcher  
4. Choose your preferred layout (Grid/List)  
5. Add your favorite apps to the dock  
6. Start exploring and customizing  

---

## 🛠️ Development

### Prerequisites

- **Android Studio** (Hedgehog or later recommended)
- **JDK 11** or higher
- **Android SDK** with API level 26+ (minimum) and API level 35 (target)
- **Gradle** 8.10.2 (included via wrapper)

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/guruswarupa/launch.git
   cd launch
   ```

2. Open the project in Android Studio

3. Sync Gradle files and wait for dependencies to download

4. Build the project:
   ```bash
   ./gradlew build
   ```

5. Run on an emulator or device:
   ```bash
   ./gradlew installDebug
   ```

For detailed development setup instructions, see [DEVELOPMENT.md](DEVELOPMENT.md).

### Project Structure

```
launch/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/guruswarupa/launch/  # Main source code
│   │   │   ├── res/                          # Resources (layouts, drawables, etc.)
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/                      # Instrumented tests
│   │   └── test/                             # Unit tests
│   └── build.gradle.kts                      # App-level build config
├── gradle/
│   └── libs.versions.toml                    # Dependency versions
└── build.gradle.kts                           # Project-level build config
```

### Key Technologies

- **Kotlin** - Primary programming language
- **Android Views + RecyclerView** - Primary UI foundation
- **Jetpack Compose** - Used where applicable alongside the view-based UI
- **AndroidX** - Core Android support libraries
- **Material Components / Material 3** - UI components
- **Hilt** - Dependency injection
- **Glide** - Image loading
- **Apache POI** - Office document parsing

### Building for Release

```bash
./gradlew assembleRelease
```

The APK will be generated at `app/build/outputs/apk/release/app-release.apk`

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on:

- How to report bugs
- How to suggest features
- How to submit pull requests
- Code style and standards
- Development workflow

### Quick Start for Contributors

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Commit your changes (`git commit -m 'Add some amazing feature'`)
5. Push to the branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

For more details, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 📄 License

This project is licensed under the MIT License with additional attribution requirements. See the [LICENSE](LICENSE) file for details.

**Note**: This software may not be substantially modified, rebranded, or claimed as the work of others without explicit written permission. Derivative works must maintain proper attribution and the same license terms.
