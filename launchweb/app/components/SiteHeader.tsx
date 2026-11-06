'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useState } from 'react';
import ThemeToggle from './ThemeToggle';

const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.guruswarupa.launch';
const GITHUB_URL = 'https://github.com/guruswarupa/launch';

export default function SiteHeader() {
  const [open, setOpen] = useState(false);
  const openSearch = () => window.dispatchEvent(new Event('open-search'));

  const links = [
    { href: '/#features', label: 'Features' },
    { href: '/docs', label: 'Docs' },
    { href: GITHUB_URL, label: 'GitHub', external: true },
  ];

  return (
    <header className="sticky top-0 z-50 border-b border-line bg-bg/80 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-2.5 font-semibold" onClick={() => setOpen(false)}>
          <Image src="/logo.png" alt="" width={32} height={32} className="rounded-lg" />
          <span className="text-lg tracking-tight">Launch</span>
        </Link>

        <nav className="ml-6 hidden items-center gap-1 md:flex" aria-label="Main">
          {links.map((l) =>
            l.external ? (
              <a key={l.label} href={l.href} target="_blank" rel="noopener noreferrer" className="rounded-full px-3 py-1.5 text-sm text-muted transition hover:bg-accent-soft hover:text-fg">{l.label}</a>
            ) : (
              <Link key={l.label} href={l.href} className="rounded-full px-3 py-1.5 text-sm text-muted transition hover:bg-accent-soft hover:text-fg">{l.label}</Link>
            ),
          )}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={openSearch}
            className="hidden items-center gap-2 rounded-full border border-line px-3.5 py-1.5 text-sm text-subtle transition hover:text-fg sm:flex"
            aria-label="Search documentation"
          >
            <span aria-hidden="true">⌕</span> Search <kbd className="rounded border border-line px-1.5 text-[10px]">Ctrl K</kbd>
          </button>
          <button type="button" onClick={openSearch} className="grid h-9 w-9 place-items-center rounded-full border border-line text-muted sm:hidden" aria-label="Search documentation">⌕</button>
          <ThemeToggle />
          <a
            href={PLAY_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="hidden rounded-full bg-gradient-to-r from-accent to-accent-2 px-4 py-2 text-sm font-semibold text-on-accent shadow-lg shadow-accent/20 transition hover:brightness-110 sm:inline-block"
          >
            Get the app
          </a>
          <button
            type="button"
            onClick={() => setOpen((o) => !o)}
            className="grid h-9 w-9 place-items-center rounded-full border border-line text-muted md:hidden"
            aria-label="Toggle menu"
            aria-expanded={open}
          >
            {open ? '✕' : '☰'}
          </button>
        </div>
      </div>

      {open && (
        <nav className="border-t border-line bg-bg px-4 py-3 md:hidden" aria-label="Mobile">
          <ul className="space-y-1">
            {links.map((l) => (
              <li key={l.label}>
                {l.external ? (
                  <a href={l.href} target="_blank" rel="noopener noreferrer" className="block rounded-xl px-3 py-2.5 text-muted hover:bg-accent-soft">{l.label}</a>
                ) : (
                  <Link href={l.href} onClick={() => setOpen(false)} className="block rounded-xl px-3 py-2.5 text-muted hover:bg-accent-soft">{l.label}</Link>
                )}
              </li>
            ))}
            <li>
              <a href={PLAY_URL} target="_blank" rel="noopener noreferrer" className="block rounded-xl bg-gradient-to-r from-accent to-accent-2 px-3 py-2.5 text-center font-semibold text-on-accent">Get the app</a>
            </li>
          </ul>
        </nav>
      )}
    </header>
  );
}
