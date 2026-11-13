---
description: React + TypeScript 前端编码规范。AI 在编写、修改前端代码时必须加载本规则。
globs: frontend/**/*.{ts,tsx}
alwaysApply: false
---

# React 前端编码规范

> 适用范围：`frontend/` 目录
> 技术栈：React 18 + TypeScript 5（strict）+ Vite 5 + Ant Design 5 + TanStack Query + Zustand + RHF + Zod

---

## 1. 目录结构（强制）

```
frontend/src/
├── pages/             # 路由级页面，每个目录一个路由
│   └── booking/
│       ├── BookingListPage.tsx
│       ├── BookingCreatePage.tsx
│       └── components/        # 仅本页用的子组件
├── components/        # 跨页通用组件
│   └── common/
├── hooks/             # 跨页通用 hooks
├── api/               # 接口封装
│   ├── client.ts      # axios 实例 + 拦截器
│   ├── booking.ts     # 按业务域分文件
│   └── types.ts       # 接口请求/响应类型
├── stores/            # Zustand 全局状态（跨页用）
├── types/             # 全局类型
├── utils/             # 纯工具函数
├── routes.tsx         # 路由配置
└── main.tsx
```

**红线**：
- ❌ `pages/` 下的组件被其他页 import → 必须升级到 `components/`
- ❌ 业务逻辑写在组件里 → 必须抽到 hooks 或 stores
- ❌ 直接用 `fetch`/`axios` → 必须经 `api/` 封装

---

## 2. 命名约定

| 类型 | 规则 | 示例 |
|---|---|---|
| 组件文件 | PascalCase | `BookingCard.tsx` |
| Hook 文件 | camelCase + `use` 前缀 | `useBookingList.ts` |
| 工具/Store | camelCase | `formatDate.ts` / `userStore.ts` |
| 类型/接口 | PascalCase | `Booking` / `CreateBookingRequest` |
| 常量 | 全大写下划线 | `MAX_BOOKING_DAYS` |
| Props 接口 | `XxxProps` | `BookingCardProps` |

---

## 3. 组件规范

### 3.1 函数组件 + 显式 Props 类型

```tsx
import type { Booking } from '@/types/booking';

interface BookingCardProps {
  booking: Booking;
  onCancel?: (id: number) => void;
}

export function BookingCard({ booking, onCancel }: BookingCardProps) {
  return (
    <Card title={booking.subject}>
      <p>{booking.roomName}</p>
      {onCancel && <Button onClick={() => onCancel(booking.id)}>取消</Button>}
    </Card>
  );
}
```

**约束**：
- 一律函数组件 + Hooks，**禁止 Class 组件**
- Props 必须 `interface XxxProps` 显式声明，**禁止 `any`**
- 默认导出仅用于路由级页面；组件用具名导出
- 单文件 ≤ 300 行；超过必须拆分

### 3.2 状态分类

| 状态类型 | 工具 | 示例 |
|---|---|---|
| **服务端数据** | TanStack Query | 会议室列表、预订详情 |
| **跨页本地状态** | Zustand | 当前用户、主题 |
| **页内本地状态** | `useState` | 表单展开/折叠 |
| **表单状态** | React Hook Form + Zod | 创建预订表单 |
| **URL 状态** | `useSearchParams` | 列表筛选条件 |

**红线**：
- ❌ 用 `useState` 维护服务端数据（用 TanStack Query）
- ❌ 用 Redux（项目不引入）
- ❌ 用 Context 维护频繁变更的状态（性能问题，用 Zustand）

---

## 4. API 层规范

### 4.1 客户端封装

```ts
// src/api/client.ts
import axios from 'axios';
import { v4 as uuid } from 'uuid';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 10_000,
});

apiClient.interceptors.request.use((config) => {
  config.headers['X-Request-Id'] = uuid();
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (res) => {
    const { code, data, msg } = res.data;
    if (code !== 0) throw new BizError(code, msg);
    return data;
  },
  (err) => Promise.reject(err),
);
```

### 4.2 按业务域分文件

```ts
// src/api/booking.ts
import { apiClient } from './client';
import type { Booking, CreateBookingRequest } from './types';

export const bookingApi = {
  list: (params: { date: string; roomId?: number }) =>
    apiClient.get<Booking[]>('/api/bookings', { params }),

  create: (req: CreateBookingRequest) =>
    apiClient.post<Booking>('/api/bookings', req),

  cancel: (id: number, reason?: string) =>
    apiClient.delete(`/api/bookings/${id}`, { data: { reason } }),
};
```

