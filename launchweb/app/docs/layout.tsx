import DocsSidebar from '../components/DocsSidebar';
import { docsNavigation } from './navigation';

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="mx-auto flex max-w-7xl flex-col lg:flex-row">
      <DocsSidebar navigation={docsNavigation} />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
