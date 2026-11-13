import axios, { AxiosError } from 'axios';
import { clearSession, getToken, UserInfo } from './auth';

/** 后端统一响应封装 */
export interface ApiResult<T> {
  code: string;
  msg: string;
  data?: T;
}

/** 创建预订请求体 */
export interface CreateBookingRequest {
  roomId: number;
  principalId?: number;
  subject: string;
  startTime: string; // yyyy-MM-ddTHH:mm:ss
  endTime: string;
  attendeeCount: number;
  visibility?: 'PUBLIC' | 'PRIVATE';
}

/** 预订详情响应 */
export interface BookingDetail {
  id: number;
  roomId: number;
  roomName: string;
  bookerId: number;
  principalId: number;
  subject: string;
  startTime: string;
  endTime: string;
  attendeeCount: number;
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED';
  needApproval: boolean;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  user: UserInfo;
}

/** 会议室列表项 */
export interface Room {
  id: number;
  name: string;
  capacity: number;
  floor: string | null;
  area: string | null;
  equipment: string[];
  vip: boolean;
  status: 'AVAILABLE' | 'MAINTENANCE' | 'DECOMMISSIONED';
}

/** 401 自定义事件名：App 监听后清状态并切回登录页 */
export const UNAUTHORIZED_EVENT = 'mr:unauthorized';

const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
});

// 请求拦截：自动注入 Authorization
http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截：401 → 清 session + 触发事件，由 App 切到登录页
http.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    if (err.response?.status === 401) {
      clearSession();
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(err);
  },
);

/** 统一抽取业务错误 */
function unwrap<T>(p: Promise<{ data: ApiResult<T> }>): Promise<T> {
  return p.then(({ data }) => {
    if (data.code !== '0') {
      throw new Error(`[${data.code}] ${data.msg}`);
    }
    return data.data!;
  }).catch((e) => {
    if (e instanceof AxiosError && e.response?.data) {
      const body = e.response.data as ApiResult<unknown>;
      throw new Error(`[${body.code ?? e.response.status}] ${body.msg ?? e.message}`);
    }
    throw e;
  });
}

/** 登录 */
export function login(username: string, password: string): Promise<LoginResponse> {
  return unwrap(http.post<ApiResult<LoginResponse>>('/auth/login', { username, password }));
}

/** 当前用户信息（用于 token 还在但 user 缓存丢失时刷新） */
export function fetchMe(): Promise<UserInfo> {
  return unwrap(http.get<ApiResult<UserInfo>>('/auth/me'));
}

/** 登出（服务端无状态；调用主要为审计预留） */
export function logout(): Promise<void> {
  return unwrap(http.post<ApiResult<void>>('/auth/logout'));
}

/** 列出全部可用会议室 */
export function fetchRooms(): Promise<Room[]> {
  return unwrap(http.get<ApiResult<Room[]>>('/rooms'));
}

/** 创建预订（自动注入 X-Request-Id 实现客户端幂等） */
export function createBooking(req: CreateBookingRequest): Promise<BookingDetail> {
  const requestId = crypto.randomUUID();
  return unwrap(
    http.post<ApiResult<BookingDetail>>('/bookings', req, {
      headers: { 'X-Request-Id': requestId },
    }),
  );
}
