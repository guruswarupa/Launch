import type { Metadata } from 'next';
import LegalPage, { type LegalSection } from '../components/LegalPage';
import { GITHUB_URL } from '../docs/lib';

export const metadata: Metadata = {
  title: 'Terms of Service',
  description: 'The terms that apply when you install and use the Launch Android launcher and the apps built into it.',
  alternates: { canonical: '/terms' },
};

const sections: LegalSection[] = [
  {
    id: 'agreement',
    title: 'Agreement',
    body: (
      <>
        <p>These terms apply to the Launch app for Android (package <strong>com.guruswarupa.launch</strong>) and the apps and tools built into it. By installing or using Launch you agree to them. If you do not agree, please uninstall the app.</p>
        <p>Launch is open-source software. The source code is published under its own licence at <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer">{GITHUB_URL}</a>. These terms cover your use of the app we publish. The licence covers the code.</p>
      </>
    ),
  },
  {
    id: 'what-it-is',
    title: 'What Launch is',
    body: (
      <>
        <p>Launch is a home-screen launcher. It also includes a suite of apps: Files, Gallery, Video Player, Camera, Calculator, Calendar, Clock, Phone, Contacts and Messages, plus widgets, a document viewer, an AI assistant, web apps, a lock screen and focus tools.</p>
        <p>Some of these apps act on behalf of your phone, for example placing calls, sending texts, changing contacts and calendar events, or deleting files. They do what you ask them to do. You are responsible for what you ask.</p>
      </>
    ),
  },
  {
    id: 'your-use',
    title: 'Using the app',
    body: (
      <>
        <p>You may use Launch for personal or business purposes. You agree not to:</p>
        <ul>
          <li>use it to break the law or other people&apos;s rights;</li>
          <li>send spam, harassing or unlawful messages or calls with the Phone and Messages apps;</li>
          <li>try to interfere with the app, other apps or the Android system, or to bypass security features such as App Lock or the encrypted vault;</li>
          <li>copy, resell or republish the app under a misleading name or as if it were yours, except as the open-source licence allows.</li>
        </ul>
        <p>You are responsible for your device, your accounts and any content you create, send or store.</p>
      </>
    ),
  },
  {
    id: 'permissions',
    title: 'Permissions and your data',
    body: (
      <>
        <p>Launch asks for Android permissions only for features you use, such as contacts, call log, calls, SMS, calendar, camera, microphone, notifications, usage access, storage and overlay. You can refuse any of them, and the matching feature will not work.</p>
        <p>The app does not collect your personal data or send it to us. See the <a href="/privacy">Privacy Policy</a> for details, including the few features that connect to the internet.</p>
      </>
    ),
  },
  {
    id: 'calls-sms',
    title: 'Calls, texts and emergencies',
    body: (
      <>
        <p>Launch Phone and Launch Messages can replace your phone&apos;s dialer and SMS app when you choose to make them the default. They depend on your phone, network and carrier.</p>
        <p><strong>Launch is not a substitute for a dedicated emergency service.</strong> Keep a working fallback dialer on your phone and do not rely only on Launch to reach emergency services. Texts and calls can be delayed, fail or cost money under your carrier&apos;s plan, and we are not responsible for those costs or failures.</p>
        <p>Picture messages (MMS) and media sent through Messages are limited by your carrier.</p>
      </>
    ),
  },
  {
    id: 'third-party',
    title: 'Third-party services',
    body: (
      <>
        <p>Some features open or use services we do not run: web apps and websites, web AI assistants (such as ChatGPT, Claude, Gemini, Perplexity and DeepSeek), news feeds, weather and currency-rate providers, maps, and Google Play. Their own terms and privacy policies apply to what you do there, and we are not responsible for their content or availability.</p>
        <p>AI answers can be wrong. Check anything important before you act on it.</p>
      </>
    ),
  },
  {
    id: 'payments',
    title: 'Purchases and support',
    body: (
      <>
        <p>Launch is free to use, without ads. Optional support purchases or donations, if offered, are handled by Google Play. Google&apos;s payment terms, taxes and refund rules apply to them. A purchase does not change what the app can do for you unless the app says so at the time.</p>
      </>
    ),
  },
  {
    id: 'availability',
    title: 'Changes and availability',
    body: (
      <>
        <p>We may update, change or remove features, and release new versions, at any time. We may also stop publishing the app. Where we can, we will describe significant changes in the release notes.</p>
      </>
    ),
  },
  {
    id: 'warranty',
    title: 'No warranty and limits of liability',
    body: (
      <>
        <p>Launch is provided <strong>&quot;as is&quot; and &quot;as available&quot;</strong>, without promises that it will be error-free, uninterrupted or fit for a particular purpose. Software can have bugs, and phones differ.</p>
        <p>To the extent the law allows, we are not liable for lost data, missed calls or messages, missed alarms or reminders, or any indirect or consequential loss arising from using the app. You should keep backups of anything important.</p>
        <p><strong>Your legal rights stay.</strong> Nothing in these terms limits rights you have as a consumer under mandatory law in your country, including in the European Economic Area and the United Kingdom, or liability that cannot be excluded by law.</p>
      </>
    ),
  },
  {
    id: 'law',
    title: 'Governing law',
    body: (
      <>
        <p>These terms are governed by the law of the country where the developer is established, except that mandatory consumer-protection law of the country where you live still applies to you.</p>
      </>
    ),
  },
  {
    id: 'changes-terms',
    title: 'Changes to these terms',
    body: (
      <>
        <p>We may update these terms. The date at the top shows the latest version. If you keep using Launch after an update is published, you accept the updated terms.</p>
      </>
    ),
  },
  {
    id: 'contact',
    title: 'Contact',
    body: (
      <>
        <p>Questions, problems or requests about these terms: please open an issue on <a href={`${GITHUB_URL}/issues`} target="_blank" rel="noopener noreferrer">GitHub</a>.</p>
      </>
    ),
  },
];

export default function TermsPage() {
  return (
    <LegalPage
      title="Terms of Service"
      updated="9 November 2026"
      intro={<p>This page explains the rules for using Launch. We kept it short and in plain language.</p>}
      sections={sections}
    />
  );
}
