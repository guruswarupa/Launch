import type { Metadata } from 'next';
import LegalPage, { type LegalSection } from '../components/LegalPage';
import { GITHUB_URL } from '../docs/lib';

export const metadata: Metadata = {
  title: 'Privacy Policy',
  description: 'Launch collects no personal data. This page explains what stays on your phone and the few features that use the internet.',
  alternates: { canonical: '/privacy' },
};

const sections: LegalSection[] = [
  {
    id: 'summary',
    title: 'In short',
    body: (
      <ul>
        <li><strong>We do not collect your data.</strong> No account, no analytics, no advertising, no tracking.</li>
        <li>Everything you create or store in Launch stays on your phone.</li>
        <li>A few features connect to the internet because you ask them to. They are listed below.</li>
      </ul>
    ),
  },
  {
    id: 'on-device',
    title: 'What the app uses on your phone',
    body: (
      <>
        <p>To work, the built-in apps read and write data that already lives on your phone. This data is not sent to us:</p>
        <ul>
          <li><strong>Contacts and call log</strong> for Phone and Contacts: names, numbers, favorites and call history.</li>
          <li><strong>SMS and MMS</strong> for Messages: your conversations and attachments you choose to send.</li>
          <li><strong>Calendar</strong> events for Calendar, and alarms you set in Clock.</li>
          <li><strong>Files, photos, videos and audio</strong> for Files, Gallery, Camera and Video Player. The all-files permission is used only to browse and manage files you choose. File names and contents are never uploaded.</li>
          <li><strong>Camera and microphone</strong> only while you use the camera or recording.</li>
          <li><strong>App usage, notifications and installed apps</strong> for screen-time charts, app limits, badges and the app list.</li>
          <li><strong>Settings, favorites, history and saved positions</strong> (for example where you stopped a video) are stored locally.</li>
        </ul>
        <p>You can remove this data at any time from the app, Android settings or by uninstalling Launch.</p>
      </>
    ),
  },
  {
    id: 'internet',
    title: 'Features that use the internet',
    body: (
      <>
        <p>These features contact other services when you use them. We do not receive that data. The service you reach receives what is needed to answer you, such as your request or your IP address.</p>
        <ul>
          <li><strong>Web apps and web AI assistants</strong> load the site you open (for example ChatGPT, Claude, Gemini, Perplexity, DeepSeek). That site&apos;s privacy policy applies.</li>
          <li><strong>Weather, news feeds and currency rates</strong> fetch public data from their providers.</li>
          <li><strong>File Transfer</strong> sends files you pick directly to another device on your local network, encrypted, and only after the other person accepts. Nothing goes through a server.</li>
          <li><strong>Calls and texts</strong> are carried by your mobile network and carrier, as with any phone.</li>
          <li><strong>On-device AI</strong>, if you download a model, runs on your phone. The model file is downloaded once from the public host named in the app.</li>
          <li><strong>Google Play</strong> handles installs, updates and any optional support purchase under Google&apos;s own policy.</li>
        </ul>
      </>
    ),
  },
  {
    id: 'sharing',
    title: 'Sharing and selling',
    body: <p>We do not sell, rent or share your personal data, because we do not have it.</p>,
  },
  {
    id: 'security',
    title: 'Security',
    body: <p>Because your data stays on your device, it is protected by your phone&apos;s lock and Android&apos;s protections. Launch also offers App Lock and an encrypted vault that you can turn on. Keep your phone updated and locked.</p>,
  },
  {
    id: 'children',
    title: 'Children',
    body: <p>Launch does not knowingly collect information from anyone, including children. It includes a Kids Mode that limits what can be opened.</p>,
  },
  {
    id: 'rights',
    title: 'Your rights',
    body: <p>Since we hold no personal data about you, there is nothing for us to access, correct or delete. If you live in the EEA, the UK or another region with data-protection rights, those rights still apply to any service you reach through the app.</p>,
  },
  {
    id: 'open-source',
    title: 'Open source',
    body: <p>Launch is open source, so you can read exactly what it does at <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer">{GITHUB_URL}</a>.</p>,
  },
  {
    id: 'changes',
    title: 'Changes to this policy',
    body: <p>If this policy changes, the date at the top will change and the new version will be published on this page. We will not start collecting personal data without telling you here first.</p>,
  },
  {
    id: 'contact',
    title: 'Contact',
    body: <p>Questions about privacy: please open an issue on <a href={`${GITHUB_URL}/issues`} target="_blank" rel="noopener noreferrer">GitHub</a>.</p>,
  },
];

export default function PrivacyPage() {
  return (
    <LegalPage
      title="Privacy Policy"
      updated="9 November 2026"
      intro={<p>Launch is built to keep your information on your phone. This page explains what the app uses and why.</p>}
      sections={sections}
    />
  );
}
