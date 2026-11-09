import Link from 'next/link';
import type { ReactNode } from 'react';

export type LegalSection = { id: string; title: string; body: ReactNode };

export default function LegalPage({ title, updated, intro, sections }: { title: string; updated: string; intro: ReactNode; sections: LegalSection[] }) {
  return (
    <main className="mx-auto max-w-5xl px-4 pb-8 pt-12 sm:px-6">
      <p className="text-sm font-medium text-accent">Legal</p>
      <h1 className="mt-2 text-4xl font-bold tracking-tight sm:text-5xl">{title}</h1>
      <p className="mt-3 text-sm text-subtle">Last updated {updated}</p>
      <div className="mt-6 max-w-3xl text-base leading-7 text-muted">{intro}</div>

      <div className="mt-10 grid gap-10 lg:grid-cols-[14rem_1fr]">
        <nav aria-label="On this page" className="hidden lg:block">
          <ul className="sticky top-24 space-y-1.5 text-sm">
            {sections.map((s) => (
              <li key={s.id}>
                <a href={`#${s.id}`} className="block rounded-lg px-3 py-1.5 text-muted transition hover:bg-accent-soft hover:text-fg">{s.title}</a>
              </li>
            ))}
          </ul>
        </nav>
        <div className="max-w-3xl space-y-10">
          {sections.map((s, i) => (
            <section key={s.id} id={s.id} className="scroll-mt-24">
              <h2 className="text-xl font-semibold tracking-tight text-fg">{i + 1}. {s.title}</h2>
              <div className="mt-3 space-y-3 text-[15px] leading-7 text-muted [&_a]:text-accent [&_a]:underline [&_li]:ml-5 [&_li]:list-disc [&_strong]:text-fg">{s.body}</div>
            </section>
          ))}
        </div>
      </div>

      <div className="mt-14 flex flex-wrap gap-4 border-t border-line pt-6 text-sm text-muted">
        <Link className="hover:text-fg" href="/terms">Terms of Service</Link>
        <Link className="hover:text-fg" href="/privacy">Privacy Policy</Link>
        <Link className="hover:text-fg" href="/">Back to home</Link>
      </div>
    </main>
  );
}
