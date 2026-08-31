import request from "@/utils/request";

export interface CampusCoinWalletVO {
  balance: number;
}

export interface CampusCoinTransactionVO {
  id: string;
  amount: number;
  balanceBefore: number;
  balanceAfter: number;
  transactionType: string;
  remark?: string;
  createTime: string;
}

export interface CampusCoinPageVO<T> {
  current: number;
  pageSize: number;
  total: number;
  records: T[];
}

interface Result<T> {
  code: number;
  data?: T;
  message?: string;
}

export const getMyCampusCoinWallet = () =>
  request<unknown, Result<CampusCoinWalletVO>>({
    url: "/api/campusCoin/me",
    method: "GET"
  });

export const listMyCampusCoinTransactions = (body: {
  current: number;
  pageSize: number;
}) =>
  request<unknown, Result<CampusCoinPageVO<CampusCoinTransactionVO>>>({
    url: "/api/campusCoin/my/transactions/page",
    method: "POST",
    data: body
  });

export const grantCampusCoin = (body: {
  userId: string;
  amount: number;
  reason: string;
}) =>
  request<unknown, Result<CampusCoinWalletVO>>({
    url: "/api/campusCoin/admin/grant",
    method: "POST",
    data: body
  });
