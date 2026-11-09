import type { MetadataRoute } from 'next';
import { SITE_URL, docSlugs } from './docs/lib';

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  return [
    { url: SITE_URL, lastModified: now, priority: 1 },
    { url: `${SITE_URL}/docs`, lastModified: now, priority: 0.9 },
    { url: `${SITE_URL}/terms`, lastModified: now, priority: 0.4 },
    { url: `${SITE_URL}/privacy`, lastModified: now, priority: 0.4 },
    ...docSlugs().map((slug) => ({ url: `${SITE_URL}/docs/${slug}`, lastModified: now, priority: 0.7 })),
  ];
}
