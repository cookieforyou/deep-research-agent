import { Breadcrumb } from '@/components/layout/Breadcrumb';

export default function ResearchLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col h-[calc(100vh-4rem)]">
      <div className="px-6 py-2 border-b bg-background/50">
        <Breadcrumb />
      </div>
      <div className="flex flex-1 overflow-hidden">
        <main className="flex-1 overflow-y-auto">{children}</main>
      </div>
    </div>
  );
}
