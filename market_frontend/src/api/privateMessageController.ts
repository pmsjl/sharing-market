// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** addPrivateMessage POST /api/privateMessage/add */
export async function addPrivateMessageUsingPost(
  body: API.PrivateMessageAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>("/api/privateMessage/add", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    data: body,
    ...(options || {})
  });
}

/** listMyPrivateMessageVOByPage POST /api/privateMessage/my/list/page/vo */
export async function listMyPrivateMessageVoByPageUsingPost(
  body: API.PrivateMessageQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePagePrivateMessageVO_>(
    "/api/privateMessage/my/list/page/vo",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      data: body,
      ...(options || {})
    }
  );
}
