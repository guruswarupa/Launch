import type { Metadata } from 'next';
import Image from 'next/image';
import Link from 'next/link';
import PhoneMockup from './components/PhoneMockup';
import { GITHUB_URL, PLAY_URL } from './docs/lib';

export const metadata: Metadata = {
  alternates: { canonical: '/' },
};

const features = [
  { icon: '🏠', title: 'Three home layouts', text: 'List, grid or a stock-style home with folders, a dock and an app drawer. Swipe between AI, wallpaper, home, widgets and news pages.', href: '/docs/introduction' },
  { icon: '🔎', title: 'One search for everything', text: 'Apps, contacts, files and the web, with a built-in calculator, smart suggestions that stay on your phone, and voice commands.', href: '/docs/search-bar' },
  { icon: '⏱', title: 'Focus and wellbeing', text: 'Focus Mode, Pomodoro timers, per-app limits, Kids Mode, grayscale and a weekly usage recap.', href: '/docs/focus-mode' },
  { icon: '🧩', title: 'Widgets that matter', text: 'To-dos, notes, calendar, weather, finance, workouts, system stats and any Android widget you like.', href: '/docs/productivity-widgets' },
  { icon: '📂', title: 'Files and storage', text: 'A full file manager with a recycle bin, ZIP support and a visual storage analyzer.', href: '/docs/files-and-storage' },
  { icon: '📸', title: 'Camera and gallery', text: 'Photo and video, a swipeable gallery and a smooth video player, all built in.', href: '/docs/camera-and-media' },
  { icon: '📄', title: 'Offline document viewer', text: 'Open PDF, Word, Excel, PowerPoint, Markdown, code and more with find, zoom and reading themes.', href: '/docs/document-viewer' },
  { icon: '🔓', title: 'Custom lock screen', text: 'Clock, swipe-away notifications, music with lyrics, flashlight and camera over the system lock screen.', href: '/docs/lock-screen' },
  { icon: '🤖', title: 'AI assistant', text: 'Run a small model fully offline, or open your favorite AI site, plus an RSS news page.', href: '/docs/ai-assistant' },
  { icon: '🌐', title: 'Web apps with ad blocking', text: 'Turn any website into an app, share it with a QR code and browse with built-in ad blocking.', href: '/docs/web-apps' },
  { icon: '🗝', title: 'Vault and backups', text: 'An encrypted vault with fingerprint unlock and backups protected by your own passphrase.', href: '/docs/vault-and-backup' },
  { icon: '🎨', title: 'Make it yours', text: 'Themes, accent colors, icon packs, fonts, wallpaper blur and ten languages.', href: '/docs/customization' },
];

const steps = [
  { n: '1', title: 'Install and open', text: 'Get Launch from Google Play. The welcome screen lets you import a backup or start fresh.' },
  { n: '2', title: 'Take the 1-minute tour', text: 'A swipeable tour explains every feature and asks for each permission with a clear reason.' },
  { n: '3', title: 'Make it your home', text: 'Set Launch as your home app on the last page and you are ready to go.' },
];

