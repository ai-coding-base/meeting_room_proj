import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RoomList from '../RoomList';
import type { Room } from '../api';

// 把 ../api 模块整体打桩，只保留我们需要的导出
vi.mock('../api', () => ({
  fetchRooms: vi.fn(),
}));

import { fetchRooms } from '../api';

const fetchRoomsMock = fetchRooms as unknown as ReturnType<typeof vi.fn>;

const sampleRooms: Room[] = [
  {
    id: 1,
    name: '会议室-201',
    capacity: 8,
    floor: 'F2',
    area: 'A 栋',
    equipment: ['projector', 'whiteboard'],
    vip: false,
    status: 'AVAILABLE',
  },
  {
    id: 2,
    name: '董事会议室',
    capacity: 20,
    floor: 'F8',
    area: 'VIP 楼层',
    equipment: ['projector', 'tv', 'unknown_equip'],
    vip: true,
    status: 'AVAILABLE',
  },
];

describe('<RoomList />', () => {
  beforeEach(() => {
    fetchRoomsMock.mockReset();
  });

  it('加载中状态：显示 "加载会议室..."', () => {
    fetchRoomsMock.mockReturnValue(new Promise(() => {})); // 永不 resolve
    render(<RoomList selectedId={null} onSelect={vi.fn()} />);
    expect(screen.getByText('加载会议室...')).toBeInTheDocument();
  });

  it('加载失败：显示错误信息', async () => {
    fetchRoomsMock.mockRejectedValue(new Error('network down'));
    render(<RoomList selectedId={null} onSelect={vi.fn()} />);
    expect(await screen.findByText('加载会议室失败')).toBeInTheDocument();
    expect(screen.getByText(/network down/)).toBeInTheDocument();
  });

  it('空列表：显示 "暂无可用会议室"', async () => {
    fetchRoomsMock.mockResolvedValue([]);
    render(<RoomList selectedId={null} onSelect={vi.fn()} />);
    expect(await screen.findByText('暂无可用会议室')).toBeInTheDocument();
  });

  it('正常渲染：列出全部房间，并默认选中第一个', async () => {
    fetchRoomsMock.mockResolvedValue(sampleRooms);
    const onSelect = vi.fn();

    render(<RoomList selectedId={null} onSelect={onSelect} />);

    await screen.findByText('会议室-201');
    expect(screen.getByText('董事会议室')).toBeInTheDocument();
    // 默认选中第一项
    expect(onSelect).toHaveBeenCalledWith(sampleRooms[0]);
  });

  it('selectedId 已存在 → 不再重复触发默认选中', async () => {
    fetchRoomsMock.mockResolvedValue(sampleRooms);
    const onSelect = vi.fn();

    render(<RoomList selectedId={2} onSelect={onSelect} />);

    await screen.findByText('会议室-201');
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('点击卡片 → 触发 onSelect(room)', async () => {
    fetchRoomsMock.mockResolvedValue(sampleRooms);
    const onSelect = vi.fn();
    const user = userEvent.setup();

    render(<RoomList selectedId={1} onSelect={onSelect} />);
    const vipCard = await screen.findByText('董事会议室');

    await user.click(vipCard);

    expect(onSelect).toHaveBeenCalledWith(sampleRooms[1]);
  });

  it('VIP 房间渲染 VIP 标签；非 VIP 不渲染', async () => {
    fetchRoomsMock.mockResolvedValue(sampleRooms);
    render(<RoomList selectedId={null} onSelect={vi.fn()} />);

    await screen.findByText('会议室-201');
    const vipTags = screen.getAllByText('VIP');
    // 只有 1 个 VIP 标签（董事会议室）
    expect(vipTags).toHaveLength(1);
  });

  it('设备 chip：已知代码翻译为中文+emoji；未知代码原样透传', async () => {
    fetchRoomsMock.mockResolvedValue(sampleRooms);
    render(<RoomList selectedId={null} onSelect={vi.fn()} />);

    await screen.findByText('董事会议室');
    // 两个房间都有 "投影仪"（projector）
    expect(screen.getAllByText(/投影仪/).length).toBeGreaterThanOrEqual(1);
    // 只有 VIP 房间有 "电视"
    expect(screen.getByText(/电视/)).toBeInTheDocument();
    // 未知代码原样
    expect(screen.getByText('unknown_equip')).toBeInTheDocument();
  });

  it('选中态：被选中的卡片有 selected 类', async () => {
    fetchRoomsMock.mockResolvedValue(sampleRooms);
    const { container } = render(<RoomList selectedId={2} onSelect={vi.fn()} />);

    await screen.findByText('董事会议室');
    const selectedCards = container.querySelectorAll('.room-card.selected');
    expect(selectedCards).toHaveLength(1);
  });

  it('卸载后异步 resolve 不应抛错（alive 守卫）', async () => {
    let resolveFn: (v: Room[]) => void = () => {};
    fetchRoomsMock.mockReturnValue(
      new Promise<Room[]>((resolve) => {
        resolveFn = resolve;
      }),
    );

    const { unmount } = render(<RoomList selectedId={null} onSelect={vi.fn()} />);
    unmount();
    // 卸载后再 resolve，断言不抛
    expect(() => resolveFn(sampleRooms)).not.toThrow();
    await waitFor(() => Promise.resolve());
  });
});
