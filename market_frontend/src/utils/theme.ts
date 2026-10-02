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
      primary: "#91aed2",
      hover: "#adc2dd",
      soft: "#2b3949",
      light3: "#a0bad9",
      light5: "#63778f",
      light7: "#435264",
      light9: "#2b3949",
      focus: "#91aed2"
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
      primary: "#aaa6cb",
      hover: "#c0bddb",
      soft: "#373443",
      light3: "#b6b2d3",
      light5: "#78748e",
      light7: "#504c60",
      light9: "#373443",
      focus: "#aaa6cb"
    }
  },
  "lake-blue": {
    label: "湖面蓝",
    light: {
      primary: "#0369a1",
      hover: "#075985",
      soft: "#e3f5fd",
      light3: "#42a3d5",
      light5: "#81c2e3",
      light7: "#b9dff0",
      light9: "#e3f5fd",
      focus: "rgba(2, 132, 199, 0.24)"
    },
    night: {
      primary: "#8cb8bd",
      hover: "#aacbcf",
      soft: "#2a3c3f",
      light3: "#9cc3c8",
      light5: "#607f83",
      light7: "#40565a",
      light9: "#2a3c3f",
      focus: "#8cb8bd"
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
  html.style.setProperty(
    "--market-focus",
    `0 0 0 ${mode === "night" ? 2 : 3}px ${scale.focus}`
  );
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
