import { useCallback, useEffect, useState } from "react";

export type FontScale = "small" | "normal" | "large" | "xlarge";

// Applied to <html> font-size. Tailwind sizes are rem-based, so the whole app —
// including the AI assistant widget — scales proportionally from this single root.
const ROOT_SIZE: Record<FontScale, string> = {
  small: "14px",
  normal: "16px",
  large: "18px",
  xlarge: "20px",
};

function applyFontScale(scale: FontScale) {
  document.documentElement.style.fontSize = ROOT_SIZE[scale] ?? ROOT_SIZE.normal;
}

export function useFontScale() {
  const [scale, setScale] = useState<FontScale>(
    () => (localStorage.getItem("fontScale") as FontScale | null) ?? "normal",
  );

  useEffect(() => {
    applyFontScale(scale);
    localStorage.setItem("fontScale", scale);
  }, [scale]);

  const setFontScale = useCallback((next: FontScale) => setScale(next), []);
  return { scale, setFontScale };
}
