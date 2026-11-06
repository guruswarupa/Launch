import type { DocPage } from './types';
import { documentationContent } from './index';
import { newFeaturePages } from './content/new-features';
import { faqPages } from './content/faq';

export const allDocPages: Record<string, DocPage> = {
  ...documentationContent,
  ...newFeaturePages,
  ...faqPages,
};