### 4.3 用 TanStack Query 包装

```ts
// src/hooks/useBookings.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { bookingApi } from '@/api/booking';

export function useBookings(date: string, roomId?: number) {
  return useQuery({
    queryKey: ['bookings', date, roomId],
    queryFn: () => bookingApi.list({ date, roomId }),
    staleTime: 30_000,
  });
}

export function useCreateBooking() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: bookingApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bookings'] }),
  });
}
```

---

## 5. 表单规范（RHF + Zod）

```tsx
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const createBookingSchema = z.object({
  roomId: z.number().int().positive(),
  subject: z.string().min(1, '会议主题不能为空').max(255),
  startTime: z.string().datetime(),
  endTime: z.string().datetime(),
  attendeeCount: z.number().int().min(1),
  visibility: z.enum(['PUBLIC', 'PRIVATE']).default('PUBLIC'),
}).refine((d) => new Date(d.endTime) > new Date(d.startTime), {
  message: '结束时间必须晚于开始时间',
  path: ['endTime'],
});

type FormValues = z.infer<typeof createBookingSchema>;

export function CreateBookingForm() {
  const form = useForm<FormValues>({
    resolver: zodResolver(createBookingSchema),
    defaultValues: { visibility: 'PUBLIC' },
  });
  // ...
}
```

**约束**：
- 表单类型必须由 Zod schema 推导（`z.infer`），**禁止重复定义**
- 校验规则与后端 Bean Validation **一一对应**（容量 ≤ room.capacity 等）
- 异步提交用 mutation，期间禁用提交按钮防重复

---

## 6. TypeScript 严格度

`tsconfig.json` 必须启用：

```json
{
  "strict": true,
  "noUncheckedIndexedAccess": true,
  "noImplicitOverride": true,
  "exactOptionalPropertyTypes": true
}
```

**红线**：
- ❌ `any`（用 `unknown` + 类型守卫）
- ❌ `as` 断言（除非有运行时校验兜底）
- ❌ `// @ts-ignore`（用 `@ts-expect-error` 并附理由）
- ❌ 函数参数缺类型

---

## 7. 性能优化（按需）

| 场景 | 工具 | 不要滥用 |
|---|---|---|
| 列表渲染 | `key` 用稳定 id（不用 index） | 必须 |
| 重计算 | `useMemo` | 仅当计算成本高时 |
| 引用稳定 | `useCallback` | 仅当传给 memo 子组件时 |
| 子组件 | `React.memo` | 仅当 profiling 证明有收益时 |
| 长列表 | `react-window` | > 100 行时考虑 |

> **规则**：先写朴素代码，profiling 发现问题再优化。**禁止**为了优化而优化。

---

## 8. 样式

- 使用 Ant Design 5 主题 token，不写裸色值
- 局部样式：CSS Modules（`*.module.css`）
- 不引入 Tailwind / styled-components（保持栈精简）

---

## 9. 测试

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BookingCard } from './BookingCard';

describe('BookingCard', () => {
  it('shows cancel button when onCancel provided', async () => {
    const onCancel = vi.fn();
    render(<BookingCard booking={mockBooking()} onCancel={onCancel} />);
    await userEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(onCancel).toHaveBeenCalledWith(mockBooking().id);
  });
});
```

**约束**：
- 测试用户行为而非实现细节（用 `getByRole`/`getByText`，不用 `getByTestId` 除非必要）
- 异步交互用 `findBy` 或 `waitFor`
- API mock 用 MSW，不直接 mock axios

---

## 10. AI 编码 Checklist（前端任务专用）

- [ ] 组件放在了正确的目录（pages/components/hooks）？
- [ ] Props 类型显式声明，无 `any`？
- [ ] 服务端数据用了 TanStack Query？
- [ ] 表单用了 RHF + Zod？
- [ ] 是否有未必要的 `useMemo`/`useCallback`/`memo`？
- [ ] `tsc --noEmit` 通过？
- [ ] 单测覆盖关键交互路径？
- [ ] 是否新增了未声明的依赖？
- [ ] 是否动了用户没要求改的代码？
