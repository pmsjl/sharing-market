import axios from "axios";
import { ElMessage } from "element-plus";
import useUserStore from "@/store/modules/user";
//创建axios实例
const request = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL,
  timeout: 60000,
  withCredentials: true
});
//请求拦截器
request.interceptors.request.use((config) => {
  // 获取用户相关的小仓库，获取仓库内部的token,登录成功以后携带给服务器
  const userStore = useUserStore();
  if (userStore.token) {
    config.headers.satoken = userStore.token;
  }
  config.headers.Authorization =
    "Bearer " + window.localStorage.getItem("TOKEN");
  return config;
});
//响应拦截器
request.interceptors.response.use(
  (response) => {
    return response.data;
  },
  (error) => {
    // 超时或断网时 Axios 不会提供 response，必须使用可空读取。
    const responseData = error.response?.data as
      | { code?: number; message?: string }
      | undefined;
    const status = error.response?.status;
    let msg: string;
    if (error.code === "ECONNABORTED" || error.code === "ETIMEDOUT") {
      msg = "请求超时，请稍后重试";
    } else if (!error.response) {
      msg = "网络连接失败，请检查服务状态";
    } else {
      switch (status) {
        case 401:
          msg = "token过期";
          break;
        case 403:
          msg = "无权访问";
          break;
        case 404:
          msg = "请求地址错误";
          break;
        case 500:
          msg = "服务器出现问题";
          break;
        default:
          msg = responseData?.message || "请求失败，请稍后重试";
      }
    }
    error.message = responseData?.message || msg;
    error.requestMessageShown = true;
    ElMessage({
      type: "error",
      message: error.message
    });
    return Promise.reject(error);
  }
);
export default request;
