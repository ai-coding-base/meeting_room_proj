package com.company.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.booking.domain.entity.BookingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预订数据访问。
 */
@Mapper
public interface BookingMapper extends BaseMapper<BookingEntity> {
}
