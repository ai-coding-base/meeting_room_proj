import { useEffect, useState } from 'react';
import { fetchRooms, Room } from './api';

/** 设备代码 → 中文 + emoji */
const EQUIP_LABEL: Record<string, string> = {
  projector: '📽️ 投影仪',
  video_conf: '📞 会议电话',
  whiteboard: '📝 白板',
  tv: '📺 电视',
  phone: '☎️ 座机',
};

interface Props {
  selectedId: number | null;
  onSelect: (room: Room) => void;
}

export default function RoomList({ selectedId, onSelect }: Props) {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    fetchRooms()
      .then((list) => {
        if (!alive) return;
        setRooms(list);
        // 默认选中第一个，避免用户必须主动点
        if (list.length > 0 && selectedId === null) {
          onSelect(list[0]);
        }
      })
      .catch((e) => alive && setError(e instanceof Error ? e.message : String(e)))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
    // 仅首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) return <div className="muted">加载会议室...</div>;
  if (error) {
    return (
      <div className="alert alert-error">
        <strong>加载会议室失败</strong>
        <pre>{error}</pre>
      </div>
    );
  }
  if (rooms.length === 0) return <div className="muted">暂无可用会议室</div>;

  return (
    <div className="room-list">
      {rooms.map((room) => {
        const selected = room.id === selectedId;
        return (
          <button
            type="button"
            key={room.id}
            className={`room-card${selected ? ' selected' : ''}`}
            onClick={() => onSelect(room)}
          >
            <div className="room-card-header">
              <span className="room-name">{room.name}</span>
              {room.vip && <span className="vip-tag">VIP</span>}
            </div>
            <div className="room-meta">
              <span>{[room.area, room.floor].filter(Boolean).join(' · ') || '—'}</span>
              <span className="capacity">👥 {room.capacity} 人</span>
            </div>
            <div className="room-equipment">
              {room.equipment.length === 0 ? (
                <span className="muted">无登记设备</span>
              ) : (
                room.equipment.map((e) => (
                  <span key={e} className="equip-chip">
                    {EQUIP_LABEL[e] ?? e}
                  </span>
                ))
              )}
            </div>
          </button>
        );
      })}
    </div>
  );
}
