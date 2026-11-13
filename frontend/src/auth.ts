/**
 * 登录态管理（localStorage）。
 * v1.0 简化方案：token + user 一起存；登出 = 清两项 + 跳 /login（条件渲染）。
 */

const TOKEN_KEY = 'mr_token';
const USER_KEY = 'mr_user';

export interface UserInfo {
  id: number;
  username: string;
  displayName: string;
  /** EMPLOYEE / ASSISTANT / MANAGER / APPROVER / ADMIN / RECEPTIONIST / AUDITOR */
  role: string;
  roleLabel: string;
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    return null;
  }
}

export function setSession(token: string, user: UserInfo): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function isLoggedIn(): boolean {
  return getToken() !== null;
}
