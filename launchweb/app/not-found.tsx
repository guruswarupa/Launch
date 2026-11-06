import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="mx-auto max-w-xl px-4 py-32 text-center">
      <p className="text-5xl" aria-hidden="true">🧭</p>
      <h1 className="mt-6 text-3xl font-bold tracking-tight">Page not found</h1>
      <p className="mt-3 text-muted">That page does not exist, or it moved.</p>
      <div className="mt-8 flex justify-center gap-3">
        <Link href="/" className="rounded-full border border-line bg-card px-6 py-3 text-sm font-semibold">Home</Link>
        <Link href="/docs" className="rounded-full bg-accent px-6 py-3 text-sm font-semibold text-on-accent">Browse docs</Link>
      </div>
    </div>
  );
}
