// Snowflake IDs must stay strings: converting them to Number loses precision.
export function newerMessageId(a?: string, b?: string): boolean {
  if (!a) return false;
  if (!b) return true;
  return BigInt(a) > BigInt(b);
}

export type LocalMessageReadState = {
  initialized: boolean;
  read: Record<string, string>;
};

export function loadMessageReadState(userId: string): LocalMessageReadState {
  try {
    const value = JSON.parse(
      localStorage.getItem(`market:private-read:${userId}`) || "null"
    );
    if (
      value?.initialized === true &&
      value.read &&
      typeof value.read === "object"
    ) {
      const read: Record<string, string> = {};
      for (const [contact, id] of Object.entries(value.read)) {
        if (
          /^\d+$/.test(contact) &&
          typeof id === "string" &&
          /^\d+$/.test(id)
        ) {
          read[contact] = id;
        }
      }
      return { initialized: true, read };
    }
  } catch {
    // Corrupt or unavailable storage should not prevent messaging.
  }
  return { initialized: false, read: {} };
}
