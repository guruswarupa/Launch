import type { DocPage } from './types';
import { gettingStartedPages } from './content/getting-started';
import { productivityPages } from './content/productivity';
import { systemToolsPages } from './content/system-tools';
import { personalizationPages } from './content/personalization';
import { privacyPages } from './content/privacy';
import { referenceGesturesPages } from './content/reference-gestures';
import { referenceSettingsPages } from './content/reference-settings';

const allPages: Record<string, DocPage> = {
  ...gettingStartedPages,
  ...productivityPages,
  ...systemToolsPages,
  ...personalizationPages,
  ...privacyPages,
  ...referenceGesturesPages,
  ...referenceSettingsPages,
};

const pageOrder = [
  'introduction',
  'installation',
  'permissions',
  'search-bar',
  'focus-mode',
  'productivity-widgets',
  'app-lock',
  'hidden-apps',
  'control-center',
  'app-management',
  'voice-commands',
  'finance-tracker',
  'sensors',
  'torch',
  'apk-sharing',
  'gestures',
  'customization',
  'privacy-dashboard',
  'web-apps',
  'speed-test',
  'github-widget',
  'gestures-guide',
  'settings',
  'privacy-security',
];

export const documentationContent: Record<string, DocPage> = Object.fromEntries(
  pageOrder.map((key) => [key, allPages[key]]),
);
