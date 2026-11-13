-- 补全/扩充会议室演示数据（在 seed.sql 之后执行）

-- 给已存在的 3 间补全 equipment + 楼栋信息（幂等：UPDATE 不会重复）
UPDATE rooms SET floor='F2', area='A 栋',
  equipment=JSON_ARRAY('projector','whiteboard')
  WHERE id=1;

UPDATE rooms SET floor='F3', area='A 栋',
  equipment=JSON_ARRAY('projector','whiteboard','tv')
  WHERE id=2;

UPDATE rooms SET floor='F1', area='B 栋',
  equipment=JSON_ARRAY('projector','video_conf','whiteboard','tv','phone')
  WHERE id=3;

-- 多插 3 间，覆盖不同设备组合和区域
INSERT INTO rooms (name, capacity, floor, area, equipment, is_vip, status) VALUES
  ('讨论间-101', 4,  'F1', 'A 栋',     JSON_ARRAY('whiteboard'),                                       0, 'AVAILABLE'),
  ('路演厅',     30, 'F2', 'B 栋',     JSON_ARRAY('projector','video_conf','tv'),                      0, 'AVAILABLE'),
  ('董事会议室', 20, 'F8', 'VIP 楼层', JSON_ARRAY('projector','video_conf','whiteboard','tv','phone'), 1, 'AVAILABLE');
