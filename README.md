<p align="center">
  <img width="120" height="120" alt="Launch icon" src="https://github.com/user-attachments/assets/257d7e4c-491f-4213-ae4a-601f0dde9166" />
</p>

<h1 align="center">Launch</h1>

<p align="center">
  A clean, fast, privacy-first Android launcher built for focus and productivity.<br/>
  Free, open source, no ads, no subscriptions.
</p>

<!-- Primary distribution -->
<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.guruswarupa.launch">
    <img
      src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      height="80"
    />
  </a>

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.guruswarupa.launch%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fguruswarupa%2Flaunch%22%2C%22author%22%3A%22guruswarupa%22%2C%22name%22%3A%22Launch%22%2C%22preferredApkIndex%22%3A0%2C%22overrideSource%22%3A%22GitHub%22%7D">
    <img
      src="https://github.com/user-attachments/assets/54fbe89c-d3ad-4bba-b613-2686688e567f"
      height="55"
      alt="Get it on Obtainium"
    />
  </a>
</p>

<!-- WhatsApp Community -->
<p align="center">
  <a href="https://whatsapp.com/channel/0029VbEBgpf4inox5taK290F">
    <img
      src="https://img.shields.io/badge/Join%20our%20WhatsApp%20Channel-25D366?style=for-the-badge&logo=whatsapp&logoColor=white"
      alt="Join Launch on WhatsApp"
    />
  </a>
</p>

## Star History

<a href="https://www.star-history.com/?repos=guruswarupa%2Flaunch&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=guruswarupa/launch&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=guruswarupa/launch&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=guruswarupa/launch&type=date&legend=top-left" />
 </picture>
</a>

## 📋 Table of Contents

