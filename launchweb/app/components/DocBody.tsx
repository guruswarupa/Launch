import type { ReactNode } from 'react';
import type { DocPage } from '../docs/types';
import { slugify } from '../docs/lib';

function inline(text: string): ReactNode[] {
  const tokens = text.split(/(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g);
  return tokens.map((t, i) => {
    if (t.startsWith('**') && t.endsWith('**') && t.length > 4) return <strong key={i}>{t.slice(2, -2)}</strong>;
    if (t.startsWith('`') && t.endsWith('`') && t.length > 2) return <code key={i}>{t.slice(1, -1)}</code>;
    const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(t);
    if (link) {
      const external = /^https?:/.test(link[2]);
      return <a key={i} href={link[2]} {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>{link[1]}</a>;
    }
    return t;
  });
}

function renderContent(content: string): ReactNode[] {
  const lines = content.split('\n');
  const out: ReactNode[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) { i++; continue; }

    if (line.startsWith('• ')) {
      const items: ReactNode[] = [];
      while (i < lines.length && lines[i].startsWith('• ')) { items.push(<li key={i}>{inline(lines[i].slice(2))}</li>); i++; }
      out.push(<ul key={`ul-${i}`}>{items}</ul>);
      continue;
    }

    if (/^\d+\.\s/.test(line)) {
      const items: ReactNode[] = [];
      while (i < lines.length && /^\d+\.\s/.test(lines[i])) { items.push(<li key={i}>{inline(lines[i].replace(/^\d+\.\s*/, ''))}</li>); i++; }
      out.push(<ol key={`ol-${i}`}>{items}</ol>);
      continue;
    }

    if (line.startsWith('> ')) {
      const parts: string[] = [];
      while (i < lines.length && lines[i].startsWith('> ')) { parts.push(lines[i].slice(2)); i++; }
      out.push(
        <aside key={`q-${i}`} className="my-4 rounded-xl border border-accent/30 bg-accent-soft px-4 py-3 text-sm text-muted [&_strong]:text-fg">
          {inline(parts.join(' '))}
        </aside>,
      );
      continue;
    }

    if (line.startsWith('### ')) { out.push(<h4 key={i}>{inline(line.slice(4))}</h4>); i++; continue; }

    out.push(<p key={i}>{inline(line)}</p>);
    i++;
  }
  return out;
}

export default function DocBody({ page }: { page: DocPage }) {
  return (
    <div className="doc-prose">
      {page.sections.map((section) => {
        const id = slugify(section.heading);
        return (
          <section key={id} id={id} className="mb-10 scroll-mt-24">
            <h2 className="group mb-3 flex items-center gap-2 text-xl font-semibold tracking-tight sm:text-2xl">
              {section.heading}
              <a href={`#${id}`} aria-label={`Link to ${section.heading}`} className="text-base font-normal text-subtle opacity-0 transition group-hover:opacity-100 focus:opacity-100">#</a>
            </h2>
            {renderContent(section.content)}
          </section>
        );
      })}
    </div>
  );
}
