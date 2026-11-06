'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { SearchEntry } from '../docs/search-types';

interface Result extends SearchEntry {
  score: number;
}

function search(entries: SearchEntry[], query: string): Result[] {
  const tokens = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (!tokens.length) return [];
  const results: Result[] = [];
  for (const e of entries) {
    const page = e.page.toLowerCase();
    const heading = e.heading.toLowerCase();
    const text = e.text.toLowerCase();
    let score = 0;
    let ok = true;
    for (const t of tokens) {
      let s = 0;
      if (page.includes(t)) s += 6;
      if (heading.includes(t)) s += 4;
      if (text.includes(t)) s += 1;
      if (s === 0) { ok = false; break; }
      score += s;
    }
    if (ok) results.push({ ...e, score });
  }
  return results.sort((a, b) => b.score - a.score).slice(0, 8);
}

function snippet(text: string, query: string): string {
  const t = query.toLowerCase().split(/\s+/)[0] ?? '';
  const i = t ? text.toLowerCase().indexOf(t) : -1;
  const start = Math.max(0, i - 40);
  const out = text.slice(start, start + 120);
  return (start > 0 ? '…' : '') + out + (start + 120 < text.length ? '…' : '');
}

export default function SearchDialog() {
  const router = useRouter();
  const [entries, setEntries] = useState<SearchEntry[]>([]);
  const loaded = useRef(false);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const results = useMemo(() => search(entries, query), [entries, query]);

  const show = useCallback(() => {
    setQuery('');
    setActive(0);
    setOpen(true);
    if (!loaded.current) {
      loaded.current = true;
      fetch('/search-index.json').then((r) => r.json()).then(setEntries).catch(() => { loaded.current = false; });
    }
    requestAnimationFrame(() => inputRef.current?.focus());
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        show();
      } else if (e.key === 'Escape') {
        setOpen(false);
      }
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('open-search', show);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('open-search', show);
    };
  }, [show]);

  const go = (r: Result) => {
    setOpen(false);
    router.push(`/docs/${r.slug}`);
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-start justify-center bg-black/50 px-4 pt-[12vh] backdrop-blur-sm" onMouseDown={() => setOpen(false)}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Search documentation"
        className="w-full max-w-xl overflow-hidden rounded-2xl border border-line bg-card shadow-2xl"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <input
          ref={inputRef}
          value={query}
          onChange={(e) => { setQuery(e.target.value); setActive(0); }}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, results.length - 1)); }
            if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
            if (e.key === 'Enter' && results[active]) go(results[active]);
          }}
          placeholder="Search the docs…"
          className="w-full border-b border-line bg-transparent px-5 py-4 text-base outline-none placeholder:text-subtle"
        />
        <ul className="max-h-[50vh] overflow-y-auto p-2">
          {query && results.length === 0 && <li className="px-4 py-6 text-center text-sm text-subtle">No results for “{query}”</li>}
          {!query && <li className="px-4 py-6 text-center text-sm text-subtle">Type to search every guide</li>}
          {results.map((r, i) => (
            <li key={`${r.slug}-${r.heading}`}>
              <button
                type="button"
                onMouseEnter={() => setActive(i)}
                onClick={() => go(r)}
                className={`w-full rounded-xl px-4 py-3 text-left transition ${i === active ? 'bg-accent-soft' : ''}`}
              >
                <div className="text-sm font-semibold">{r.page} <span className="font-normal text-subtle">/ {r.heading}</span></div>
                <div className="mt-0.5 line-clamp-2 text-xs text-muted">{snippet(r.text, query)}</div>
              </button>
            </li>
          ))}
        </ul>
        <div className="flex items-center justify-between border-t border-line px-4 py-2 text-[11px] text-subtle">
          <span>↑↓ to move, Enter to open, Esc to close</span>
          <span>Ctrl/⌘ K</span>
        </div>
      </div>
    </div>
  );
}