- [What is Launch?](#-what-is-launch)
- [Layouts and Home Screen](#-layouts-and-home-screen)
- [Pages and Navigation](#-pages-and-navigation)
- [Apps: Organize, Hide, Lock, Limit](#-apps-organize-hide-lock-limit)
- [Search, Voice and Smart Suggestions](#-search-voice-and-smart-suggestions)
- [Focus, Wellbeing and Parental Controls](#-focus-wellbeing-and-parental-controls)
- [Workspaces and Work Profile](#-workspaces-and-work-profile)
- [Widgets](#-widgets)
- [Web Apps, AI Assistant, RSS and Documents](#-web-apps-ai-assistant-rss-and-documents)
- [File Transfer](#-file-transfer)
- [Privacy, Vault and Backup](#-privacy-vault-and-backup)
- [System Tools and Automation](#-system-tools-and-automation)
- [Customization](#-customization)
- [Gestures Reference](#-gestures-reference)
- [Voice Commands](#-voice-commands)
- [Permissions Explained](#-permissions-explained)
- [Getting Started](#-getting-started)
- [Development](#-development)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🧭 What is Launch?

Launch replaces your Android home screen with a minimal, quick launcher that keeps the things you use every day one swipe away: your apps, a universal search bar, a set of useful widgets, and tools for focus and privacy. Everything works offline and locally by default. Features that need extra access (contacts, usage stats, sensors, accessibility) are opt-in and can be turned off.

**Principles**

- **Minimal** – a clean home screen, with extra pages you can switch on or off.
- **Private** – settings, notes, finance data, the vault and backups stay on your device.
- **Free and open source** – no ads and no paid tiers. Supporting the project is optional.
- **Fast** – a lightweight app list, cached icons and widgets that load one at a time so the launcher stays responsive.

---

## 🏠 Layouts and Home Screen

Pick how the launcher looks in **Settings → Appearance → Display style**.

### List layout
- Alphabetical app list with letter section headers and a **fast scroller** on the right edge (drag to jump to a letter, including `#`).
- **Favorites first:** the home screen shows only your favorites. Scroll past the end (or use the fast scroller) to reveal **all apps**, starting from **A**. Scroll back to the top to return to favorites.
- Option to hide app icons and show text only.

### Grid layout
- The same favorites / all apps flow, shown as an icon grid.
- Choose the number of columns and whether app names are shown under icons.

### Stock layout
A familiar phone-style home screen:
- **Home grid** with your chosen apps and **folders**, plus a **hotseat** (dock) with 3 to 5 slots.
- **App drawer** opened by swiping up (can be turned off), with its own search, a Focus filter and a Work filter.
- **Drag and drop:** reorder apps on the home grid, drop an app onto another app to create a **folder**, drag an app to the "Remove from Home" zone, or drag from the drawer to add it to Home. Drag apps out of a folder to take them out, or onto "Add to Home".
- Folders can be renamed, deleted (their apps stay) and opened as a popup. The popup closes when you launch an app from it.
- **Auto-organize folders** sorts your home apps into category folders (such as Social, Games, Finance) with one tap.
- **Stock gestures:** swipe down to open the notification shade, swipe right to open an app of your choice, and double-tap empty space to lock the screen.

### Home screen header (all layouts)
- **Clock and date** (tap the time to open your Clock app, tap the date to open Calendar), current **weather**, and today's **screen time**.
- **Search bar** with a search-type menu and a voice button.
- **Dock shortcuts:** Focus Mode, Workspaces and Work Profile toggles.
- **Notification badges** on app icons (optional; uses notification access).
- Optional **media controller** on the home screen (wallpaper or top widget), with synced lyrics when available.

---

## 📄 Pages and Navigation

Swipe left and right between full-screen pages. Pages wrap around, so swiping past the last one brings you back to the first.

| Page | What it shows |
|------|---------------|
| **AI assistant** (optional) | Chat with an on-device model or a web AI. Sits to the left of everything else. |
| **Wallpaper page** (list and grid layouts) | A clean view of your wallpaper |
| **Home** | Your apps, search and header |
| **Widgets** | All your widgets, reorderable by drag and drop |
| **RSS feed** (optional) | Your news feeds |

- Turn the Widgets, RSS and AI pages on or off in Settings.
- Choose which page the launcher opens to by default.
- The Widgets page can be locked so it stays out of reach (for example with Kids Mode).

---

## 📱 Apps: Organize, Hide, Lock, Limit

- **Favorites:** pin apps to the top. In stock layout they live on the home grid and hotseat.
- **Hidden apps:** hide apps from the list; reach them from the Hidden Apps settings page.
- **Long-press menu** on any app: app info, uninstall, share APK, set a timer, add or remove from favorites, hide or unhide.
- **Icons:** choose the shape (round, squircle, squared, teardrop, vortex, overlay), the size, an **icon pack**, or grayscale mode. Icons are cached and loaded in the background.
- **Usage-aware list:** shows how long you spent in each app today and learns from your habits (see Smart suggestions).
- **Notification badges** on icons (turn on in Settings).
- **App Lock:** protect apps with a 4 to 6 digit PIN, with **biometric unlock** support. Locks can follow a **schedule**, for example only on selected days and times.
- **App timers:** set daily limits and quick session timers. Over-limit apps are dimmed, and the launcher can send you back to the home screen when time runs out.
- **Web apps** (websites as apps) appear in the app list like normal apps.

---

## 🔎 Search, Voice and Smart Suggestions

- **One search bar** for apps, contacts, files and the web.
- **Search modes** from the menu: All, Apps, Contacts, Files. Quick actions search Google Maps, the web, the Play Store and YouTube.
- **Calculator in the search box:** type `12*8+5` and get the answer instantly.
- **Contact actions:** call, SMS and WhatsApp straight from a search result.
- **Search engine:** Google, Bing, DuckDuckGo, Ecosia, Brave, Startpage, and more.
- **Voice search** with spoken commands (see [Voice Commands](#-voice-commands)).
- **Smart suggestions:** ranks results and app suggestions using time of day, how often you open an app and how recently. Learned locally and never uploaded. Can be turned off.
- **AI assistant search:** ask natural-language questions from the search bar (see [AI Assistant](#ai-assistant)).

---

## 🔕 Focus, Wellbeing and Parental Controls

- **Focus Mode:** choose which apps stay available and hide everything else for a set session, with a countdown.
- **Pomodoro:** work and break timers (short and long breaks) with configurable lengths.
- **Daily app limits and timers:** see App timers above.
- **Usage stats:** daily and weekly screen time, with a per-day breakdown (tap a day in the weekly usage widget).
- **Usage Recap:** a weekly summary of total, daily and average screen time, your top apps and your most-launched apps. Find it under Settings → System Monitor.
- **Kids Mode:** shows only a set of allowed apps, locks Settings while active, and needs your App Lock PIN to exit.
- **Flip to Do Not Disturb:** put the phone face down to silence it.
- **Grayscale mode** and **extra dimmer** to make the screen less tempting at night.

---

## 💼 Workspaces and Work Profile

- **Workspaces:** named groups of apps (for example Work, Study, Travel). Switch from the dock to see only that group's apps.
- **Work profile:** create and manage an Android work profile to keep work and personal apps apart, switch work mode on or off from the launcher, and see work apps alongside your own. Work-mode state follows the real profile and updates when it starts or stops.

---

## 🧰 Widgets

All widgets live on the Widgets page. Turn each on or off, **reorder them by dragging**, resize system widgets, and change their translucency in **Settings → Widgets**.

**Productivity**
- **Todo list:** due times, priorities, recurring tasks and alarms.
- **Notes:** quick notes with a full editor.
- **Calculator:** basic, scientific and unit-converter modes.
- **Finance tracker:** log income and expenses, see your monthly balance, savings and transaction history (stored locally).
- **Calendar events:** list or calendar view.
- **Countdown:** manual timers and countdowns to calendar events.
- **Weather forecast:** hourly and daily forecasts with weather alerts (storm, snow, rain, heat, freeze).
- **GitHub contributions:** your contribution graph.
- **Year progress:** how much of the year has passed.

**Health and activity**
- **Workout:** start, stop and track workouts with list and calendar views and streaks.
- **Physical activity:** steps, distance and walking time.

**Device and sensors**
- **Battery health** and battery status.
- **Device info:** hardware and system details.
- **Network stats:** data usage and connectivity.
- **Compass**, **pressure**, **temperature** and **noise meter** (where your hardware has the sensors).
- **DNS widget:** switch Private DNS between Automatic, Off, Google, Cloudflare (including Security and Family), AdGuard (including Family), Quad9, CleanBrowsing or a custom provider.
- **System monitor:** live CPU and GPU graphs, memory, sensors, network and camera info.
- **Weekly usage** and **notifications** widgets.
- **Media controller:** play, pause and skip your current media, with lyrics.

**Android widgets:** host standard third-party Android widgets on the same page, with resize handles.

---

## 🌐 Web Apps, AI Assistant, RSS and Documents

### Web apps
Turn any website into an app in your list.
- **Add** by name and URL, or just search: typing a name such as `youtube` or `play console` offers matches instantly, and you can also search the web.
- **Share and import by QR code:** show a web app as a QR and scan it on another phone to add it.
- **Ad blocking:** blocks about 3,500 known ad and tracker domains, common ad URL patterns and pop-ups, and hides ad containers on pages. On YouTube it skips ads by speeding through and clicking "Skip".
- **Safe navigation:** HTTPS by default (HTTP is opt-in with a warning), an optional block on redirects to other domains, and trusted-domain handling.
- Icons are fetched automatically. File upload and fullscreen video are supported.

### AI assistant
- **On-device:** download a small open model (Qwen2.5 0.5B or 1.5B Instruct, Apache-2.0) and ask questions entirely offline. Downloads happen over Wi-Fi only and are verified. The assistant checks your device has enough memory and storage first.
- **Web AI:** or use ChatGPT, Claude, Gemini, Copilot, Perplexity or DeepSeek in a full-screen page.
- Shows as its own leftmost page and in search.

### RSS feeds
- A news page with your sources, topics and categories. Add and manage sources in **Settings → RSS Sources**.

### Documents and notes
- **Document viewer** that opens files fully offline, with no cloud conversion and no network access:
  - **Word:** `.docx`, `.docm`, `.dotx` with headings, bold, italic, underline, colors, lists and numbering, tables (merged cells and shading), images, hyperlinks, footnotes and page breaks.
  - **Excel:** `.xlsx`, `.xlsm` with multiple sheets as tabs, merged cells, column widths, cell colors and bold, and correctly formatted dates, numbers, percentages and currencies.
  - **PowerPoint:** `.pptx`, `.pptm`, `.ppsx` rendered as real slides with positioned text, shapes, pictures, tables, themes, backgrounds and speaker notes.
  - **OpenDocument and RTF:** `.odt`, `.ods`, `.odp` and `.rtf`.
  - **Text and data:** `.txt`, `.log`, `.srt`, `.csv` / `.tsv` as a spreadsheet grid, Markdown rendered with tables and code blocks (with a source view), HTML rendered safely (with a source view) and JSON pretty-printed.
  - **Code:** 50+ languages and config formats with syntax colors, line numbers and optional word wrap.
  - **PDF:** page-by-page viewing with a page counter.
  - **Images:** PNG, JPEG, GIF, WebP, BMP, ICO and SVG (with a source view) with pinch-to-zoom.
  - **Video and audio:** plays MP4, WebM, MKV, 3GP, MP3, WAV, OGG, FLAC, M4A and more inside the viewer, with playback controls.
  - **Archives and anything else:** ZIP, JAR, APK and EPUB show their contents; unknown binary files show a hex dump.
  - Older binary `.doc`, `.xls` and `.ppt` files show a basic text extraction and can be opened in another app.
  - Find in document, adjustable text size, light, dark and sepia reading themes, pinch to zoom, share and open-with.
  - Handles encodings automatically (UTF-8, UTF-16, Windows-1252) and large files by showing the first part.
  - Launch can also open these files when you choose it from another app.
- **Note editor** shared with the Notes widget.
- **APK sharing** for installed apps.

---

### Camera and Files

- **Launch Camera:** photo and video, front/back, flash (off/auto/on), 3s/10s timer, grid, and a last-capture thumbnail. Saves to DCIM/Camera.
- **Launch Files:** category shortcuts (Images, Videos, Audio, Documents, Downloads, installers, archives, recent), folder browsing, list/grid, sort, search, multi-select, copy, move, rename, share, new folder, ZIP compress/extract, favorites, and details. Deleted files go to a recycle bin for 30 days (restore, or empty it yourself) before being removed automatically. Files open in the built-in photo, video and document viewers. Files also includes a storage analyzer (treemap, folder breakdown, largest files, per-type totals). Camera, Files, Gallery and Video Player appear in the app list like normal apps.

## 📡 File Transfer

Send files between devices on the same network, with no accounts and no cloud (**Settings → File Transfer**).

- **Send** files or whole folders, picked with the system file picker.
- **Receive:** turn on receiving to make the device discoverable. A QR code and typed code are shown for pairing.
- **Secure:** every transfer uses mutual TLS with pinned device fingerprints. Pairing is verified by QR secret or by comparing a short code on both screens.
- **Safe receiving:** you approve each incoming transfer, files are checked and named safely, and nothing is overwritten. Received files go to **Downloads/Launch**.
- **History** of what you sent and received.

---

## 🔐 Privacy, Vault and Backup

### Vault
- **Encrypted vault** (AES-GCM, password-derived key) for private files, with thumbnails and a built-in viewer.
- **Change password** (re-encrypts everything), **auto-lock** timer, and **export / import** of the vault as a file.
- The vault locks when the screen turns off.

### Privacy Hub
- **Privacy dashboard** showing which apps hold which sensitive permissions.
- **App data disclosure:** an upfront screen explaining what the launcher accesses and why.

### Backup and restore
- **Export** your settings and local data (notes, finance, web apps, weather, activity data) to a single file.
- **Optional passphrase:** encrypt the backup with a passphrase (PBKDF2 + AES-GCM).
- **Optional vault inclusion:** include your encrypted vault files.
- **Import** on a new phone, including from the first-launch screen.
- **Theme sharing:** export your look (theme, layout, typography, stock folders and ordering) as a small file and import it elsewhere.
- Keystore-bound secrets (App Lock PIN, finance and GitHub tokens) are never included in Android's automatic cloud backup.

---

## 🛠️ System Tools and Automation

Many of these use the optional **accessibility service** or sensors, and you turn each one on yourself.

- **Control Center overlay:** a swipe-in panel with shortcuts for Wi-Fi, Bluetooth, airplane mode, torch, mobile data, auto-rotate, sound, Do Not Disturb, location, QR scan, camera, screenshot, screen record, lock, power menu, hotspot and screen timeout. You choose and reorder them.
- **Edge panel:** a handle on the screen edge opens a side panel with your pinned apps and recent apps. Set its position, size and transparency.
- **Double-tap to lock** the screen, and **lock / screenshot / notifications** actions via the accessibility service.
- **Shake for torch:** shake twice to toggle the flashlight.
- **Back tap:** double-tap the back of the phone to run an action: torch, screenshot, notifications, screen off or sound toggle.
- **Walk to play:** launch an action after sustained walking.
- **Screen dimmer** and **night mode** with adjustable intensity.
- **Screen recording** shortcut.
- **Physical activity tracking** service for steps and workouts.
- **Boot receiver** restores your services after a restart.

---

## 🎨 Customization

- **Themes:** dozens of built-in wallpaper themes, plus palettes: Nord, Dracula, Catppuccin Mocha and Monochrome, with accent colors.
- **Wallpaper:** use your system wallpaper or pick one, with adjustable background translucency, blur and an "opaque surfaces" option.
- **Typography:** font size scale, font style, weight and color, and **downloadable fonts**.
- **Icons:** shape, size, icon packs, grayscale.
- **Languages:** English, Arabic, German, Spanish, French, Hindi, Indonesian, Kannada, Brazilian Portuguese and Russian.
- **Orientation** control and tablet layouts.
- **Minimal mode** for a plain black background.
- **Interactive tutorial** for first-time users.

---

## 🤏 Gestures Reference

| Gesture | Result |
|---------|--------|
| Swipe left / right | Move between pages (wraps around) |
| Scroll to the end of favorites | Show all apps |
| Scroll to the top of all apps | Back to favorites |
| Swipe up (stock layout) | Open the app drawer |
| Swipe down (stock layout) | Open the notification shade |
| Swipe right (stock layout) | Open your chosen app |
| Double-tap empty space | Lock the screen |
| Tap time / date | Open Clock / Calendar |
| Long-press search bar | Open your web search |
| Long-press an app | App menu (info, uninstall, share, timer, favorite, hide) |
| Long-press a widget | Drag to reorder |
| Long-press the Focus icon | Focus Mode setup |
| Tap a day in weekly usage | Daily usage breakdown |
| Long-press the finance balance | Transaction history |
| Shake twice | Toggle torch |
| Double-tap the back of the phone | Your chosen back-tap action |
| Swipe from the screen edge | Edge panel or Control Center |

---

## 🎙️ Voice Commands

Use the microphone in the search bar.

| Say | What happens |
|-----|--------------|
| `Call Swaroop` | Opens the dialer with that contact |
| `Message Swaroop` | Opens a text message to that contact |
| `Send hi to Swaroop` | Sends a message to the contact |
| `Search weather tomorrow` | Searches with your chosen engine |
| `Open YouTube` | Opens that app |
| `Uninstall WhatsApp` | Opens the uninstall screen for that app |
| `Bangalore to Mysore` | Opens Google Maps with the route |

Needs the contacts and microphone permissions.

---

## 🔑 Permissions Explained

Launch asks only for what a feature needs, and you can use the launcher without most of them.

| Permission | Used for |
|------------|----------|
| Contacts | Contact search and quick actions |
| Calendar | Calendar and countdown widgets |
| Usage access | App usage, limits, sorting, recap, edge panel recents |
| Notification access | Notification badges and the notifications widget |
| Accessibility service | Double-tap lock, screenshot, notifications, Control Center and edge panel |
| Display over other apps | Control Center, edge panel, dimmer and night mode overlays |
| Activity recognition | Steps and physical activity |
| Microphone | Voice search and the noise meter |
| Camera | Torch and camera shortcut |
| Biometric | Fingerprint unlock for App Lock and the vault |
| Notifications / exact alarms | Todo reminders and Pomodoro |
| Do Not Disturb access | Focus Mode and Flip to DND |
| Modify system settings / secure settings | Screen timeout, auto-rotate and the Private DNS widget |
| Bluetooth | Bluetooth toggle in Control Center |
| Query all packages | Showing every installed app in the launcher |
| Request delete packages | Uninstalling apps from the long-press menu |
| Foreground services | Sensor, shake, back-tap and recording services |
| Boot completed | Restarting enabled services after reboot |
| Internet / network state | Weather, RSS, web apps, fonts, AI model download and file transfer |

The built-in Files app asks for "All files access" so it can browse and manage storage like any file manager. It is requested only when you open Files, can be revoked in system settings, and nothing is uploaded. Camera asks for the camera (and microphone for video). See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

---

## 🚀 Getting Started

1. Install Launch from Google Play, Obtainium or the GitHub releases.
2. Open it and read the short data disclosure.
3. Set **Launch** as your default home app when asked.
4. Choose a layout: List, Grid or Stock.
5. Add favorites (long-press an app) or drag apps onto the stock home grid.
6. Open **Settings** to turn on the pages, widgets and tools you want.

---

## 🛠️ Development

### Prerequisites

- **Android Studio** (recent stable)
- **JDK 21**
- **Android SDK** with minimum API 26 and compile API 35
- **Gradle** (included via the wrapper)

### Setup

```bash
git clone https://github.com/guruswarupa/Launch.git
cd Launch
./gradlew installDebug
```

For more detail see [DEVELOPMENT.md](DEVELOPMENT.md).

### Project structure

```
launch/
├── app/src/main/
│   ├── java/com/guruswarupa/launch/
│   │   ├── ai/           # On-device and web AI assistant, usage prediction
│   │   ├── core/         # Permissions, backups, shared helpers
│   │   ├── handlers/     # Initialization, gestures, settings coordination
│   │   ├── managers/     # Apps, search, focus, folders, pager, web apps
│   │   ├── services/     # Accessibility, sensors, dimmer, recording
│   │   ├── transfer/     # Secure local file transfer
│   │   ├── ui/           # Activities, adapters, views, theming
│   │   ├── utils/        # Helpers (currency, search, QR, themes)
│   │   └── widgets/      # All home widgets
│   ├── res/              # Layouts, drawables, strings (10 languages)
│   └── assets/           # Bundled ad-block domain list
├── gradle/libs.versions.toml
└── .github/workflows/    # PR checks and release builds
```

### Key technologies

- **Kotlin**, Android Views with RecyclerView, and Jetpack Compose (file transfer)
- **Material Components**, **Hilt**, **Glide**, **Moshi**, **Apache POI**, **Jsoup**
- **BouncyCastle** for transfer certificates, **ZXing** for QR codes
- **Google Play Billing** for optional support purchases

### Building for release

```bash
./gradlew assembleRelease
```

The APK is generated at `app/build/outputs/apk/release/`. Tagged builds are produced automatically by the GitHub release workflow.

---

## 🤝 Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to report bugs, suggest features, follow the code style and open pull requests.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes
4. Push the branch
5. Open a pull request

---

## 📄 License

Licensed under the MIT License with additional attribution requirements. See [LICENSE](LICENSE).

**Note:** this software may not be substantially modified, rebranded or claimed as the work of others without explicit written permission. Derivative works must keep proper attribution and the same license terms.
