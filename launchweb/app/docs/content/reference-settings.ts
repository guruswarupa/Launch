import type { DocPage } from '../types';

export const referenceSettingsPages: Record<string, DocPage> = {
  'settings': {
    title: 'Settings & Configuration',
    sections: [
      {
        heading: 'Complete Settings Overview',
        content: `Launch offers comprehensive customization through its settings menu. Access settings by long-pressing an empty area on the home screen or tapping the gear icon in the Control Center.`
      },
      {
        heading: 'Appearance Settings',
        content: `**Theme**
• Light mode
• Dark mode
• AMOLED Black
• Material You (dynamic colors)
• Custom gradient themes

**Grid & Layout**
• Apps per row: 3, 4, 5, or 6
• Icon size: XS, S, M, L, XL
• Page count: Unlimited
• Dock rows: 1 or 2
• Display mode: Grid, List, Compact, Minimalist

**Fonts**
• Font family selection (system fonts + custom)
• Font size: Tiny to Extra Large
• Text style: Bold, Italic, Uppercase
• Letter spacing adjustment
• Shadow effects toggle

**Colors**
• Background color/opacity
• Text color selection
• Accent color picker
• Icon pack support (1000+ packs)
• Corner radius (rounded vs square icons)

**Wallpapers**
• Static image picker
• Live wallpapers support
• Wallpaper blur effect
• Darken overlay option
• Category browsing`
      },
      {
        heading: 'Behavior Settings',
        content: `**Animations**
• Animation speed: 0.5x, 1x, 1.5x, 2x
• Transition effects: Fade, Slide, Zoom, Cube, Stack, Flip
• Scroll animation toggle

**Scrolling**
• Bounce effect on/off
• Infinite scroll toggle
• Scroll speed adjustment
• Overscroll behavior

**Clock & Date**
• 12-hour / 24-hour format
• Show/hide seconds
• Timezone selection
• Date format options
• Week start day (Monday/Sunday)

**Search Bar**
• Default search mode
• Show/hide microphone
• Calculator integration toggle
• Recent searches limit
• Clear search history

**Lock Screen**
• Double-tap to sleep
• Always show lock clock
• Lock clock style
• Shortcut customization`
      },
      {
        heading: 'Gestures & Input',
        content: `**Shake Gestures**
• Shake to torch: Enable/disable
• Sensitivity: Low, Medium, High
• Screen-on requirement toggle
• Shake to activate voice commands

**Drawing Letters**
• Enable/disable gesture drawing
• Configure custom letters (a-z)
• Assign apps to letters
• Test gesture recognition
• Sensitivity adjustment

**Edge Swipes**
• Enable/disable edge swipes
• Left edge action
• Right edge action
• Swipe width threshold

**Flip to DND**
• Enable/disable flip detection
• Sensitivity calibration
• Exclude times (e.g., nighttime)`
      },
      {
        heading: 'Workspaces & Organization',
        content: `**Workspace Management**
• Create new workspace
• Edit existing workspaces
• Delete workspace
• Reorder workspace tabs
• Workspace color themes

**Hidden Apps**
• View hidden apps list
• Unhide individual apps
• Hide additional apps
• Authentication requirements

**Folders**
• Folder naming
• Folder icon selection
• Folder color themes
• Auto-name suggestions
• Folder preview style`
      },
      {
        heading: 'Notifications & Status',
        content: `**Notification Widget**
• Enable/disable widget
• Notification count badge
• Show/hide app names
• Swipe-to-dismiss toggle
• Clear all button visibility

**Status Bar**
• Show/hide status bar
• Clock visibility
• Battery indicator style
• Network speed monitor
• Custom status icons

**DND Integration**
• Automatic DND during focus mode
• Scheduled DND times
• Allow exceptions (favorites, alarms)
• Flip-to-DND toggle`
      },
      {
        heading: 'Permissions & Security',
        content: `**Permission Manager**
• Grant required permissions
• Revoke optional permissions
• Permission explanations
• One-tap permission reset

**App Lock Settings**
• Change PIN/password
• Enable/disable biometrics
• Lock timeout duration
• Locked apps list
• Fingerprint setup

**Privacy Dashboard**
• Permission usage timeline
• Recent permission access
• Suspicious activity alerts
• Privacy report export
• Auto-reset permissions`
      },
      {
        heading: 'Backup & Restore',
        content: `**Backup Data**
• Backup layout configuration
• Save widget arrangements
• Export workspace setups
• Backup hidden apps list
• Cloud backup (Google Drive)

**Restore**
• Import backup file
• Selective restore options
• Merge with current setup
• Factory reset option

**Migration**
• Import from other launchers
• Export to share with others
• Sync across devices`
      },
      {
        heading: 'Advanced Settings',
        content: `**Developer Options**
• Debug mode toggle
• Log viewer
• Performance metrics
• Memory usage monitor
• Animation debugging

**Accessibility Service**
• Screen lock with double-tap
• Enhanced gesture detection
• Voice command enhancements
• Automation features

**Automation Rules**
• Time-based profile switching
• Location-based automation
• App-specific settings
• Conditional triggers

**About Launch**
• Version information
• Open source licenses
• Contributor credits
• Check for updates
• Report bugs
• Feature requests`
      }
    ]
  },

};
