-- =====================================================================
-- 会议室预订系统 v1.0 · 种子数据
-- 演示账号：8 个，覆盖所有角色，密码统一为 password123
-- 注意：BCrypt 哈希由 backend 测试 PasswordHashGenerator 生成，
--      每次运行哈希值不同（盐随机），但都能验证同一明文。
-- =====================================================================

-- ---------- 用户（8 个角色）----------
INSERT INTO `users` (`username`, `display_name`, `email`, `password_hash`, `role`, `is_active`) VALUES
  ('alice',  'Alice',   'alice@example.com',   '$2a$10$7.jCTsDn0sK5yl8WF78Me.3EYjNZl474i93WukgMmTOJQiZCIcRyi', 'EMPLOYEE',     1),
  ('bob',    'Bob',     'bob@example.com',     '$2a$10$TbibkhChUnQJZfXjTdReseeGQ4B0DAQyF1Egu07J31Lmt5qN3FlyC', 'EMPLOYEE',     1),
  ('carol',  'Carol',   'carol@example.com',   '$2a$10$l52NCfWbYFEw/QClUDIZHuIWN9BuQR9jLjsEDdvVA/MRr5/LzmnYK', 'ASSISTANT',    1),
  ('david',  'David',   'david@example.com',   '$2a$10$KO3GHmBdHfoB54xpTCnvtuRxN2QdjJN9syvb80Wlsa9v..x8WVReG', 'MANAGER',      1),
  ('eve',    'Eve',     'eve@example.com',     '$2a$10$E9s4utS6dWKcjMr.cZFRr.somc.s1GfkUsAgziAdTmwAmhWh8fZ.W', 'APPROVER',     1),
  ('frank',  'Frank',   'frank@example.com',   '$2a$10$4okWZeRqU19z3vuLxb6f2.JImztlETQjV3VotKCuaWd2FIpPZYO02', 'ADMIN',        1),
  ('grace',  'Grace',   'grace@example.com',   '$2a$10$cT1J3HWtE0SmTDjcqxes1ev7p1gwhj9XRBK8pl0/Uns936SyYRMb.', 'RECEPTIONIST', 1),
  ('helen',  'Helen',   'helen@example.com',   '$2a$10$TPKTiWOc1FSc.US4AE5OKewYehCPBOBuRK9NUw4BNYVts586DN1Q.', 'AUDITOR',      1);

-- ---------- 代订授权：carol 可代订 alice 和 bob ----------
INSERT INTO `delegation_grants` (`delegate_id`, `principal_id`, `valid_from`, `valid_to`)
SELECT c.id, a.id, NULL, NULL FROM users c, users a WHERE c.username='carol' AND a.username='alice';
INSERT INTO `delegation_grants` (`delegate_id`, `principal_id`, `valid_from`, `valid_to`)
SELECT c.id, b.id, NULL, NULL FROM users c, users b WHERE c.username='carol' AND b.username='bob';

-- ---------- 会议室（演示用 2 个）----------
INSERT INTO `rooms` (`name`, `capacity`, `floor`, `area`, `is_vip`, `status`) VALUES
  ('小会议室-A', 10, 'F3', '北区', 0, 'AVAILABLE'),
  ('大会议室-B', 60, 'F1', '中庭', 0, 'AVAILABLE');
