import Image from 'next/image';
import Link from 'next/link';
import { GITHUB_URL, PLAY_URL } from '../docs/lib';

export default function SiteFooter() {
  return (
    <footer className="mt-24 border-t border-line bg-bg-soft">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-4">
        <div className="md:col-span-2">
          <div className="flex items-center gap-2.5 font-semibold">
            <Image src="/logo.png" alt="" width={32} height={32} className="rounded-lg" />
            <span className="text-lg">Launch</span>
          </div>
          <p className="mt-3 max-w-sm text-sm text-muted">A fast, private Android launcher with files, camera, documents, AI and focus tools built in. Open source, no ads, no tracking.</p>
        </div>
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wider text-subtle">Explore</h3>
          <ul className="mt-3 space-y-2 text-sm text-muted">
            <li><Link className="hover:text-fg" href="/#features">Features</Link></li>
            <li><Link className="hover:text-fg" href="/docs">Documentation</Link></li>
            <li><Link className="hover:text-fg" href="/docs/faq">FAQ</Link></li>
            <li><Link className="hover:text-fg" href="/terms">Terms of Service</Link></li>
            <li><Link className="hover:text-fg" href="/privacy">Privacy Policy</Link></li>
          </ul>
        </div>
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wider text-subtle">Get Launch</h3>
          <ul className="mt-3 space-y-2 text-sm text-muted">
            <li><a className="hover:text-fg" href={PLAY_URL} target="_blank" rel="noopener noreferrer">Google Play</a></li>
            <li><a className="hover:text-fg" href={GITHUB_URL} target="_blank" rel="noopener noreferrer">GitHub</a></li>
            <li><a className="hover:text-fg" href={`${GITHUB_URL}/issues`} target="_blank" rel="noopener noreferrer">Report an issue</a></li>
          </ul>
        </div>
      </div>
      <div className="border-t border-line py-4 text-center text-xs text-subtle">© {new Date().getFullYear()} Launch. Open source under its project license.</div>
    </footer>
  );
}
