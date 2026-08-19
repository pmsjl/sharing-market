const THEME_MODE_KEY = "market-theme-mode";
const THEME_ACCENT_KEY = "market-theme-accent";
const DEFAULT_ACCENT = "campus-blue";

export type ThemeMode = "light" | "night";
export type ThemeAccentPreset = "campus-blue" | "indigo" | "lake-blue";

interface AccentScale {
  primary: string;
  hover: string;
  soft: string;
  light3: string;
  light5: string;
  light7: string;
  light9: string;
  focus: string;
}

interface AccentDefinition {
  label: string;
  light: AccentScale;
  night: AccentScale;
}

export const THEME_ACCENTS: Record<ThemeAccentPreset, AccentDefinition> = {
  "campus-blue": {
    label: "校园蓝",
    light: {
      primary: "#2563eb",
      hover: "#1d4ed8",
      soft: "#e8f0ff",
      light3: "#5b86ed",
      light5: "#8aa8f3",
      light7: "#bacbf9",
      light9: "#e8f0ff",
      focus: "rgba(37, 99, 235, 0.24)"
    },
    night: {
      primary: "#60a5fa",
      hover: "#93c5fd",
      soft: "rgba(96, 165, 250, 0.15)",
      light3: "#7eb7fb",
      light5: "#4b78ad",
      light7: "#2f4e73",
      light9: "#1b3150",
      focus: "rgba(96, 165, 250, 0.32)"
    }
  },
  indigo: {
    label: "学院靛青",
    light: {
      primary: "#4f46e5",
      hover: "#4338ca",
      soft: "#eeedff",
      light3: "#756eea",
      light5: "#9f9af0",
      light7: "#c7c4f7",
      light9: "#eeedff",
      focus: "rgba(79, 70, 229, 0.24)"
    },
    night: {
      primary: "#818cf8",
      hover: "#a5b4fc",
      soft: "rgba(129, 140, 248, 0.15)",
      light3: "#9aa3fa",
      light5: "#626aaa",
      light7: "#414873",
      light9: "#252a50",
      focus: "rgba(129, 140, 248, 0.32)"
    }
  },
  "lake-blue": {
    label: "湖面蓝",
    light: {
      primary: "#0284c7",
      hover: "#0369a1",
      soft: "#e3f5fd",
      light3: "#42a3d5",
      light5: "#81c2e3",
      light7: "#b9dff0",
      light9: "#e3f5fd",
      focus: "rgba(2, 132, 199, 0.24)"
    },
    night: {
      primary: "#38bdf8",
      hover: "#7dd3fc",
      soft: "rgba(56, 189, 248, 0.15)",
      light3: "#66caf9",
      light5: "#3488ad",
      light7: "#255b73",
      light9: "#173648",
      focus: "rgba(56, 189, 248, 0.32)"
    }
  }
};

const isAccentPreset = (value: string | null): value is ThemeAccentPreset =>
  value != null && Object.prototype.hasOwnProperty.call(THEME_ACCENTS, value);

export const getStoredThemeMode = (): ThemeMode =>
  localStorage.getItem(THEME_MODE_KEY) === "night" ? "night" : "light";

export const getStoredAccentPreset = (): ThemeAccentPreset => {
  const stored = localStorage.getItem(THEME_ACCENT_KEY);
  if (isAccentPreset(stored)) return stored;
  localStorage.setItem(THEME_ACCENT_KEY, DEFAULT_ACCENT);
  return DEFAULT_ACCENT;
};

export const applyAccentPreset = (preset: ThemeAccentPreset) => {
  const html = document.documentElement;
  const mode = html.dataset.theme === "night" ? "night" : "light";
  const scale = THEME_ACCENTS[preset][mode];

  html.dataset.accent = preset;
  html.style.setProperty("--market-primary", scale.primary);
  html.style.setProperty("--market-primary-hover", scale.hover);
  html.style.setProperty("--market-primary-soft", scale.soft);
  html.style.setProperty("--market-green", scale.primary);
  html.style.setProperty("--market-green-dark", scale.hover);
  html.style.setProperty("--market-focus", `0 0 0 3px ${scale.focus}`);
  html.style.setProperty("--el-color-primary", scale.primary);
  html.style.setProperty("--el-color-primary-light-3", scale.light3);
  html.style.setProperty("--el-color-primary-light-5", scale.light5);
  html.style.setProperty("--el-color-primary-light-7", scale.light7);
  html.style.setProperty("--el-color-primary-light-9", scale.light9);
  html.style.setProperty("--el-color-primary-dark-2", scale.hover);
  localStorage.setItem(THEME_ACCENT_KEY, preset);
};

export const applyThemeMode = (mode: ThemeMode) => {
  const html = document.documentElement;
  html.classList.toggle("dark", mode === "night");
  html.dataset.theme = mode;
  localStorage.setItem(THEME_MODE_KEY, mode);
  applyAccentPreset(getStoredAccentPreset());
};

export const restoreTheme = () => {
  applyThemeMode(getStoredThemeMode());
};
