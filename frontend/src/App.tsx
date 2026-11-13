import { useEffect, useState } from 'react';
import { logout, UNAUTHORIZED_EVENT } from './api';
import { clearSession, getUser, UserInfo } from './auth';
import BookingPage from './BookingPage';
import Login from './Login';

export default function App() {
  const [user, setUser] = useState<UserInfo | null>(getUser());

  // 监听 axios 401：清状态 → 切登录页
  useEffect(() => {
    const handler = () => setUser(null);
    window.addEventListener(UNAUTHORIZED_EVENT, handler);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handler);
  }, []);

  async function onLogout() {
    try {
      await logout();
    } catch {
      // 即便后端请求失败，也要本地清干净
    }
    clearSession();
    setUser(null);
  }

  if (!user) {
    return <Login onLoggedIn={setUser} />;
  }

  return (
    <>
      <nav className="topbar">
        <div className="brand">会议室预订</div>
        <div className="user-area">
          <span className="role-tag">{user.roleLabel}</span>
          <span className="display-name">{user.displayName}</span>
          <button className="link-btn" onClick={onLogout}>登出</button>
        </div>
      </nav>
      <BookingPage user={user} />
    </>
  );
}
