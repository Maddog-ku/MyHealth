// Service-worker lifecycle. We only register in production builds: a SW in the Vite dev
// server would cache stale modules and interfere with HMR and e2e runs. In dev we proactively
// unregister any worker a previous production visit may have left behind.
export function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;

  if (import.meta.env.PROD) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("/sw.js").catch(() => {
        // A failed registration must never break the app; offline support is best-effort.
      });
    });
  } else {
    navigator.serviceWorker.getRegistrations().then((regs) => regs.forEach((r) => r.unregister()));
  }
}
