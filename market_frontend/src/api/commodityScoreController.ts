// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** addCommodityScore POST /api/commodityScore/add */
export async function addCommodityScoreUsingPost(
  body: API.CommodityScoreAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>("/api/commodityScore/add", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    data: body,
    ...(options || {})
  });
}

/** getAverageScore GET /api/commodityScore/averageScore */
export async function getAverageScoreUsingGet(
  params: API.getAverageScoreUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponse>("/api/commodityScore/averageScore", {
    method: "GET",
    params: {
      ...params
    },
    ...(options || {})
  });
}

/** editCommodityScore POST /api/commodityScore/edit */
export async function editCommodityScoreUsingPost(
  body: API.CommodityScoreEditRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>("/api/commodityScore/edit", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    data: body,
    ...(options || {})
  });
}

  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
/** listCommodityScoreVOByPage POST /api/commodityScore/list/page/vo */
export async function listCommodityScoreVoByPageUsingPost(
  body: API.CommodityScoreQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageCommodityScoreVO_>(
    "/api/commodityScore/list/page/vo",
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

/** listMyCommodityScoreVOByPage POST /api/commodityScore/my/list/page/vo */
export async function listMyCommodityScoreVoByPageUsingPost(
  body: API.CommodityScoreQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageCommodityScoreVO_>(
    "/api/commodityScore/my/list/page/vo",
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

/** updateCommodityScore POST /api/commodityScore/update */
export async function updateCommodityScoreUsingPost(
  body: API.CommodityScoreUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>("/api/commodityScore/update", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    data: body,
    ...(options || {})
  });
}
