import type { DocPage } from '../types';

export const gettingStartedPages: Record<string, DocPage> = {
  introduction: {
    title: 'Introduction to Launch',
    sections: [
      {
        heading: 'What is Launch?',
        content: `Launch is a clean, efficient, and minimalist Android launcher built for focus and productivity. It replaces your default home screen with a powerful interface that keeps your most important apps and features at your fingertips.`
      },
      {
        heading: 'Key Benefits',
        content: `• **Productivity First**: Focus mode, Pomodoro timer, and app usage tracking
• **Privacy Focused**: 100% open source, no ads, no tracking
• **Highly Customizable**: Themes, layouts, workspaces, and more
• **Feature Rich**: Unified search, voice commands, advanced sensors
• **Lightweight**: Fast performance with minimal battery drain`
      },
      {
        heading: 'System Requirements',
        content: `• Android 8.0 (API level 26) or higher
• 100 MB free storage space
• 2 GB RAM recommended
• Internet connection for web features`
      }
    ]
  },
  
  installation: {
    title: 'Installation & Setup',
    sections: [
      {
        heading: 'Download & Install',
        content: `**From Google Play Store**
1. Open Play Store on your device
2. Search for "Launch - Productive Launcher"
3. Tap "Install" and wait for download
4. Tap "Open" to launch

**Direct APK Download**
1. Download APK from GitHub releases
2. Enable "Install unknown apps" for your browser
3. Open downloaded APK file
4. Follow installation prompts`
      },
      {
        heading: 'Initial Setup',
        content: `**Step 1: Set as Default Launcher**
When you first open Launch, you'll be prompted to set it as your default home app:
1. Press the home button
2. Select "Launch" from the list of launchers
3. Tap "Always" to make it default

**Step 2: Grant Permissions**
Launch will guide you through required permissions:
- Usage stats permission (for app usage tracking)
- Notification access (for notification widget)
- Accessibility service (for screen lock features)

**Step 3: Customize Your Layout**
- Choose grid size (apps per row)
- Select preferred theme
- Configure initial widgets`
      },
      {
        heading: 'Migration Tips',
        content: `• Your app layout and folders will be preserved
• Widgets need to be re-added manually
• Take time to explore gesture controls
• Check out workspaces for organizing apps`
      }
    ]
  },
  
  permissions: {
    title: 'Required Permissions',
    sections: [
      {
        heading: 'Why Permissions Matter',
        content: `Launch requests several permissions to provide full launcher functionality. All permissions are optional where possible, and data is stored locally on your device.`
      },
      {
        heading: 'Essential Permissions',
        content: `**Query All Packages**
Allows Launch to display and search your installed apps. This is required for any third-party launcher.

**Usage Stats Access**
Enables app usage tracking, screen time monitoring, and app frequency sorting. Used for smart app management features.

**Home Launcher**
Sets Launch as your default home screen. You can change this in Android settings anytime.`
      },
      {
        heading: 'Optional Permissions',
        content: `**Contacts Access**
Enables contact search in unified search bar and quick call actions from search results.

**Notification Access**
Enables integration with active media sessions for the media controller widget.

**Accessibility Service**
Enables screen locking with double-tap gesture and fingerprint unlock integration.

**Device Administrator**
Allows turning off screen with double-tap on search bar.

**Storage Access**
Required for notes, wallpaper backups, and file sharing features.

**Camera**
Used for QR scanner in control center.

**Microphone**
Enables voice commands for hands-free operation.

**Phone/SMS**
Provides calling and messaging shortcuts from contacts search.

**Activity Recognition**
Powers step counter and physical activity tracking widgets.

**Body Sensors**
Accesses heart rate and other fitness sensor data when available.`
      },
      {
        heading: 'Managing Permissions',
        content: `You can manage permissions anytime:
1. Open Launch Settings
2. Navigate to "Permissions"
3. Toggle individual permissions
4. Review what each permission enables

Note: Core launcher functionality works independently even if you decline optional permissions.`
      }
    ]
  },

};
