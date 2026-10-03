import { onScopeDispose, ref, shallowRef } from "vue";

/** Each panel owns its request generation; old responses cannot replace new data. */
export const useRemote = <T>(initial: T, fetcher: () => Promise<T>) => {
  const data = shallowRef<T>(initial);
  const loading = ref(false);
  const error = ref("");
  let generation = 0;
  const load = async () => {
    const current = ++generation;
    loading.value = true;
    error.value = "";
    try {
      const value = await fetcher();
      if (current === generation) data.value = value;
    } catch (cause) {
      if (current === generation)
        error.value =
          cause instanceof Error ? cause.message : "暂时无法加载，请重试";
    } finally {
      if (current === generation) loading.value = false;
    }
  };
  onScopeDispose(() => {
    generation++;
  });
  return { data, loading, error, load };
};
export const responseData = <T>(response: {
  code?: number;
  data?: T;
  message?: string;
}): T => {
  if (response.code !== 200 || response.data == null)
    throw new Error(response.message || "暂时无法加载，请重试");
  return response.data;
};
