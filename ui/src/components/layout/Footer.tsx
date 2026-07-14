export function Footer() {
  return (
    <footer className="border-t py-4 px-6">
      <div className="flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>
          © {new Date().getFullYear()} DeepResearch — 企业级 AI 多智能体深度研究系统
        </span>
        <div className="flex gap-4">
          <span>Spring AI 2.0 + DeepSeek V4 + LangGraph4j</span>
        </div>
      </div>
    </footer>
  );
}
