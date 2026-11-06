'use client';

export default function ThemeToggle() {
  const toggle = () => {
    const next = !document.documentElement.classList.contains('dark');
    document.documentElement.classList.toggle('dark', next);
    try {
      localStorage.setItem('theme', next ? 'dark' : 'light');
    } catch {
      /* ignore */
    }
  };

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label="Toggle light and dark theme"
      className="grid h-9 w-9 place-items-center rounded-full border border-line text-muted transition hover:bg-accent-soft hover:text-fg"
    >
      <span aria-hidden="true" className="hidden text-base leading-none dark:inline">☀</span>
      <span aria-hidden="true" className="text-base leading-none dark:hidden">☾</span>
    </button>
  );
}
