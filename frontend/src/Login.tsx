import { FormEvent, useState } from 'react';
import { login } from './api';
import { setSession, UserInfo } from './auth';

interface Props {
  onLoggedIn: (user: UserInfo) => void;
}

export default function Login({ onLoggedIn }: Props) {
  const [username, setUsername] = useState('alice');
  const [password, setPassword] = useState('password123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const { token, user } = await login(username, password);
      setSession(token, user);
      onLoggedIn(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="container login-box">
      <h1>登录</h1>
      <p className="subtitle">会议室预订系统 v1.0</p>

      <form onSubmit={onSubmit}>
        <div className="field">
          <label>用户名</label>
          <input
            type="text"
            value={username}
            autoComplete="username"
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label>密码</label>
          <input
            type="password"
            value={password}
            autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <button type="submit" disabled={loading}>
          {loading ? '登录中...' : '登录'}
        </button>
      </form>

      {error && (
        <div className="alert alert-error">
          <strong>登录失败</strong>
          <pre>{error}</pre>
        </div>
      )}

      <div className="hint">
        <p>演示账号（密码统一 <code>password123</code>）</p>
        <ul>
          <li>alice / bob — 普通员工</li>
          <li>carol — 助理（可代订 alice/bob）</li>
          <li>frank — 管理员</li>
          <li>david · eve · grace · helen — 主管/审批人/前台/审计</li>
        </ul>
      </div>
    </div>
  );
}
