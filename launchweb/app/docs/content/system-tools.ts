import type { DocPage } from '../types';

export const systemToolsPages: Record<string, DocPage> = {
  'control-center': {
    title: 'Control Center Shortcuts',
    sections: [
      {
        heading: 'Quick Settings Panel',
        content: `The Control Center provides instant access to frequently-used toggles and settings without digging through menus.`
      },
      {
        heading: 'Accessing Control Center',
        content: `**Swipe Down**
Swipe down from top edge of home screen (like notification shade on stock Android)

**Swipe Up**
On tablets, swipe up from bottom edge

**Keyboard Shortcut**
Press Ctrl+Space when using keyboard with Android`
      },
      {
        heading: 'Available Toggles',
        content: `**Connectivity**
• Wi-Fi on/off
• Mobile data
• Bluetooth
• Airplane mode
• NFC
• Hotspot

**Sound & Display**
• DND (Do Not Disturb)
• Silent/Vibrate/Ring profiles
• Brightness slider
• Night mode / Blue light filter
• Auto-rotate lock

**Utilities**
• Flashlight/Torch
• QR Scanner
• Screenshot
• Screen recording
• Cast/Screen mirroring
• Location/GPS

**Quick Actions**
• Calculator
• Camera
• Voice recorder
• Notes
• Recent apps
• Power menu`
      },
      {
        heading: 'Customizing Control Center',
        content: `**Reorder Toggles**
1. Settings > Control Center
2. Long press and drag toggles
3. Arrange in preferred order
4. Top row shows primary toggles

**Add/Remove Toggles**
• Tap "+" to add more toggles
• Tap "-" to remove unused ones
• Some system toggles cannot be removed

**Appearance**
• Choose grid size (3x3, 4x4, 5x5)
• Select toggle style (rounded, square)
• Enable/disable labels`
      }
    ]
  },

  'sensors': {
    title: 'Advanced Sensors',
    sections: [
      {
        heading: 'Hardware Sensor Integration',
        content: `Launch leverages your device's built-in sensors to provide useful real-time information through dedicated widgets.`
      },
      {
        heading: 'Compass Widget',
        content: `**Features**
• Real-time directional heading (0-360°)
• Cardinal directions (N, NE, E, SE, S, SW, W, NW)
• Visual compass rose
• Accuracy indicator

**Usage**
• Hold device flat for accurate reading
• Calibrate by moving in figure-8 motion
• Tap to toggle widget on/off
• Shows magnetic north

**Note**: Requires magnetometer sensor (not available on all devices)`
      },
      {
        heading: 'Pressure Sensor',
        content: `**Displays**
• Atmospheric pressure in hPa/mbar
• Altitude estimation in meters
• Pressure trend (rising/falling/stable)

**Applications**
• Weather prediction aid (falling pressure often precedes storms)
• Altitude tracking for hiking
• Indoor positioning assistance

**Accuracy**: ±1 hPa typically`
      },
      {
        heading: 'Temperature Sensor',
        content: `**Monitors**
• Ambient air temperature (°C/°F)
• Device temperature (CPU/battery)
• Historical temperature graph

**Limitations**
• Affected by device heat
• Best accuracy when screen off briefly
• Not all devices have ambient temp sensor`
      },
      {
        heading: 'Noise Decibel Meter',
        content: `**Real-Time Analysis**
• Current noise level in dB
• Noise level classification:
  - Quiet (< 50 dB)
  - Moderate (50-70 dB)
  - Loud (70-90 dB)
  - Very Loud (> 90 dB)
• Visual decibel meter
• Peak hold indicator

**Use Cases**
• Monitor workplace noise safety
• Check concert/event volume levels
• Sleep environment optimization

**Calibration**: Adjust baseline in settings for your environment`
      },
      {
        heading: 'Physical Activity Tracking',
        content: `**Step Counter**
• Daily step count
• Distance traveled (km/miles)
• Calories burned estimate
• Weekly/monthly trends

**Workout Detection**
• Automatic activity recognition
• Walking, running, cycling detection
• Workout session logging

**Integration**
• Syncs with Google Fit (optional)
• Historical data charts
• Goal setting and achievements`
      }
    ]
  },

  'torch': {
    title: 'Shake to Toggle Torch',
    sections: [
      {
        heading: 'Quick Flashlight Access',
        content: `Instantly turn your flashlight on or off by shaking your device twice—no need to fumble with buttons or widgets.`
      },
      {
        heading: 'How It Works',
        content: `**Activation**
1. Shake device twice in quick succession
2. Torch turns on/off immediately
3. Visual feedback shows torch status
4. Works even when screen is off

**Sensitivity Settings**
1. Go to Settings > Gestures > Shake to Torch
2. Adjust sensitivity: Low, Medium, High
3. Test shake pattern
4. Enable/disable as needed`
      },
      {
        heading: 'Smart Features',
        content: `**Screen-On Requirement**
By default, shake gesture only works when screen is on to prevent accidental activation. Option to enable with screen off available.

**Background Operation**
Shake detection runs efficiently in background with minimal battery impact (< 1% per day).

**Timeout Options**
• Auto-off after 1/2/5/10 minutes
• Manual off only (default)
• Double shake to toggle (same gesture)`
      },
      {
        heading: 'Battery Efficiency',
        content: `• Uses hardware sensors only
• No continuous CPU wake locks
• Optimized shake detection algorithm
• Can be disabled to save power`
      }
    ]
  },

  'speed-test': {
    title: 'Network Speed Test',
    sections: [
      {
        heading: 'Built-In Network Testing',
        content: `Measure your internet speed directly from the home screen widget without installing additional apps.`
      },
      {
        heading: 'Running Speed Test',
        content: `**Quick Test**
1. Tap the Network Stats widget
2. Tap "Run Speedtest"
3. Wait for test to complete (~15 seconds)
4. View results instantly

**Detailed Test**
• Download speed (Mbps)
• Upload speed (Mbps)
• Ping/Latency (ms)
• Jitter (stability metric)
• Server location
• Connection type (WiFi/4G/5G)`
      },
      {
        heading: 'Features',
        content: `**Real-Time Monitoring**
• Current network speed in status bar
• WiFi signal strength indicator
• Data usage tracker
• Network type detector (2G/3G/4G/5G/WiFi)

**History & Trends**
• Speed test history with timestamps
• Daily/weekly/monthly averages
• Speed comparison graphs
• Peak performance tracking
• Export test results

**Server Selection**
• Auto-select nearest server
• Manual server choice
• Test against multiple servers
• Save favorite servers`
      },
      {
        heading: 'Data Usage Tracking',
        content: `**Mobile Data**
• Daily usage limit setting
• Warning at threshold
• Auto-disable data at limit
• Per-app usage breakdown

**WiFi Usage**
• Estimate data consumed
• Track connected time
• Signal quality history

**Billing Cycle**
• Set cycle reset date
• Projected usage estimate
• Overage alerts`
      }
    ]
  },

  'voice-commands': {
    title: 'Voice Commands',
    sections: [
      {
        heading: 'Hands-Free Control',
        content: `Use your voice to control Launch, make calls, send messages, and more—no touching required.`
      },
      {
        heading: 'Activating Voice Commands',
        content: `**Methods to Activate**
1. Say "Hey Launch" (always listening when enabled)
2. Tap microphone icon in search bar
3. Long press home button (if configured)
4. Shake device twice (optional gesture)

**Setup**
1. Settings > Voice Commands
2. Enable "Hey Launch" hotword detection
3. Train voice model (recommended)
4. Configure sensitivity`
      },
      {
        heading: 'Available Commands',
        content: `**App Control**
• "Open [app name]"
• "Launch [app name]"
• "Close [app name]"
• "Show me my apps"

**Calling & Messaging**
• "Call [contact name]"
• "Send message to [contact]"
• "Text [contact] saying [message]"
• "Read my messages"

**Search**
• "Search for [query]"
• "Look up [topic]"
• "Find [file type] files"
• "Search YouTube for [video]"

**System Control**
• "Turn on flashlight"
• "Enable WiFi"
• "Set brightness to 50%"
• "Take a screenshot"
• "Show notifications"

**Shortcuts**
• "Go home"
• "Go back"
• "Show recent apps"
• "Lock screen"`
      },
      {
        heading: 'Privacy & Accuracy',
        content: `**Privacy**
• Voice processing happens on-device when possible
• No voice recordings sent to servers
• Disable hotword detection anytime
• Microphone permission required

**Improving Accuracy**
• Speak clearly and naturally
• Reduce background noise
• Train voice model during setup
• Update contact names pronunciation`
      }
    ]
  },

};
