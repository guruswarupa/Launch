import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import DocBody from '../../components/DocBody';
import TableOfContents from '../../components/TableOfContents';
import { GITHUB_URL, docSlugs, getDoc, getNavItem, pageDescription, prevNext, slugify } from '../lib';

export function generateStaticParams() {
  return docSlugs().map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const doc = getDoc(slug);
  if (!doc) return {};
  const description = pageDescription(doc);
  return {
    title: doc.title,
    description,
    alternates: { canonical: `/docs/${slug}` },
    openGraph: { title: doc.title, description, type: 'article', url: `/docs/${slug}` },
  };
}

export default async function DocPageRoute({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const doc = getDoc(slug);
  if (!doc) notFound();
  const nav = getNavItem(slug);
  const { prev, next } = prevNext(slug);
  const toc = doc.sections.map((s) => ({ id: slugify(s.heading), label: s.heading }));

  return (
    <div className="flex gap-10 px-4 py-8 sm:px-8 lg:px-12">
      <article className="min-w-0 max-w-3xl flex-1">
        <nav aria-label="Breadcrumb" className="mb-4 text-xs text-subtle">
          <Link href="/docs" className="hover:text-fg">Docs</Link>
          {nav && <span> / {nav.category}</span>}
        </nav>
        <h1 className="mb-8 text-3xl font-bold tracking-tight sm:text-4xl">{doc.title}</h1>
        <DocBody page={doc} />

        <div className="mt-12 grid gap-3 sm:grid-cols-2">
          {prev ? (
            <Link href={`/docs/${prev.id}`} className="rounded-2xl border border-line bg-card p-4 transition hover:border-accent/50">
              <div className="text-xs text-subtle">← Previous</div>
              <div className="mt-1 font-semibold">{prev.label}</div>
            </Link>
          ) : <span />}
          {next ? (
            <Link href={`/docs/${next.id}`} className="rounded-2xl border border-line bg-card p-4 text-right transition hover:border-accent/50">
              <div className="text-xs text-subtle">Next →</div>
              <div className="mt-1 font-semibold">{next.label}</div>
            </Link>
          ) : <span />}
        </div>

        <div className="mt-10 rounded-2xl border border-line bg-gradient-to-r from-accent-soft to-transparent p-6">
          <h2 className="font-semibold">Need help?</h2>
          <p className="mt-1 text-sm text-muted">Check the FAQ, or open an issue and we will take a look.</p>
          <div className="mt-4 flex flex-wrap gap-3">
            <Link href="/docs/faq" className="rounded-full bg-accent px-4 py-2 text-sm font-semibold text-on-accent transition hover:brightness-110">View FAQ</Link>
            <a href={`${GITHUB_URL}/issues`} target="_blank" rel="noopener noreferrer" className="rounded-full border border-line px-4 py-2 text-sm font-medium transition hover:bg-accent-soft">Report an issue</a>
          </div>
        </div>
      </article>
      <TableOfContents items={toc} />
    </div>
  );
}
