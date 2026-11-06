import { buildSearchIndex } from '../docs/lib';

export const dynamic = 'force-static';

export function GET() {
  return Response.json(buildSearchIndex());
}
