import type { Metadata } from 'next';
import Link from 'next/link';
import { docsNavigation } from './navigation';
import { getDoc, pageDescription } from './lib';

export const metadata: Metadata = {
  title: 'Documentation',
  description: 'Guides for every Launch feature: setup, focus tools, files, camera, lock screen, AI, privacy and more.',
  alternates: { canonical: '/docs' },
};

export default function DocsHome() {
  return (
    <div className="px-4 py-10 sm:px-8 lg:px-12">
      <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Launch documentation</h1>
      <p className="mt-3 max-w-2xl text-muted">Everything Launch can do, explained with short guides. Press <kbd className="rounded border border-line px-1.5 text-xs">Ctrl K</kbd> to search.</p>

      <div className="mt-10 space-y-12">
        {docsNavigation.map((cat) => (
          <section key={cat.category}>
            <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-subtle">{cat.category}</h2>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {cat.items.map((item) => {
                const doc = getDoc(item.id);
                if (!doc) return null;
                return (
                  <Link key={item.id} href={`/docs/${item.id}`} className="group rounded-2xl border border-line bg-card p-4 transition hover:-translate-y-0.5 hover:border-accent/50 hover:shadow-lg hover:shadow-accent/10">
                    <div className="flex items-center gap-3">
                      <span aria-hidden="true" className="grid h-9 w-9 place-items-center rounded-xl bg-accent-soft text-lg">{item.icon}</span>
                      <span className="font-semibold">{item.label}</span>
                    </div>
                    <p className="mt-3 line-clamp-2 text-sm text-muted">{pageDescription(doc)}</p>
                  </Link>
                );
              })}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
