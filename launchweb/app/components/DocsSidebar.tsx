'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';
import type { NavCategory } from '../docs/navigation';

export default function DocsSidebar({ navigation }: { navigation: NavCategory[] }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  const list = (
    <nav aria-label="Documentation" className="space-y-7">
      {navigation.map((section) => (
        <div key={section.category}>
          <h3 className="mb-2 px-3 text-[11px] font-semibold uppercase tracking-wider text-subtle">{section.category}</h3>
          <ul className="space-y-0.5">
            {section.items.map((item) => {
              const href = `/docs/${item.id}`;
              const active = pathname === href;
              return (
                <li key={item.id}>
                  <Link
                    href={href}
                    aria-current={active ? 'page' : undefined}
                    onClick={() => setOpen(false)}
                    className={`flex items-center gap-2.5 rounded-lg px-3 py-1.5 text-sm transition ${active ? 'bg-accent-soft font-medium text-fg' : 'text-muted hover:bg-accent-soft/60 hover:text-fg'}`}
                  >
                    <span aria-hidden="true" className="w-5 text-center">{item.icon}</span>
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );

  return (
    <>
      <div className="sticky top-16 z-30 border-b border-line bg-bg/90 px-4 py-2 backdrop-blur lg:hidden">
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="flex items-center gap-2 rounded-lg border border-line px-3 py-1.5 text-sm text-muted"
          aria-expanded={open}
          aria-controls="docs-drawer"
        >
          ☰ Browse docs
        </button>
      </div>

      <aside className="sticky top-16 hidden h-[calc(100vh-4rem)] w-72 shrink-0 overflow-y-auto border-r border-line px-3 py-8 lg:block">{list}</aside>

      {open && (
        <div className="fixed inset-0 z-[60] lg:hidden" id="docs-drawer" role="dialog" aria-modal="true" aria-label="Documentation menu">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-[82%] max-w-xs overflow-y-auto border-r border-line bg-bg px-3 py-6 shadow-2xl">
            <div className="mb-4 flex items-center justify-between px-3">
              <span className="font-semibold">Documentation</span>
              <button type="button" onClick={() => setOpen(false)} aria-label="Close menu" className="grid h-8 w-8 place-items-center rounded-full border border-line">✕</button>
            </div>
            {list}
          </div>
        </div>
      )}
    </>
  );
}
