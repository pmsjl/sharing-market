// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** addComment POST /api/comment/add */
export async function addCommentUsingPost(
  body: API.CommentAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>("/api/comment/add", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    data: body,
    ...(options || {})
  });
}

/** deleteComment POST /api/comment/delete */
export async function deleteCommentUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>("/api/comment/delete", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    data: body,
    ...(options || {})
  });
}

/** getCommentByPostId GET /api/comment/get/questonComment */
export async function getCommentByPostIdUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getCommentByPostIdUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListCommentVO_>(
    "/api/comment/get/questonComment",
    {
      method: "GET",
      params: {
        ...params
      },
      ...(options || {})
    }
  );
}

/** listMyComments POST /api/comment/myComments */
export async function listMyCommentsUsingPost(options?: {
  [key: string]: any;
}) {
  return request<API.BaseResponseListMyCommentVO_>("/api/comment/myComments", {
    method: "POST",
    ...(options || {})
  });
}
