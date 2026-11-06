import type { Metadata, Viewport } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';
import SiteHeader from './components/SiteHeader';
import SiteFooter from './components/SiteFooter';
import SearchDialog from './components/SearchDialog';
import { SITE_URL } from './docs/lib';

const geistSans = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'] });

const description =
  'Launch is a fast, private Android launcher with files, camera, document viewer, AI assistant, widgets, focus tools and a custom lock screen built in. Open source, no ads, no tracking.';

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: { default: 'Launch: the productive Android launcher', template: '%s | Launch' },
  description,
  applicationName: 'Launch',
  keywords: ['Android launcher', 'minimal launcher', 'focus mode', 'open source launcher', 'file manager', 'productivity'],
  openGraph: {
    type: 'website',
    siteName: 'Launch',
    title: 'Launch: the productive Android launcher',
    description,
  },
  twitter: { card: 'summary_large_image', title: 'Launch: the productive Android launcher', description },
};

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#eceff4' },
    { media: '(prefers-color-scheme: dark)', color: '#20242d' },
  ],
};

const themeScript = `try{var t=localStorage.getItem('theme');var d=t?t==='dark':window.matchMedia('(prefers-color-scheme: dark)').matches;document.documentElement.classList.toggle('dark',d)}catch(e){document.documentElement.classList.add('dark')}`;

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={`${geistSans.variable} ${geistMono.variable} antialiased`} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="flex min-h-screen flex-col bg-bg text-fg">
        <a href="#content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[200] focus:rounded-lg focus:bg-card focus:px-4 focus:py-2">Skip to content</a>
        <SiteHeader />
        <SearchDialog />
        <div id="content" className="flex-1">{children}</div>
        <SiteFooter />
      </body>
    </html>
  );
}
