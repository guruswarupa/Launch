import type { DocPage } from '../types';

export const privacyPages: Record<string, DocPage> = {
  'privacy-dashboard': {
    title: 'Privacy Dashboard',
    sections: [
      {
        heading: 'Complete Privacy Control',
        content: `Monitor and manage which apps have access to sensitive permissions on your device.`
      },
      {
        heading: 'Permission Overview',
        content: `**Tracked Permissions**
• Camera access
• Microphone access
• Location (GPS)
• Contacts
• Storage/File access
• Phone/SMS
• Calendar
• Call logs
• Media/Audio/Video

**Visual Indicators**
• Green dot: Permission currently in use
• Yellow dot: Used in last 24 hours
• Gray dot: Not used recently
• Red outline: Suspicious usage pattern`
      },
      {
        heading: 'Features',
        content: `**One-Tap Management**
• Revoke permissions directly from dashboard
• Bulk revoke by permission type
• Set permission schedules (time-based access)
• Get notified when apps access sensitive data

**Access Timeline**
• See when each app last used a permission
• View frequency of access
• Identify unusual patterns (e.g., flashlight app accessing contacts)

**Privacy Report**
• Weekly summary of permission usage
• Export privacy audit log
• Compare with similar apps
• Security recommendations`
      },
      {
        heading: 'Additional Privacy Tools',
        content: `**Permission Auto-Reset**
Automatically revoke permissions from apps you haven't used in months (Android 11+ feature enhanced by Launch).

**Incognito Mode**
• Hide recent apps from recents
• Disable usage tracking temporarily
• Clear search history one-tap

**App Ops Integration**
Advanced users can fine-tune permissions beyond standard Android settings (requires ADB setup).`
      }
    ]
  },

  'privacy-security': {
    title: 'Privacy & Security',
    sections: [
      {
        heading: 'Your Privacy Matters',
        content: `Launch is committed to protecting your privacy and security. As an open-source launcher, all code is transparent and auditable. We believe in minimal data collection and maximum user control.`
      },
      {
        heading: 'Data Collection Policy',
        content: `**What We DON'T Collect**
• No personal data transmitted to servers
• No usage tracking or analytics
• No advertising IDs collected
• No location history stored remotely
• No contact information uploaded
• No browsing history monitored

**What Stays Local**
• All app usage statistics
• Search history
• Gesture patterns
• Customization preferences
• Workspace configurations
• Hidden apps list
• All data stored only on your device

**Open Source Commitment**
• 100% open source codebase
• Available on GitHub for review
• Community-audited security
• No hidden trackers or spyware
• No ads or ad SDKs`
      },
      {
        heading: 'Permission Transparency',
        content: `**Essential Permissions**
Query All Packages: Required to display and search your installed apps (mandatory for any launcher)

Usage Stats Access: Enables app usage tracking and frequency sorting (optional, can be declined)

Home Launcher: Sets Launch as default home screen (revocable anytime)

**Optional Permissions**
Contacts Access: Powers contact search in unified search bar (decline if not needed)

Notification Access: Enables notification widget features (optional)

Accessibility Service: Enables screen locking gestures and enhanced automation (optional)

Device Administrator: Allows turning off screen with gestures (optional)

Storage Access: Required for notes, backups, and file sharing features (optional)

Camera: Used for QR scanner functionality (optional)

Microphone: Enables voice commands (optional)

Phone/SMS: Provides calling/messaging shortcuts (optional)

Activity Recognition: Powers step counter and fitness tracking (optional)

Body Sensors: Accesses heart rate and fitness data (optional)`
      },
      {
        heading: 'Security Features',
        content: `**App Lock**
• PIN protection for sensitive apps
• Biometric authentication (fingerprint/face)
• Configurable lock timeout
• Failed attempt warnings
• Emergency bypass disabled

**Privacy Dashboard**
• Real-time permission monitoring
• Visual indicators for active permissions
• Timeline of permission usage
• Suspicious activity detection
• One-tap permission revocation

**Hidden Apps**
• Conceal rarely-used or private apps
• Optional authentication to view hidden apps
• Separate from main app drawer
• Maintains full functionality

**Secure Folders**
• Create password-protected workspaces
• Isolate sensitive apps
• Hide entire workspace contents
• Quick hide gesture`
      },
      {
        heading: 'Privacy Best Practices',
        content: `**Recommended Settings**
1. Enable app lock for sensitive apps (banking, messaging, email)
2. Review permission dashboard monthly
3. Use hidden apps for rarely-used applications
4. Enable auto-reset for unused app permissions
5. Regularly clear search history if concerned
6. Use incognito mode for private browsing sessions

**Permission Management**
• Start with minimal permissions
• Grant permissions as needed
• Review permissions quarterly
• Revoke permissions from unused apps
• Watch for suspicious permission combinations

**Security Tips**
• Use strong PIN for app lock
• Enable biometric when available
• Don't share device unlock pattern
• Keep launcher updated
• Review automation rules periodically
• Be cautious with accessibility permissions`
      },
      {
        heading: 'Privacy Controls',
        content: `**Incognito Mode**
• Temporarily disable usage tracking
• Hide apps from recent list
• Clear search history one-tap
• Pause notification logging
• Auto-disable after session

**Auto-Reset Permissions**
Automatically revoke permissions from apps not used in 3+ months (Android 11+ feature enhanced by Launch)

**Permission Schedules**
Set time-based permission access:
• Camera: Only during daytime
• Microphone: Disabled at night
• Location: Work hours only

**Privacy Notifications**
Get alerted when:
• App accesses camera/microphone
• Location accessed in background
• Unusual permission activity detected
• New permission requested`
      },
      {
        heading: 'Data Portability',
        content: `**Export Your Data**
• Export layout configuration
• Backup workspace setups
• Save hidden apps list
• Export finance tracker data (CSV)
• Download usage statistics

**Import Data**
• Restore from backup files
• Import from other launchers
• Migrate to new device
• Share configurations

**Right to Deletion**
• Factory reset clears all data
• Uninstall removes local data
• No server-side data to delete
• Complete data sovereignty`
      },
      {
        heading: 'Third-Party Services',
        content: `**Integrated Services**
Weather: Powered by Open-Meteo (privacy-focused, no API key required)

GitHub Widget: OAuth authentication, read-only access, credentials stored locally

Web Apps: Uses your browser's WebView, respects website privacy policies

Voice Commands: On-device processing when possible, no cloud transmission

**External Links**
Play Store links open in official app
GitHub links open in browser or GitHub app
Downloaded APKs from trusted sources only
No affiliate tracking links`
      },
      {
        heading: 'Security Updates',
        content: `**Update Policy**
• Regular security patches
• Prompt vulnerability fixes
• Transparent changelog
• Backward compatibility maintained
• Manual update check available

**Reporting Vulnerabilities**
• GitHub Issues for bug reports
• Security@ email for sensitive issues
• Responsible disclosure policy
• Bounty program for critical findings
• Public security advisories

**Version History**
Check Settings > About for:
• Current version number
• Release date
• Security patch level
• Changelog link
• Update availability`
      }
    ]
  },
};
