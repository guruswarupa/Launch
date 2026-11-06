'use client';

import { useEffect, useState } from 'react';

export interface TocItem {
  id: string;
  label: string;
}

export default function TableOfContents({ items }: { items: TocItem[] }) {
  const [active, setActive] = useState(items[0]?.id ?? '');

  useEffect(() => {
    const elements = items.map((i) => document.getElementById(i.id)).filter((e): e is HTMLElement => !!e);
    if (!elements.length) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActive(visible[0].target.id);
      },
      { rootMargin: '-90px 0px -65% 0px', threshold: 0 },
    );
    elements.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [items]);

  if (items.length < 2) return null;

  return (
    <nav aria-label="On this page" className="sticky top-24 hidden max-h-[calc(100vh-7rem)] w-56 shrink-0 overflow-y-auto xl:block">
      <h3 className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-subtle">On this page</h3>
      <ul className="space-y-1 border-l border-line">
        {items.map((item) => (
          <li key={item.id}>
            <a
              href={`#${item.id}`}
              className={`-ml-px block border-l py-1 pl-3 text-sm transition ${active === item.id ? 'border-accent font-medium text-fg' : 'border-transparent text-subtle hover:text-fg'}`}
            >
              {item.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