export default function Home() {
  return (
    <>
      <section className="relative overflow-hidden">
        <div aria-hidden="true" className="pointer-events-none absolute -top-40 left-1/2 h-[28rem] w-[60rem] -translate-x-1/2 rounded-full bg-gradient-to-r from-accent/25 via-accent-2/20 to-transparent blur-3xl" />
        <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-4 pb-16 pt-14 sm:px-6 lg:grid-cols-2 lg:pt-24">
          <div className="animate-fade-in">
            <p className="inline-flex items-center gap-2 rounded-full border border-line bg-card px-3 py-1 text-xs font-medium text-muted">
              <span aria-hidden="true">●</span> Open source · No ads · No tracking
            </p>
            <h1 className="mt-5 text-4xl font-bold leading-[1.05] tracking-tight sm:text-5xl lg:text-6xl">
              The launcher that does <span className="bg-gradient-to-r from-accent to-accent-2 bg-clip-text text-transparent">everything</span>, quietly.
            </h1>
            <p className="mt-5 max-w-xl text-lg text-muted">
              Launch is a fast, private Android home screen with files, camera, documents, an AI assistant, widgets and focus tools built in, so you need fewer apps.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <a href={PLAY_URL} target="_blank" rel="noopener noreferrer" className="inline-flex items-center rounded-full bg-gradient-to-r from-accent to-accent-2 px-7 py-3.5 text-sm font-semibold text-on-accent shadow-lg shadow-accent/25 transition hover:brightness-110">
                Get it on Google Play <span aria-hidden="true" className="ml-2">→</span>
              </a>
              <Link href="/docs" className="inline-flex items-center rounded-full border border-line bg-card px-7 py-3.5 text-sm font-semibold transition hover:border-accent/50">Read the docs</Link>
              <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="inline-flex items-center rounded-full px-5 py-3.5 text-sm font-semibold text-muted transition hover:text-fg">View on GitHub</a>
            </div>
            <dl className="mt-10 grid max-w-md grid-cols-3 gap-4 text-sm">
              {[['10', 'languages'], ['30+', 'widgets & tools'], ['0', 'ads or trackers']].map(([v, l]) => (
                <div key={l}>
                  <dt className="text-2xl font-bold">{v}</dt>
                  <dd className="text-subtle">{l}</dd>
                </div>
              ))}
            </dl>
          </div>
          <div className="mx-auto flex w-full justify-center">
            <div className="scale-90 sm:scale-100">
              <PhoneMockup />
            </div>
          </div>
        </div>
      </section>

      <section id="features" className="mx-auto max-w-7xl scroll-mt-20 px-4 py-16 sm:px-6">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-bold tracking-tight sm:text-4xl">Everything in one place</h2>
          <p className="mt-3 text-muted">Every feature works offline and locally by default. Pick what you want, ignore the rest.</p>
        </div>
        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f) => (
            <Link key={f.title} href={f.href} className="group rounded-2xl border border-line bg-card p-6 transition hover:-translate-y-1 hover:border-accent/50 hover:shadow-xl hover:shadow-accent/10">
              <span aria-hidden="true" className="grid h-11 w-11 place-items-center rounded-xl bg-accent-soft text-xl">{f.icon}</span>
              <h3 className="mt-4 text-lg font-semibold">{f.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted">{f.text}</p>
              <span className="mt-4 inline-block text-sm font-medium text-accent">Learn more <span aria-hidden="true" className="transition group-hover:translate-x-1">→</span></span>
            </Link>
          ))}
        </div>
      </section>

      <section className="bg-bg-soft py-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6">
          <h2 className="text-center text-3xl font-bold tracking-tight sm:text-4xl">Set up in a minute</h2>
          <ol className="mt-12 grid gap-6 md:grid-cols-3">
            {steps.map((s) => (
              <li key={s.n} className="rounded-2xl border border-line bg-card p-6">
                <span className="grid h-9 w-9 place-items-center rounded-full bg-gradient-to-r from-accent to-accent-2 text-sm font-bold text-on-accent">{s.n}</span>
                <h3 className="mt-4 text-lg font-semibold">{s.title}</h3>
                <p className="mt-2 text-sm text-muted">{s.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
        <div className="grid items-center gap-8 rounded-3xl border border-line bg-gradient-to-br from-accent-soft via-card to-card p-8 sm:p-12 lg:grid-cols-[1fr_auto]">
          <div>
            <h2 className="text-3xl font-bold tracking-tight">Private by design</h2>
            <ul className="mt-5 space-y-2.5 text-muted">
              <li>✓ No accounts, ads, analytics or telemetry</li>
              <li>✓ Notes, finance, vault and backups stay on your phone</li>
              <li>✓ On-device AI that never sends what you type anywhere</li>
              <li>✓ Every permission is optional and explained before you allow it</li>
            </ul>
            <Link href="/docs/privacy-security" className="mt-6 inline-block text-sm font-semibold text-accent">Read about privacy →</Link>
          </div>
          <Image src="/logo.png" alt="" width={128} height={128} className="hidden rounded-3xl shadow-2xl shadow-accent/30 lg:block" />
        </div>
      </section>

      <section className="mx-auto max-w-4xl px-4 pb-8 text-center sm:px-6">
        <h2 className="text-3xl font-bold tracking-tight">Ready to try Launch?</h2>
        <p className="mt-3 text-muted">Free, open source and a minute away from a calmer phone.</p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <a href={PLAY_URL} target="_blank" rel="noopener noreferrer" className="rounded-full bg-gradient-to-r from-accent to-accent-2 px-7 py-3.5 text-sm font-semibold text-on-accent shadow-lg shadow-accent/25 transition hover:brightness-110">Download for Android</a>
          <Link href="/docs/introduction" className="rounded-full border border-line bg-card px-7 py-3.5 text-sm font-semibold transition hover:border-accent/50">Start with the guide</Link>
        </div>
      </section>
    </>
  );
}
