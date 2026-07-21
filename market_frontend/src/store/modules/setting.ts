// layout组件相关配置仓库
import { defineStore } from "pinia";

const useLayOutSettingStore = defineStore("SettingStore", {
  state: () => {
    return {
      fold: false, // 用户控制菜单折叠还是收起控制
      refsh: false, //仓库这个属性用于控制刷新效果
      focusMode: false // AI 助手专注模式，仅在当前页面访问期间生效
    };
  }
});
export default useLayOutSettingStore;
