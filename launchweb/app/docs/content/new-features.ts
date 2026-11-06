import type { DocPage } from '../types';

export const newFeaturePages: Record<string, DocPage> = {
  'app-tour': {
    title: 'First Run & App Tour',
    sections: [
      {
        heading: 'What happens on first launch',
        content: `Launch walks you through setup in a few short steps, and you can skip or replay any of it later.

1. **Welcome**: choose to import a backup from another phone, or start fresh
2. **Pick a layout**: List, Grid, or Stock home screen
3. **App tour**: 15 swipeable pages that explain every feature
4. **Permissions**: one page per group, each with a short reason and an Allow button
5. **Home app**: make Launch your default home screen on the last page`
      },
      {
        heading: 'Permissions, explained',
        content: `The tour shows each permission with a one-line reason, so nothing is asked without context.

**Essential**
• **Contacts**: search people and call or message them
• **Usage access**: screen-time charts, app limits and the weekly recap
• **Notification access**: badges on app icons and lock screen alerts
• **Notifications**: task reminders and timers

**Optional**
• **Microphone**, **Camera**, **Calendar**, **Physical activity**
• **Display over other apps**: dimmer, night mode and the custom lock screen
• **Do Not Disturb access**: Focus Mode and flip-to-silence
• **Accessibility service**: double-tap to lock and Control Center toggles
• **All files access**: the built-in Files app

> Every permission can be changed later in Android settings, and each feature only uses its own permission.`
      },
      {
        heading: 'Replay the tour',
        content: `Open **Settings** and tap **Take the app tour** at the top. Existing users also see a one-time prompt after updating.`
      }
    ]
  },

  'files-and-storage': {
    title: 'Files & Storage Analyzer',
    sections: [
      {
        heading: 'Launch Files',
        content: `Launch includes its own file manager that appears in the app list like any other app.

• Browse all internal storage, including Downloads and Documents
• Category shortcuts: **Images, Videos, Audio, Docs, Installers, Archives, Recent**
• Copy, move, rename, delete, share and create folders or text files
• **ZIP** compress and extract, with progress and cancel
• Search, sort (name, date, size, type), grid or list view, and multi-select
• Files open in the built-in photo, video and document viewers

> Files uses Android's **All files access** permission, which you grant from the permissions page of the tour or the first time you open Files. Nothing is uploaded.`
      },
      {
        heading: 'Recycle bin',
        content: `Deleted files go to a recycle bin instead of disappearing.

• Items are kept for **30 days**, then removed automatically
• Restore single items or everything, or empty the bin yourself
• The bin shows how many days each item has left`
      },
      {
        heading: 'Storage analyzer',
        content: `Tap **Analyze** on the storage card to see what uses your space.

• **Map**: a treemap where block size matches file size and color shows type
• **Folders**: sizes with a percentage bar, drill down folder by folder
• **Largest**: your 120 biggest files
• **Apps**: every installed app with App, Data and Cache sizes (needs usage access)

Long-press a block or row to move it to the recycle bin.`
      }
    ]
  },

  'camera-and-media': {
    title: 'Camera, Gallery & Video Player',
    sections: [
      {
        heading: 'Camera',
        content: `• Photo and video, front and back cameras
• Flash (off, auto, on), 3s and 10s timers, and a rule-of-thirds grid
• A thumbnail of your last capture
• Saves to **DCIM/Camera**
• Opens over the lock screen, with the gallery thumbnail disabled while locked`
      },
      {
        heading: 'Gallery & photo viewer',
        content: `• Pinch to zoom, double-tap to jump in and out, swipe down to close
• **Swipe left and right** to move through the photos in the same folder
• Details, rotate, share and open with
• Appears in Android's **Open with** menu for images`
      },
      {
        heading: 'Video & audio player',
        content: `• Full playback controls, speed, repeat, and screen lock
• Gesture controls for seeking, brightness and volume
• Previous and next buttons to move through videos in the folder
• Music files show album art and title`
      }
    ]
  },

  'document-viewer': {
    title: 'Document Viewer',
    sections: [
      {
        heading: 'Supported formats',
        content: `Documents open fully offline, with no cloud conversion.

• **Word**: .docx and related formats
• **Excel**: .xlsx with sheets as tabs
• **PowerPoint**: .pptx rendered as slides
• **OpenDocument and RTF**
• **PDF**, text, code (50+ languages), CSV, Markdown, JSON, XML and HTML
• Images, video and audio, plus archives and a hex view for anything else`
      },
      {
        heading: 'Reading tools',
        content: `• **Find in document**, including inside PDFs
• Smooth PDF pinch-zoom with sharp re-rendering, and a Go to page box
• Larger or smaller text, word wrap, and light, dark or sepia themes
• Launch can open these files when you pick it from other apps`
      }
    ]
  },

  'lock-screen': {
    title: 'Custom Lock Screen',
    sections: [
      {
        heading: 'What it is',
        content: `An optional Launch lock screen that appears over the system one when the screen turns on. Your PIN, pattern or fingerprint still protects the phone: swiping up asks Android to unlock as normal.`
      },
      {
        heading: 'What it shows',
        content: `• Clock, date and battery level
• Notifications as cards: **swipe a card left or right to dismiss it**
• Music controls with song name, artist and synced lyrics
• Flashlight and camera buttons
• Your wallpaper behind everything`
      },
      {
        heading: 'Setup and privacy',
        content: `1. Open **Settings** and turn on **Custom lock screen**
2. Allow **Display over other apps** when asked
3. Optionally allow **Show notification content**

> With notification content off, the lock screen shows only app names and counts.

Fingerprint and face unlock close the Launch lock screen automatically and take you home.`
      }
    ]
  },

  'ai-assistant': {
    title: 'AI Assistant & News',
    sections: [
      {
        heading: 'On-device AI',
        content: `• Download a small open model (Qwen2.5) over Wi-Fi and chat **fully offline**
• Downloads are verified, and Launch checks your device has enough memory and storage
• Nothing you type leaves the phone
• Ask questions straight from the search bar`
      },
      {
        heading: 'Web AI',
        content: `Prefer a bigger model? Open **ChatGPT, Claude, Gemini, Copilot, Perplexity or DeepSeek** full screen. These need internet and your own account on each site.`
      },
      {
        heading: 'News feeds',
        content: `The optional RSS page shows your sources, topics and categories. Add and manage sources in **Settings, RSS Sources**.`
      }
    ]
  },

  'file-transfer': {
    title: 'File Transfer',
    sections: [
      {
        heading: 'Send files between phones',
        content: `Move files and folders between devices on the same Wi-Fi network, with no account and no cloud. Find it in **Settings, File Transfer**.

• **Send**: pick files or whole folders
• **Receive**: turn on receiving to show a QR code and a typed code
• **Approve**: you accept each incoming transfer`
      },
      {
        heading: 'How it stays safe',
        content: `• Every transfer uses mutual TLS with pinned device fingerprints
• Pairing is verified by QR secret or a short code shown on both screens
• Files are checked and named safely, and nothing is overwritten
• Received files go to **Downloads/Launch**, with a history of what you sent and received`
      }
    ]
  },

  'vault-and-backup': {
    title: 'Vault & Backup',
    sections: [
      {
        heading: 'Encrypted vault',
        content: `• AES-GCM encryption with a key derived from your password
• Unlock with your password or **fingerprint**
• Thumbnails and a built-in viewer
• Change the password (re-encrypts everything), set an auto-lock timer, and export or import the vault
• The vault locks when the screen turns off`
      },
      {
        heading: 'Backup and restore',
        content: `• Export settings and local data (notes, finance, web apps, weather, activity) to one file
• Optionally protect it with a **passphrase** (PBKDF2 and AES-GCM). A forgotten passphrase cannot be recovered
• Optionally include your encrypted vault files
• Import on a new phone, even from the first-launch screen`
      },
      {
        heading: 'Share your theme',
        content: `Export your look (theme, layout, typography, stock folders and ordering) as a small file and import it on another phone.`
      }
    ]
  },
};
