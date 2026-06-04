const THEME_MODE_KEY = "market-theme-mode";
const THEME_ACCENT_KEY = "market-theme-accent";
const DEFAULT_ACCENT = "#2f7d5c";

export const getStoredThemeMode = () => {
  return localStorage.getItem(THEME_MODE_KEY) === "night" ? "night" : "light";
};

export const getStoredAccentColor = () => {
  return localStorage.getItem(THEME_ACCENT_KEY) || DEFAULT_ACCENT;
};

export const applyThemeMode = (mode: "light" | "night") => {
  const html = document.documentElement;
  html.classList.toggle("dark", mode === "night");
  html.dataset.theme = mode;
  localStorage.setItem(THEME_MODE_KEY, mode);
};

export const applyAccentColor = (color: string) => {
  const html = document.documentElement;
  html.style.setProperty("--el-color-primary", color);
  html.style.setProperty("--market-green", color);
  html.style.setProperty("--market-focus", `0 0 0 3px ${color}38`);
  localStorage.setItem(THEME_ACCENT_KEY, color);
};

export const restoreTheme = () => {
  applyThemeMode(getStoredThemeMode());
  applyAccentColor(getStoredAccentColor());
};
