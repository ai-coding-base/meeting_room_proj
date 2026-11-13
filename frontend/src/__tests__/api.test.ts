import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError } from 'axios';

// 准备 mock instance（在 vi.mock 工厂内复用同一引用）
type Handler = (cfg: unknown) => unknown;
type ErrHandler = (err: unknown) => unknown;

const requestHandlers: Handler[] = [];
const responseSuccessHandlers: Handler[] = [];
const responseErrorHandlers: ErrHandler[] = [];

const instance = {
  get: vi.fn(),
  post: vi.fn(),
  interceptors: {
    request: { use: (h: Handler) => requestHandlers.push(h) },
    response: {
      use: (s: Handler, e: ErrHandler) => {
        responseSuccessHandlers.push(s);
        responseErrorHandlers.push(e);
      },
    },
  },
};

vi.mock('axios', () => ({
  default: {
    create: () => instance,
    AxiosError: class AxiosError extends Error {
      response?: { status: number; data: unknown };
      constructor(msg: string, response?: { status: number; data: unknown }) {
        super(msg);
        this.response = response;
      }
    },
  },
  AxiosError: class AxiosError extends Error {
    response?: { status: number; data: unknown };
    constructor(msg: string, response?: { status: number; data: unknown }) {
      super(msg);
      this.response = response;
    }
  },
}));

// localStorage 在测试间清理
beforeEach(() => {
  localStorage.clear();
  instance.get.mockReset();
  instance.post.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('api 模块', () => {
  describe('请求拦截器', () => {
    it('有 token 时自动注入 Authorization 头', async () => {
      await import('../api'); // 触发拦截器注册
      localStorage.setItem('mr_token', 'test-token-123');

      const config = { headers: {} } as { headers: Record<string, string> };
      const result = requestHandlers[0](config) as typeof config;

      expect(result.headers.Authorization).toBe('Bearer test-token-123');
    });

    it('没有 token 时不注入 Authorization', async () => {
      await import('../api');
      // localStorage 已 clear

      const config = { headers: {} } as { headers: Record<string, string> };
      const result = requestHandlers[0](config) as typeof config;

      expect(result.headers.Authorization).toBeUndefined();
    });
  });

  describe('响应拦截器（401 处理）', () => {
    it('401 → 清空 session + 派发 mr:unauthorized 事件', async () => {
      await import('../api');
      localStorage.setItem('mr_token', 't');
      localStorage.setItem('mr_user', '{"id":1}');

      const listener = vi.fn();
      window.addEventListener('mr:unauthorized', listener);

      const err = { response: { status: 401 } };
      // 取出 401 处理器（注意 import 触发的拦截器可能会有多个，取最新）
      const handler = responseErrorHandlers[responseErrorHandlers.length - 1];
      await expect(handler(err)).rejects.toBeDefined();

      expect(localStorage.getItem('mr_token')).toBeNull();
      expect(localStorage.getItem('mr_user')).toBeNull();
      expect(listener).toHaveBeenCalledTimes(1);

      window.removeEventListener('mr:unauthorized', listener);
    });

    it('非 401（500） → 不清 session，不派发事件', async () => {
      await import('../api');
      localStorage.setItem('mr_token', 't');

      const listener = vi.fn();
      window.addEventListener('mr:unauthorized', listener);

      const err = { response: { status: 500 } };
      const handler = responseErrorHandlers[responseErrorHandlers.length - 1];
      await expect(handler(err)).rejects.toBeDefined();

      expect(localStorage.getItem('mr_token')).toBe('t');
      expect(listener).not.toHaveBeenCalled();
      window.removeEventListener('mr:unauthorized', listener);
    });
  });

  describe('createBooking', () => {
    it('成功 → 注入 X-Request-Id Header 并解包返回 data', async () => {
      const api = await import('../api');
      instance.post.mockResolvedValue({
        data: { code: '0', msg: 'ok', data: { id: 1, roomId: 1 } },
      });

      const res = await api.createBooking({
        roomId: 1,
        subject: 'x',
        startTime: '2026-06-01T10:00:00',
        endTime: '2026-06-01T11:00:00',
        attendeeCount: 5,
      });

      expect(res).toEqual({ id: 1, roomId: 1 });
      expect(instance.post).toHaveBeenCalledTimes(1);
      const [url, _body, config] = instance.post.mock.calls[0];
      expect(url).toBe('/bookings');
      expect(config?.headers?.['X-Request-Id']).toBeTruthy();
    });

    it('业务错误（code 非 0）→ 抛 [code] msg 异常', async () => {
      const api = await import('../api');
      instance.post.mockResolvedValue({
        data: { code: 'BOOKING_CONFLICT', msg: '该时段已被预订' },
      });

      await expect(
        api.createBooking({
          roomId: 1,
          subject: 'x',
          startTime: '2026-06-01T10:00:00',
          endTime: '2026-06-01T11:00:00',
          attendeeCount: 5,
        }),
      ).rejects.toThrow('[BOOKING_CONFLICT] 该时段已被预订');
    });

    it('网络错误（AxiosError 含 response.data）→ 透传业务 code/msg', async () => {
      const api = await import('../api');
      const axiosErr = new AxiosError('Request failed');
      // @ts-expect-error 模拟 AxiosError
      axiosErr.response = {
        status: 400,
        data: { code: 'INVALID_TIME', msg: '时间不对齐' },
      };
      instance.post.mockRejectedValue(axiosErr);

      await expect(
        api.createBooking({
          roomId: 1,
          subject: 'x',
          startTime: '2026-06-01T10:00:00',
          endTime: '2026-06-01T11:00:00',
          attendeeCount: 5,
        }),
      ).rejects.toThrow(/INVALID_TIME|时间不对齐/);
    });
  });

  describe('fetchRooms', () => {
    it('正常 → 返回房间列表', async () => {
      const api = await import('../api');
      const rooms = [
        {
          id: 1,
          name: 'A',
          capacity: 8,
          floor: 'F2',
          area: 'A 栋',
          equipment: ['projector'],
          vip: false,
          status: 'AVAILABLE',
        },
      ];
      instance.get.mockResolvedValue({
        data: { code: '0', msg: 'ok', data: rooms },
      });

      const res = await api.fetchRooms();

      expect(res).toEqual(rooms);
      expect(instance.get).toHaveBeenCalledWith('/rooms');
    });
  });

  describe('login', () => {
    it('成功 → 返回 token + user', async () => {
      const api = await import('../api');
      instance.post.mockResolvedValue({
        data: {
          code: '0',
          msg: 'ok',
          data: {
            token: 'tk',
            user: { id: 1, username: 'a', displayName: 'A', role: 'EMPLOYEE', roleLabel: '员工' },
          },
        },
      });

      const res = await api.login('a', 'p');
      expect(res.token).toBe('tk');
      expect(res.user.username).toBe('a');
      expect(instance.post).toHaveBeenCalledWith('/auth/login', { username: 'a', password: 'p' });
    });
  });
});
