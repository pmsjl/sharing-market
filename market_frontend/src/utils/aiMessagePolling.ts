import type { AiMessageVO, AiPageVO } from "@/api/aiController";

type PollingOptions = {
  messageId: string;
  fetchPage: (page: number) => Promise<AiPageVO<AiMessageVO>>;
  onSnapshot: (records: AiMessageVO[], total: number) => void;
  onResult: (message: AiMessageVO) => void;
  onMissing: () => void;
  onError: (error: unknown) => boolean;
};

/** 每个实例只串行查询一条助手消息；停止后忽略所有在途响应。 */
export function startAiMessagePolling(options: PollingOptions) {
  let stopped = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const stop = () => {
    stopped = true;
    if (timer !== undefined) clearTimeout(timer);
  };
  const schedule = (delay: number) => {
    if (!stopped) timer = setTimeout(() => void poll(), delay);
  };
  const poll = async () => {
    try {
      const records: AiMessageVO[] = [];
      let page = 1;
      while (!stopped) {
        const snapshot = await options.fetchPage(page);
        if (stopped) return;
        records.push(...snapshot.records);
        const target = snapshot.records.find(
          (message) => message.id === options.messageId
        );
        if (target) {
          options.onSnapshot(records, snapshot.total);
          if (target.status === "PENDING") schedule(2000);
          else {
            stop();
            options.onResult(target);
          }
          return;
        }
        if (
          !snapshot.records.length ||
          page * snapshot.pageSize >= snapshot.total
        ) {
          stop();
          options.onMissing();
          return;
        }
        page += 1;
      }
    } catch (error) {
      if (stopped) return;
      if (options.onError(error)) schedule(10000);
      else stop();
    }
  };
  schedule(2000);
  return { stop };
}
