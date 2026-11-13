import { useState, FormEvent } from 'react';
import { createBooking, BookingDetail, Room } from './api';
import { UserInfo } from './auth';
import RoomList from './RoomList';

interface Props {
  user: UserInfo;
}

/** 默认值：明天上午 09:00 - 10:00，对齐 30 分钟槽位。 */
function defaultTimes() {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(9, 0, 0, 0);
  const end = new Date(tomorrow.getTime() + 60 * 60 * 1000);
  return { start: toLocalInput(tomorrow), end: toLocalInput(end) };
}

function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export default function BookingPage({ user }: Props) {
  const { start, end } = defaultTimes();
  const [room, setRoom] = useState<Room | null>(null);
  const [subject, setSubject] = useState('部门周会');
  const [startTime, setStartTime] = useState(start);
  const [endTime, setEndTime] = useState(end);
  const [attendeeCount, setAttendeeCount] = useState(5);
  const [visibility, setVisibility] = useState<'PUBLIC' | 'PRIVATE'>('PUBLIC');
  const [principalIdStr, setPrincipalIdStr] = useState('');

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<BookingDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isAssistant = user.role === 'ASSISTANT';
  const isAdmin = user.role === 'ADMIN';

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!room) {
      setError('请先选择会议室');
      return;
    }
    setLoading(true);
    setResult(null);
    setError(null);
    try {
      const principalId = principalIdStr.trim() ? Number(principalIdStr) : undefined;
      const data = await createBooking({
        roomId: room.id,
        principalId,
        subject,
        startTime: `${startTime}:00`,
        endTime: `${endTime}:00`,
        attendeeCount,
        visibility,
      });
      setResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="container">
      <h1>会议室预订</h1>
      <p className="subtitle">v1.0 · 时间需对齐 30 分钟整点</p>

      <form onSubmit={onSubmit}>
        <div className="field">
          <label>选择会议室</label>
          <RoomList selectedId={room?.id ?? null} onSelect={setRoom} />
        </div>

        <div className="field">
          <label>会议主题</label>
          <input
            type="text"
            maxLength={255}
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            required
          />
        </div>

        {(isAssistant || isAdmin) && (
          <div className="field">
            <label>
              代订人 ID（可选）
              <span className="muted">
                {isAssistant ? '（仅可代订已授权用户）' : '（管理员可代订任意用户）'}
              </span>
            </label>
            <input
              type="number"
              min={1}
              value={principalIdStr}
              onChange={(e) => setPrincipalIdStr(e.target.value)}
              placeholder="留空表示自订"
            />
          </div>
        )}

        <div className="row">
          <div className="field">
            <label>开始时间</label>
            <input
              type="datetime-local"
              step={1800}
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label>结束时间</label>
            <input
              type="datetime-local"
              step={1800}
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
              required
            />
          </div>
        </div>

        <div className="row">
          <div className="field">
            <label>参会人数</label>
            <input
              type="number"
              min={1}
              value={attendeeCount}
              onChange={(e) => setAttendeeCount(Number(e.target.value))}
              required
            />
          </div>
          <div className="field">
            <label>可见性</label>
            <select
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'PRIVATE')}
            >
              <option value="PUBLIC">全员可见</option>
              <option value="PRIVATE">仅自己可见</option>
            </select>
          </div>
        </div>

        <button type="submit" disabled={loading}>
          {loading ? '提交中...' : '创建预订'}
        </button>
      </form>

      {result && (
        <div className="alert alert-success">
          <strong>预订成功 · {result.status}</strong>
          {result.needApproval && <span>（需要审批）</span>}
          <pre>{JSON.stringify(result, null, 2)}</pre>
        </div>
      )}

      {error && (
        <div className="alert alert-error">
          <strong>失败</strong>
          <pre>{error}</pre>
        </div>
      )}
    </div>
  );
}
