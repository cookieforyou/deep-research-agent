export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-8">
      <div className="flex flex-col items-center gap-8 max-w-2xl text-center">
        {/* Logo */}
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground text-lg font-bold">
            DR
          </div>
          <h1 className="text-3xl font-bold tracking-tight">DeepResearch</h1>
        </div>

        <p className="text-lg text-muted-foreground">
          企业级 AI 多智能体深度研究系统 —— Phase 0 脚手架就绪
        </p>

        <div className="flex gap-3">
          <div className="rounded-lg border bg-card px-4 py-2 text-sm text-muted-foreground">
            Next.js 15 + TypeScript
          </div>
          <div className="rounded-lg border bg-card px-4 py-2 text-sm text-muted-foreground">
            Tailwind CSS v4
          </div>
          <div className="rounded-lg border bg-card px-4 py-2 text-sm text-muted-foreground">
            shadcn/ui
          </div>
        </div>
      </div>
    </main>
  );
}
