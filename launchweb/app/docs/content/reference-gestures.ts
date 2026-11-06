import type { DocPage } from '../types';

export const referenceGesturesPages: Record<string, DocPage> = {
  'gestures-guide': {
    title: 'Gestures Guide',
    sections: [
      {
        heading: 'Complete Gesture Reference',
        content: `Launch provides an extensive gesture system for efficient navigation and quick actions throughout the launcher.`
      },
      {
        heading: 'Home Screen Gestures',
        content: `**Swiping**
• Swipe left/right: Navigate between home screen pages
• Swipe up from bottom: Open app drawer
• Swipe down from top: Open Control Center
• Pinch out (two fingers spreading): Enter overview/edit mode
• Pinch in (two fingers together): Exit edit mode

**Edge Swipes**
• Swipe from left edge: Go to previous page
• Swipe from right edge: Go to next page
• Swipe from top edge: Quick settings panel`
      },
      {
        heading: 'App Icon Gestures',
        content: `**Tap Gestures**
• Single tap: Open/launch app
• Double tap: Quick action (configurable per app)

**Long Press Menu**
Opens context menu with options:
• App info: View Android app details
• Uninstall: Remove app from device
• Share APK: Send app installer to others
• Lock app: Require PIN/biometric to open
• Hide app: Move to hidden apps section
• Add to favorites: Pin to top of drawer
• Set time limit: Configure daily usage cap
• Remove from workspace: If in a workspace
• Rename: Change app label (system apps excluded)`
      },
      {
        heading: 'Widget Gestures',
        content: `**Time/Date Widget**
• Tap time display: Open Clock app
• Tap date display: Open Calendar app

**Search Bar**
• Long press: Open Google in browser
• Type text: Instant search or calculator
• Tap microphone: Voice search activation

**Focus Mode Icon**
• Tap: Toggle focus mode on/off
• Long press: Configure focus settings

**Notification Widget**
• Swipe left on notification: Dismiss
• Tap notification: Open related app
• Tap badge count: Expand full notifications

**Usage Statistics Graph**
• Tap specific day: View detailed breakdown
• Swipe horizontally: Navigate between weeks
• Tap category in pie chart: Filter by category`
      },
      {
        heading: 'Dock Gestures',
        content: `**Swipe Up on Dock**
Reveals all dock apps in expanded view

**Long Press Dock App**
Opens menu to:
• Remove from dock
• Rename label
• Change icon
• Rearrange position

**Drag & Drop**
• Drag apps into dock: Add to dock
• Drag out of dock: Remove from dock
• Rearrange dock icons: Reorder positions`
      },
      {
        heading: 'Special Gestures',
        content: `**Shake Gestures**
• Shake twice: Toggle flashlight/torch
• Shake phone: Activate voice commands (optional)

**Flip to DND**
Place phone face-down on surface to automatically enable Do Not Disturb mode

**Double Tap Actions**
• Double tap search bar: Turn off screen
• Double tap empty space: Lock screen (requires accessibility permission)
• Double tap app icon: Quick action shortcut

**Drawing Gestures**
Draw letter shapes to launch apps quickly:
• Draw 'c': Open Camera
• Draw 'w': Open WhatsApp
• Draw 'm': Open Messages
• Draw 'd': Open Dialer
(Configure custom letters in Settings > Gestures > Draw letters)

**Three-Finger Swipe**
• Swipe down: Take screenshot
• Swipe up: Open recent apps`
      },
      {
        heading: 'Customization',
        content: `**Gesture Settings**
Navigate to Settings > Gestures to:
• Enable/disable specific gestures
• Adjust shake sensitivity
• Configure double-tap actions
• Set custom drawing letters
• Modify swipe thresholds
• Calibrate gesture recognition

**Accessibility Options**
• Increase gesture size for easier detection
• Reduce motion for sensitive users
• Haptic feedback on gesture success
• Sound effects toggle`
      }
    ]
  },

};
