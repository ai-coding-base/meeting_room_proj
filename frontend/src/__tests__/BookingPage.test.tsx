import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import BookingPage from '../BookingPage';
import type { Room, BookingDetail } from '../api';
import type { UserInfo } from '../auth';

// Mock api
vi.mock('../api', () => ({
  fetchRooms: vi.fn(),
  createBooking: vi.fn(),
}));

import { fetchRooms, createBooking } from '../api';

const fetchRoomsMock = fetchRooms as unknown as ReturnType<typeof vi.fn>;
const createBookingMock = createBooking as unknown as ReturnType<typeof vi.fn>;

const room1: Room = {
  id: 1,
  name: '会议室-201',
  capacity: 8,
  floor: 'F2',
  area: 'A 栋',
  equipment: ['projector'],
  vip: false,
  status: 'AVAILABLE',
};

function employeeUser(): UserInfo {
  return { id: 1, username: 'alice', displayName: 'Alice', role: 'EMPLOYEE', roleLabel: '员工' };
}

function assistantUser(): UserInfo {
  return { id: 2, username: 'carol', displayName: 'Carol', role: 'ASSISTANT', roleLabel: '助理' };
}

function adminUser(): UserInfo {
  return { id: 99, username: 'admin', displayName: 'Admin', role: 'ADMIN', roleLabel: '管理员' };
}

describe('<BookingPage />', () => {
  beforeEach(() => {
    fetchRoomsMock.mockReset();
    createBookingMock.mockReset();
    fetchRoomsMock.mockResolvedValue([room1]);
  });

  it('普通员工：不显示代订人字段', async () => {
    render(<BookingPage user={employeeUser()} />);
    await screen.findByText('会议室-201');
    expect(screen.queryByText(/代订人/)).not.toBeInTheDocument();
  });

  it('助理：显示代订人字段，文案带 "已授权"', async () => {
    render(<BookingPage user={assistantUser()} />);
    await screen.findByText('会议室-201');
    expect(screen.getByText(/代订人 ID/)).toBeInTheDocument();
    expect(screen.getByText(/已授权用户/)).toBeInTheDocument();
  });

  it('管理员：显示代订人字段，文案带 "任意用户"', async () => {
    render(<BookingPage user={adminUser()} />);
    await screen.findByText('会议室-201');
    expect(screen.getByText(/任意用户/)).toBeInTheDocument();
  });

  it('提交成功 → 显示预订成功 + 状态', async () => {
    const detail: BookingDetail = {
      id: 100,
      roomId: 1,
      roomName: '会议室-201',
      bookerId: 1,
      principalId: 1,
      subject: '部门周会',
      startTime: '2026-06-01T09:00:00',
      endTime: '2026-06-01T10:00:00',
      attendeeCount: 5,
      status: 'CONFIRMED',
      needApproval: false,
      createdAt: '2026-05-25T10:00:00',
    };
    createBookingMock.mockResolvedValue(detail);

    const user = userEvent.setup();
    render(<BookingPage user={employeeUser()} />);
    await screen.findByText('会议室-201');

    await user.click(screen.getByRole('button', { name: /创建预订/ }));

    expect(await screen.findByText(/预订成功/)).toBeInTheDocument();
    // CONFIRMED 在 strong 与 pre 中都存在，用 getAllByText
    expect(screen.getAllByText(/CONFIRMED/).length).toBeGreaterThanOrEqual(1);

    // 验证 createBooking 入参
    expect(createBookingMock).toHaveBeenCalledTimes(1);
    const arg = createBookingMock.mock.calls[0][0];
    expect(arg.roomId).toBe(1);
    expect(arg.subject).toBe('部门周会');
    expect(arg.startTime).toMatch(/:00$/); // 后端要求 :SS
    expect(arg.endTime).toMatch(/:00$/);
  });

  it('提交失败 → 显示错误信息', async () => {
    createBookingMock.mockRejectedValue(new Error('[BOOKING_CONFLICT] 该时段已被预订'));

    const user = userEvent.setup();
    render(<BookingPage user={employeeUser()} />);
    await screen.findByText('会议室-201');

    await user.click(screen.getByRole('button', { name: /创建预订/ }));

    expect(await screen.findByText('失败')).toBeInTheDocument();
    expect(screen.getByText(/BOOKING_CONFLICT/)).toBeInTheDocument();
  });

  it('未选会议室（接口返回空）→ 提交时报 "请先选择会议室"', async () => {
    fetchRoomsMock.mockResolvedValue([]);

    const user = userEvent.setup();
    render(<BookingPage user={employeeUser()} />);

    await screen.findByText('暂无可用会议室');

    await user.click(screen.getByRole('button', { name: /创建预订/ }));

    expect(await screen.findByText('请先选择会议室')).toBeInTheDocument();
    expect(createBookingMock).not.toHaveBeenCalled();
  });

  it('助理填写代订人 ID → 透传到 createBooking 的 principalId', async () => {
    createBookingMock.mockResolvedValue({
      id: 1,
      roomId: 1,
      roomName: '会议室-201',
      bookerId: 2,
      principalId: 999,
      subject: '部门周会',
      startTime: '2026-06-01T09:00:00',
      endTime: '2026-06-01T10:00:00',
      attendeeCount: 5,
      status: 'CONFIRMED',
      needApproval: false,
      createdAt: '2026-05-25T10:00:00',
    } as BookingDetail);

    const user = userEvent.setup();
    render(<BookingPage user={assistantUser()} />);
    await screen.findByText('会议室-201');

    const principalInput = screen.getByPlaceholderText(/留空表示自订/);
    await user.type(principalInput, '999');

    await user.click(screen.getByRole('button', { name: /创建预订/ }));

    await screen.findByText(/预订成功/);
    expect(createBookingMock.mock.calls[0][0].principalId).toBe(999);
  });

  it('助理留空代订人 → principalId 不传 (undefined)', async () => {
    createBookingMock.mockResolvedValue({
      id: 1,
      roomId: 1,
      roomName: '会议室-201',
      bookerId: 2,
      principalId: 2,
      subject: 'x',
      startTime: 'x',
      endTime: 'x',
      attendeeCount: 1,
      status: 'CONFIRMED',
      needApproval: false,
      createdAt: 'x',
    } as BookingDetail);

    const user = userEvent.setup();
    render(<BookingPage user={assistantUser()} />);
    await screen.findByText('会议室-201');

    await user.click(screen.getByRole('button', { name: /创建预订/ }));
    await screen.findByText(/预订成功/);

    expect(createBookingMock.mock.calls[0][0].principalId).toBeUndefined();
  });
});
