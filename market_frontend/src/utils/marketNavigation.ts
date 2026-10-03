export const ACCOUNT_SECTIONS = [
  { path: "/user/account", label: "个人概览" },
  { path: "/user/account/trade", label: "我的交易" },
  { path: "/user/account/content", label: "我的内容" },
  { path: "/user/account/wallet", label: "校园币" },
  { path: "/user/account/settings", label: "资料设置" }
];
export const ACCOUNT_PATHS = ACCOUNT_SECTIONS.map((item) => item.path);
export const accountSectionPath = (value: unknown): string =>
  typeof value === "string" && ACCOUNT_PATHS.includes(value)
    ? value
    : ACCOUNT_PATHS[0];
export const queryText = (value: unknown): string =>
  typeof value === "string" ? value : "";
export const queryPage = (value: unknown): number => {
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : 1;
};
export const editorReturnPath = (value: unknown, fallback: string): string => {
  const text = queryText(value);
  const path = text.split("?")[0];
  return ["/user/commodity", "/user/post", "/user/account/content"].includes(
    path
  ) &&
    !text.includes("#") &&
    !text.includes("\\")
    ? text
    : fallback;
};
export const formatCampusCoin = (value: unknown, fixed = false): string =>
  new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: fixed ? 2 : 0,
    maximumFractionDigits: 2
  }).format(Number.isFinite(Number(value)) ? Number(value) : 0);
