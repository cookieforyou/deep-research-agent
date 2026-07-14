export default function GlobalLoading() {
  return (
    <main className="flex min-h-screen items-center justify-center p-8">
      <div className="flex flex-col items-center gap-4">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        <p className="text-sm text-muted-foreground">加载中...</p>
      </div>
    </main>
  );
}
