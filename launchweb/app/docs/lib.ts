import { allDocPages } from './all-pages';
import { docsNavigation, type NavItem } from './navigation';
import type { DocPage } from './types';
import type { SearchEntry } from './search-types';

export const SITE_NAME = 'Launch';
export const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.guruswarupa.launch';
export const GITHUB_URL = 'https://github.com/guruswarupa/launch';
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000';

export interface FlatNavItem extends NavItem {
  category: string;
}

export function flatNavigation(): FlatNavItem[] {
  return docsNavigation.flatMap((c) => c.items.map((item) => ({ ...item, category: c.category })));
}

export function getDoc(slug: string): DocPage | undefined {
  return allDocPages[slug];
}

export function docSlugs(): string[] {
  return flatNavigation().filter((i) => allDocPages[i.id]).map((i) => i.id);
}

export function getNavItem(slug: string): FlatNavItem | undefined {
  return flatNavigation().find((i) => i.id === slug);
}

export function prevNext(slug: string): { prev?: FlatNavItem; next?: FlatNavItem } {
  const slugs = flatNavigation().filter((i) => allDocPages[i.id]);
  const index = slugs.findIndex((i) => i.id === slug);
  return { prev: index > 0 ? slugs[index - 1] : undefined, next: index >= 0 && index < slugs.length - 1 ? slugs[index + 1] : undefined };
}

export function slugify(text: string): string {
  return text.toLowerCase().replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

export function stripMarkup(text: string): string {
  return text
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^[>•#\s]+/gm, '')
    .replace(/^\d+\.\s*/gm, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export function pageDescription(page: DocPage): string {
  const first = page.sections[0];
  const text = first ? stripMarkup(first.content) : page.title;
  return text.length > 160 ? text.slice(0, 157).trimEnd() + '…' : text;
}

export function buildSearchIndex(): SearchEntry[] {
  const out: SearchEntry[] = [];
  for (const slug of docSlugs()) {
    const page = allDocPages[slug];
    for (const section of page.sections) {
      out.push({ slug, page: page.title, heading: section.heading, text: stripMarkup(section.content) });
    }
  }
  return out;
}
