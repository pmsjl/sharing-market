// @ts-ignore
/* eslint-disable */
import request from "@/utils/request";

/** addUserCommodityFavorites POST /api/userCommodityFavorites/add */
export async function addUserCommodityFavoritesUsingPost(
  body: API.UserCommodityFavoritesAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong_>("/api/userCommodityFavorites/add", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    data: body,
    ...(options || {})
  });
}

/** editUserCommodityFavorites POST /api/userCommodityFavorites/edit */
export async function editUserCommodityFavoritesUsingPost(
  body: API.UserCommodityFavoritesEditRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>("/api/userCommodityFavorites/edit", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    data: body,
    ...(options || {})
  });
}

/** listMyUserCommodityFavoritesVOByPage POST /api/userCommodityFavorites/my/list/page/vo */
export async function listMyUserCommodityFavoritesVoByPageUsingPost(
  body: API.UserCommodityFavoritesQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageUserCommodityFavoritesVO_>(
    "/api/userCommodityFavorites/my/list/page/vo",
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
