import type { DocPage } from '../types';

export const productivityPages: Record<string, DocPage> = {
  'search-bar': {
    title: 'Unified Search Bar',
    sections: [
      {
        heading: 'Overview',
        content: `The unified search bar at the top of your home screen is the command center of Launch. It provides instant access to apps, contacts, files, web searches, and more—all from a single input field.`
      },
      {
        heading: 'Search Modes',
        content: `**All** (Default)
Searches across all sources simultaneously: apps, contacts, files, web, Maps, Play Store, and YouTube.

**Apps Only**
Quickly find and launch installed applications. Results show app icons and names.

**Contacts Only**
Search your contact list. Tap results to call or message.

**Files Only**
Find documents, images, videos, and other files stored on your device.

**Maps**
Search locations directly in Google Maps. Shows nearby places and addresses.

**Web**
Performs Google search for web results. Opens in your default browser.

**Play Store**
Search for apps, games, and content on Google Play Store.

**YouTube**
Search YouTube videos and channels directly.`
      },
      {
        heading: 'Features',
        content: `**Instant Auto-complete**
As you type, suggestions appear showing matching results from all enabled search sources.

**Quick Calculator**
Type math expressions directly (e.g., "25*4+10") and get instant results without opening calculator app.

**Recent Searches**
Shows your recent searches for quick re-access. Tap history icon to view.

**Voice Input**
Tap microphone icon to use voice search instead of typing.

**Long Press Action**
Long press the search bar to quickly open Google in your browser.`
      },
      {
        heading: 'Tips',
        content: `• Start typing immediately when home screen is focused
• Use specific search modes to narrow results
• Calculator supports basic and scientific operations
• Search results update in real-time as you type
• Swipe search results left/right to navigate`
      }
    ]
  },

  'focus-mode': {
    title: 'Focus Mode & Pomodoro',
    sections: [
      {
        heading: 'Focus Mode',
        content: `Focus Mode helps you stay productive by limiting distractions. When enabled, it can hide distracting apps, enable Do Not Disturb, and keep you on task.`
      },
      {
        heading: 'Setting Up Focus Mode',
        content: `**Configure Focus Sessions**
1. Long press the Focus icon (⏱) in your widget area
2. Select apps to block during focus sessions
3. Choose duration: 15 min, 30 min, 45 min, 1h, 2h, 3h, or 4h
4. Enable "Do Not Disturb" if desired
5. Optionally enable "Lock Drawer" to prevent accessing hidden apps

**During Focus Mode**
- Blocked apps remain accessible but won't appear in main view
- DND silences notifications (if enabled)
- A subtle indicator shows focus mode is active
- Tap focus icon to end session early`
      },
      {
        heading: 'Pomodoro Timer',
        content: `The built-in Pomodoro technique automates work/break cycles:

**How It Works**
1. Start a focus session with Pomodoro enabled
2. Work for your chosen duration (typically 25 min)
3. Timer automatically starts a short break (5 min)
4. After 4 cycles, take a longer break (15-30 min)

**Customization**
- Adjust work session length (15m-4h)
- Set break duration (3m-30m)
- Enable/disable sound notifications
- Choose whether to auto-start breaks

**Benefits**
• Maintains productivity rhythm
• Prevents burnout with regular breaks
• Tracks completed pomodoros
• Builds healthy work habits`
      }
    ]
  },

  'productivity-widgets': {
    title: 'Productivity Widgets',
    sections: [
      {
        heading: 'All-in-One Productivity Hub',
        content: `Launch includes a comprehensive suite of productivity widgets that can be mixed and matched on your home screen. Each widget is fully customizable and functional.`
      },
      {
        heading: 'Calculator Widget',
        content: `**Modes**
• **Basic**: Standard arithmetic operations (+, -, ×, ÷)
• **Scientific**: Trigonometry, logarithms, exponents, roots
• **Converter**: Length, weight, temperature, currency conversion

**Features**
• Calculation history tape
• Copy/paste support
• Landscape mode for scientific calculator
• Persistent across sessions`
      },
      {
        heading: 'Todo List Widget',
        content: `**Task Management**
• Add tasks with due times
• Set priority levels (High, Medium, Low)
• Create recurring tasks (daily, weekly, monthly)
• Mark tasks complete with visual feedback
• View completion statistics

**Organization**
• Sort by priority, due date, or creation date
• Filter active/completed tasks
• Search tasks by keyword
• Color-coded priority indicators`
      },
      {
        heading: 'Weather Widget',
        content: `**Powered by Open-Meteo**
• Location-based forecasts
• Current temperature and conditions
• Hourly and daily forecasts
• Weather icons (sunny, cloudy, rain, etc.)
• Feels-like temperature
• Humidity and wind speed

**Customization**
• Choose Celsius or Fahrenheit
• Select update frequency
• Show/hide additional details`
      },
      {
        heading: 'Countdown Timer Widget',
        content: `**Features**
• Multiple timers simultaneously
• Set duration from 1 minute to 24 hours
• Calendar event integration (auto-suggests meeting times)
• Persistent timers survive app restarts
• Visual progress ring
• Audio alerts when complete

**Use Cases**
• Cooking timers
• Meeting countdowns
• Study sessions
• Workout intervals`
      }
    ]
  },

  // Additional feature documentation continues similarly...
  
  'app-lock': {
    title: 'App Lock & Timers',
    sections: [
      {
        heading: 'App Lock',
        content: `Protect sensitive apps with PIN or biometric authentication. Locked apps require verification before opening.`
      },
      {
        heading: 'Setting Up App Lock',
        content: `**Initial Setup**
1. Long press any app icon
2. Select "Lock App" from context menu
3. Set a 4-digit PIN (or use biometrics if available)
4. Confirm PIN

**Unlocking Apps**
• Tap locked app icon
• Enter PIN or use fingerprint/face unlock
• App launches after successful authentication

**Managing Locked Apps**
• Settings > Privacy > App Lock
• See list of all locked apps
• Unlock individual apps or change PIN
• Enable/disable biometric option`
      },
      {
        heading: 'App Timers',
        content: `**Daily Usage Limits**
Set time limits for apps to prevent overuse:

1. Long press app icon
2. Select "Set Time Limit"
3. Choose daily limit (15min, 30min, 1h, 2h, 4h, unlimited)
4. App icon dims when limit reached
5. Optional: Block app completely when limit exceeded

**Features**
• Daily reset at midnight
• Usage tracking graph
• Warning notifications before limit
• Override option with confirmation
• Separate weekday/weekend limits`
      }
    ]
  },

  'hidden-apps': {
    title: 'Hidden Apps & Workspaces',
    sections: [
      {
        heading: 'Hidden Apps',
        content: `Keep rarely-used or private apps out of sight without uninstalling them. Hidden apps remain functional and can be accessed when needed.`
      },
      {
        heading: 'Hiding Apps',
        content: `**Method 1: Long Press**
1. Long press app icon in app drawer
2. Select "Hide App"
3. App moves to hidden section

**Method 2: Settings**
1. Go to Settings > Appearance > Hidden Apps
2. Toggle apps to hide/unhide
3. Changes apply immediately

**Accessing Hidden Apps**
• Scroll to bottom of app drawer
• Tap "Show hidden apps"
• Authenticate if app lock enabled
• Tap hidden app to unhide or launch`
      },
      {
        heading: 'Workspaces',
        content: `**What Are Workspaces?**
Workspaces are filtered views showing only selected apps. Perfect for separating work/personal apps or creating themed collections.

**Creating a Workspace**
1. Long press home screen
2. Tap "Create Workspace"
3. Name your workspace (e.g., "Work", "Games", "Social")
4. Select apps to include
5. Choose icon color/theme

**Using Workspaces**
• Swipe left/right on workspace tabs
• Each workspace shows only its assigned apps
• Apps can belong to multiple workspaces
• Switch workspaces instantly

**Managing Workspaces**
• Edit: Long press workspace name
• Delete: Remove workspace (apps return to main view)
• Reorder: Drag workspace tabs
• Rename: Tap workspace name twice`
      }
    ]
  },

  'app-management': {
    title: 'Smart App Management',
    sections: [
      {
        heading: 'Intelligent App Organization',
        content: `Launch automatically organizes your apps based on usage patterns, making it easier to find what you need.`
      },
      {
        heading: 'Usage-Based Sorting',
        content: `**Most Used Apps**
Your frequently opened apps appear at the top of the app drawer for quick access.

**Recently Added**
Newly installed apps show with a "New" badge for easy identification.

**Rarely Used Group**
Apps you haven't opened in 30+ days are grouped alphabetically in a separate section to reduce clutter.

**Adaptive Learning**
The launcher learns from your habits and adjusts app positions throughout the day based on typical usage patterns.`
      },
      {
        heading: 'Weekly Usage Statistics',
        content: `**Usage Graph**
Visual representation of your app usage over the past week:
• Total screen time
• Most used apps by time spent
• Number of unlocks per day
• Peak usage hours

**Interactive Pie Charts**
Tap the graph to see detailed breakdown:
• By category (Social, Productivity, Games, etc.)
• By individual app
• Day vs night usage
• Weekday vs weekend comparison`
      },
      {
        heading: 'Quick App Actions',
        content: `**Long Press Menu**
Long press any app icon reveals:
• **Uninstall**: Remove app (with confirmation)
• **Share APK**: Send app installer to others
• **App Info**: Open Android app info screen
• **Lock**: Require PIN to open
• **Hide**: Move to hidden apps
• **Add to Favorites**: Pin to top
• **Set Time Limit**: Configure daily usage cap
• **Remove from Workspace**: If applicable

**Drag & Drop**
• Rearrange apps in grid
• Create folders by dragging app onto another
• Move apps between pages`
      }
    ]
  },

  'finance-tracker': {
    title: 'Finance Tracker',
    sections: [
      {
        heading: 'Personal Finance Management',
        content: `Track income, expenses, and savings right from your home screen with the built-in finance widget.`
      },
      {
        heading: 'Getting Started',
        content: `**Adding Transactions**
1. Tap the Finance widget
2. Choose "Income" or "Expense"
3. Enter amount
4. Select category (Food, Transport, Salary, etc.)
5. Add note (optional)
6. Tap Save

**Categories**
Pre-configured categories include:
• Income: Salary, Freelance, Investments, Gifts
• Expenses: Food, Transport, Shopping, Bills, Entertainment, Health
• Custom: Create your own categories`
      },
      {
        heading: 'Features',
        content: `**Transaction History**
• View recent transactions list
• Filter by date range
• Search by keyword or amount
• Edit or delete entries

**Monthly Overview**
• Total income vs expenses
• Savings calculation
• Category-wise spending breakdown
• Month-over-month comparison

**Budgets**
• Set monthly budget per category
• Get alerts when approaching limit
• Track budget progress visually

**Data Export**
• Export to CSV for spreadsheet analysis
• Email reports to yourself
• Backup and restore data`
      },
      {
        heading: 'Tips',
        content: `• Log transactions immediately for accuracy
• Review weekly spending every Sunday
• Set realistic monthly budgets
• Use notes to remember purchase details
• Export data monthly for tax records`
      }
    ]
  },

  'github-widget': {
    title: 'GitHub Contributions Widget',
    sections: [
      {
        heading: 'Track Your Coding Activity',
        content: `Visual display of your GitHub contribution graph, stats, and achievements right on your home screen.`
      },
      {
        heading: 'Setup',
        content: `**Connect GitHub Account**
1. Tap GitHub widget
2. Enter GitHub username
3. Authorize Launch (OAuth)
4. Widget displays your contributions

**Privacy**
• Read-only access (no write permissions)
• Username stored locally
• Disconnect anytime
• No data shared with third parties`
      },
      {
        heading: 'Features',
        content: `**Contribution Graph**
• Classic GitHub heatmap visualization
• Color-coded by contribution count
• Scrollable yearly view
• Tap days to see details

**Statistics**
• Total contributions (this year)
• Current streak (consecutive days)
• Longest streak achieved
• Contribution breakdown by:
  - Commits
  - Pull requests
  - Issues opened
  - Reviews completed

**Achievements**
• Badges for milestones
• Streak celebrations
• Top contributor alerts
• Year-end summary`
      },
      {
        heading: 'Widget Sizes',
        content: `**Small (2x2)**
• Current streak
• Total contributions

**Medium (3x2)**
• Mini contribution graph (last 3 months)
• Key stats

**Large (4x3)**
• Full year graph
• Complete statistics
• Achievement showcase`
      }
    ]
  },

};
