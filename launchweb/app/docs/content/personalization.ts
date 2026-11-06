import type { DocPage } from '../types';

export const personalizationPages: Record<string, DocPage> = {
  'gestures': {
    title: 'Gesture Controls',
    sections: [
      {
        heading: 'Intuitive Gesture Interface',
        content: `Launch provides extensive gesture support for quick actions and efficient navigation throughout the launcher.`
      },
      {
        heading: 'App Icon Gestures',
        content: `**Tap**
• Single tap: Open app
• Double tap: Quick action (configurable)

**Long Press**
Opens context menu with options:
• App info
• Uninstall
• Share APK
• Lock app
• Hide app
• Add to favorites
• Set time limit
• Remove from workspace
• Uninstall (system apps grayed out)`
      },
      {
        heading: 'Widget Gestures',
        content: `**Time/Date Widget**
• Tap time: Open Clock app
• Tap date: Open Calendar app

**Search Bar**
• Long press: Open Google in browser
• Type: Instant search/calculator
• Tap mic: Voice search

**Focus Mode Icon**
• Tap: Toggle focus mode
• Long press: Configure focus settings

**Notification Widget**
• Swipe left: Dismiss notification
• Tap: Open related app
• Tap badge: Expand notifications

**Usage Graph**
• Tap day: View detailed breakdown
• Swipe: Navigate weeks`
      },
      {
        heading: 'Navigation Gestures',
        content: `**Home Screen Swiping**
• Swipe left/right: Change pages
• Swipe up: Open app drawer (if enabled)
• Swipe down: Control center
• Pinch out: Overview mode
• Pinch in: Edit mode

**Dock Gestures**
• Swipe up on dock: Show all dock apps
• Long press dock app: Remove/rename

**Edge Swipes**
• Swipe from left edge: Previous page
• Swipe from right edge: Next page
• Swipe from top: Notifications`
      },
      {
        heading: 'Special Gestures',
        content: `**Shake Gestures**
• Shake twice: Toggle torch
• Shake phone: Activate voice commands (optional)

**Flip to DND**
• Place phone face-down: Automatically enable Do Not Disturb
• Configurable in Settings > Gestures

**Double Tap**
• Double tap search bar: Turn off screen
• Double tap empty space: Lock screen (requires accessibility permission)

**Drawing Gestures**
• Draw 'c': Open camera
• Draw 'w': Open WhatsApp
• Draw 'm': Open messages
(Configure in Settings > Gestures > Draw letters)`
      }
    ]
  },

  'customization': {
    title: 'Deep Customization',
    sections: [
      {
        heading: 'Make Launch Yours',
        content: `Every aspect of Launch can be customized to match your preferences and workflow.`
      },
      {
        heading: 'Layout Options',
        content: `**Grid Configuration**
• Apps per row: 3, 4, 5, or 6
• Icon size: Small, Medium, Large, Extra Large
• Page count: Unlimited home screen pages
• Dock rows: 1 or 2 rows

**Display Mode**
• Grid view (default)
• List view (single column)
• Compact mode (smaller spacing)
• Minimalist mode (icons only, no labels)`
      },
      {
        heading: 'Themes & Appearance',
        content: `**Built-in Themes**
Choose from preset themes:
• Light
• Dark
• AMOLED Black
• Material You (dynamic colors)
• Custom gradient themes

**Custom Theme Builder**
• Background color/opacity
• Text color and font
• Icon pack support (1000+ packs compatible)
• Accent color selection
• Shadow intensity
• Corner radius (rounded vs square icons)

**Wallpapers**
• Static images
• Live wallpapers supported
• Wallpaper picker with categories
• Blur effect toggle
• Darken overlay option`
      },
      {
        heading: 'Font Customization',
        content: `**Font Styles**
• System default
• Roboto (multiple weights)
• Downloadable fonts from Google Fonts
• Custom .ttf/.otf files

**Font Sizes**
• Tiny, Small, Medium, Large, Extra Large
• Preview before applying
• Separate size for labels vs widgets

**Text Effects**
• Bold, Italic
• Uppercase toggle
• Letter spacing
• Shadow on/off`
      },
      {
        heading: 'Behavior Settings',
        content: `**Animation Speed**
• Animation scale: 0.5x, 1x, 1.5x, 2x
• Transition effects: Fade, Slide, Zoom, Cube, Stack

**Scrolling**
• Bounce effect on/off
• Infinite scroll toggle
• Scroll speed adjustment

**Clock Format**
• 12-hour (AM/PM)
• 24-hour format
• Show/hide seconds
• Timezone selection`
      }
    ]
  },

  'web-apps': {
    title: 'Web Apps Support',
    sections: [
      {
        heading: 'Progressive Web App Integration',
        content: `Add any website as a standalone app with custom icons, full-screen mode, and ad-blocking capabilities.`
      },
      {
        heading: 'Adding Web Apps',
        content: `**Method 1: From Browser**
1. Visit website in Chrome/Firefox
2. Tap menu (⋮) > "Add to Home screen"
3. Name the web app
4. Tap "Add"
5. Icon appears in app drawer

**Method 2: From Launch**
1. Long press empty space
2. Select "Add Web App"
3. Enter name and URL
4. Choose custom icon (optional)
5. Configure settings
6. Tap "Add"`
      },
      {
        heading: 'Features',
        content: `**WebView Customization**
• Built-in WebView with full browsing
• Custom address bar
• Full-screen mode (hides URL bar)
• JavaScript enabled/disabled
• Cookie management
• Cache controls

**Ad Blocking**
• Built-in ad blocker for web apps
• Blocks banners, popups, video ads
• Whitelist favorite sites
• Custom filter lists

**Standalone Experience**
• Opens in separate window
• Appears in recent apps
• Independent from browser
• Custom splash screen
• Orientation lock (portrait/landscape)`
      },
      {
        heading: 'Managing Web Apps',
        content: `**Edit Settings**
Long press web app icon > Settings:
• Change name
• Update URL
• Select new icon
• Clear cache/data
• Enable/disable ad block
• Set homepage

**Organize**
• Add to favorites
• Include in workspaces
• Hide from main view
• Create folders with other apps

**Remove**
Long press > "Remove Web App" deletes from launcher (doesn't affect browser bookmarks).`
      }
    ]
  },

  'apk-sharing': {
    title: 'APK Sharing',
    sections: [
      {
        heading: 'Share Installed Apps',
        content: `Easily share installed applications with friends via Bluetooth, email, messaging apps, or any file-sharing method.`
      },
      {
        heading: 'Sharing Methods',
        content: `**From App Context Menu**
1. Long press app icon in drawer
2. Select "Share APK"
3. Choose sharing method:
   - Bluetooth
   - Gmail/Email
   - WhatsApp/Telegram
   - Google Drive
   - Any file-sharing app

**From Dock**
1. Long press dock app icon
2. Tap "Share" option
3. Select recipient app

**Batch Sharing**
1. Settings > Apps > Share Multiple APKs
2. Select multiple apps
3. Share as ZIP archive`
      },
      {
        heading: 'Features',
        content: `**APK Extraction**
• Extracts original APK from system
• Preserves app signature
• Includes split APKs for modern apps
• Shows APK size before sharing

**File Management**
• Saved to /Downloads/Launch APKs/
• Named as "AppName_version.apk"
• Option to delete after sharing
• Browse shared APKs history

**Compatibility Check**
• Warns if recipient device may not support APK
• Shows minimum Android version required
• Indicates architecture (ARM/x86)`
      },
      {
        heading: 'Legal Notice',
        content: `• Only share apps you have rights to distribute
• Respect app licenses and copyrights
• Don't share paid apps without permission
• Personal use recommended`
      }
    ]
  },

};
