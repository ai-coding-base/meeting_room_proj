package com.company.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.booking.domain.entity.RoomEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议室数据访问。
 */
@Mapper
public interface RoomMapper extends BaseMapper<RoomEntity> {
}
