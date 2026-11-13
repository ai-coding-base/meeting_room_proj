import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// 每个用例执行后清理 DOM
afterEach(() => {
  cleanup();
});

// 给 RoomList / api 用到的 crypto.randomUUID 在 jsdom 里兜底
if (!globalThis.crypto || typeof globalThis.crypto.randomUUID !== 'function') {
  // @ts-expect-error - 测试环境兜底
  globalThis.crypto = {
    ...(globalThis.crypto ?? {}),
    randomUUID: () => '00000000-0000-0000-0000-000000000000',
  };
}
