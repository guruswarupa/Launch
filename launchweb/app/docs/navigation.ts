export interface NavItem {
  id: string;
  label: string;
  icon: string;
}

export interface NavCategory {
  category: string;
  items: NavItem[];
}

export const docsNavigation: NavCategory[] = [
  {
    category: 'Getting Started',
    items: [
      { id: 'introduction', label: 'Introduction', icon: '📱' },
      { id: 'installation', label: 'Installation & Setup', icon: '⚙️' },
      { id: 'app-tour', label: 'First Run & App Tour', icon: '🧭' },
      { id: 'permissions', label: 'Required Permissions', icon: '🔐' },
    ],
  },
  {
    category: 'Core Features',
    items: [
      { id: 'search-bar', label: 'Unified Search Bar', icon: '🔎' },
      { id: 'focus-mode', label: 'Focus Mode & Pomodoro', icon: '⏱' },
      { id: 'productivity-widgets', label: 'Productivity Widgets', icon: '🧩' },
      { id: 'app-lock', label: 'App Lock & Timers', icon: '🔒' },
      { id: 'hidden-apps', label: 'Hidden Apps & Workspaces', icon: '🗂' },
      { id: 'control-center', label: 'Control Center', icon: '🎛' },
      { id: 'app-management', label: 'Smart App Management', icon: '📊' },
      { id: 'voice-commands', label: 'Voice Commands', icon: '🎤' },
      { id: 'finance-tracker', label: 'Finance Tracker', icon: '💰' },
      { id: 'sensors', label: 'Advanced Sensors', icon: '🧲' },
      { id: 'torch', label: 'Shake to Torch', icon: '🔦' },
      { id: 'apk-sharing', label: 'APK Sharing', icon: '📦' },
      { id: 'gestures', label: 'Gesture Controls', icon: '🤏' },
      { id: 'customization', label: 'Deep Customization', icon: '🎨' },
      { id: 'privacy-dashboard', label: 'Privacy Dashboard', icon: '🛡' },
      { id: 'web-apps', label: 'Web Apps Support', icon: '🌐' },
      { id: 'speed-test', label: 'Network Speed Test', icon: '⚡' },
      { id: 'github-widget', label: 'GitHub Contributions', icon: '📈' },
      { id: 'files-and-storage', label: 'Files & Storage Analyzer', icon: '📂' },
      { id: 'camera-and-media', label: 'Camera, Gallery & Player', icon: '📸' },
      { id: 'document-viewer', label: 'Document Viewer', icon: '📄' },
      { id: 'lock-screen', label: 'Custom Lock Screen', icon: '🔓' },
      { id: 'ai-assistant', label: 'AI Assistant & News', icon: '🤖' },
      { id: 'file-transfer', label: 'File Transfer', icon: '📡' },
      { id: 'vault-and-backup', label: 'Vault & Backup', icon: '🗝' },
    ],
  },
  {
    category: 'Reference',
    items: [
      { id: 'gestures-guide', label: 'Gestures Guide', icon: '👆' },
      { id: 'settings', label: 'Settings & Configuration', icon: '🛠' },
      { id: 'privacy-security', label: 'Privacy & Security', icon: '🛡' },
      { id: 'faq', label: 'FAQ & Troubleshooting', icon: '❓' },
    ],
  },
];
