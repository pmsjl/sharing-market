import { onBeforeUnmount, onMounted, type Ref } from "vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate } from "vue-router";
import { ElMessageBox } from "element-plus";

export const useUnsavedChanges = (
  dirty: Readonly<Ref<boolean>>,
  busy?: Readonly<Ref<boolean>>
) => {
  const confirmLeave = async () => {
    if (busy?.value) return false;
    if (!dirty.value) return true;
    try {
      await ElMessageBox.confirm(
        "尚有未保存的内容，离开后将丢失。",
        "离开编辑页？",
        {
          confirmButtonText: "放弃更改",
          cancelButtonText: "继续编辑",
          type: "warning"
        }
      );
      return true;
    } catch {
      return false;
    }
  };
  onBeforeRouteLeave(confirmLeave);
  onBeforeRouteUpdate((to, from) =>
    to.path === from.path ? true : confirmLeave()
  );
  const beforeUnload = (event: BeforeUnloadEvent) => {
    if (dirty.value || busy?.value) {
      event.preventDefault();
      event.returnValue = "";
    }
  };
  onMounted(() => window.addEventListener("beforeunload", beforeUnload));
  onBeforeUnmount(() =>
    window.removeEventListener("beforeunload", beforeUnload)
  );
};
