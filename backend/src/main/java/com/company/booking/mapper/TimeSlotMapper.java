package com.company.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.booking.domain.entity.TimeSlotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 时间槽数据访问。
 * <p>
 * insertBatch 是防超卖的关键：依赖 uk_room_slot 唯一约束，冲突时抛 DuplicateKeyException。
 */
@Mapper
public interface TimeSlotMapper extends BaseMapper<TimeSlotEntity> {

    /**
     * 批量插入时间槽（一次预订可能跨多个 30 分钟槽位）。
     * <p>
     * 任一槽位与已存在记录冲突时整个事务回滚。
     *
     * @param slots 槽位列表
     * @return 实际插入行数
     */
    int insertBatch(@Param("slots") List<TimeSlotEntity> slots);
}
