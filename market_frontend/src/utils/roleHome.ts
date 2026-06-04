export const ADMIN_HOME_PATH = "/admin/userManagement";
export const USER_HOME_PATH = "/user/home";

export const getRoleHomePath = (role?: string | null) => {
  if (role === "admin") {
    return ADMIN_HOME_PATH;
  }
  if (role === "user") {
    return USER_HOME_PATH;
  }
  return "/welcome";
};

export const isPathAllowedForRole = (path: string, role?: string | null) => {
  if (!role || path === "/login" || path === "/register" || path === "/404") {
    return true;
  }

  if (path === "/" || path === "/index" || path === "/welcome") {
    return true;
  }

  if (role === "admin") {
    return path.startsWith("/admin") || path === "/user/account";
  }

  if (role === "user") {
    return path.startsWith("/user");
  }

  return false;
};

export const getSafeRedirectPath = (
  redirect: unknown,
  role?: string | null
) => {
  const path = typeof redirect === "string" ? redirect : "";
  if (path && isPathAllowedForRole(path, role)) {
    return path;
  }
  return getRoleHomePath(role);
};
