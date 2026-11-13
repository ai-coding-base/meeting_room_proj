package com.company.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.booking.domain.entity.IdempotencyKeyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 幂等键数据访问。
 */
@Mapper
public interface IdempotencyKeyMapper extends BaseMapper<IdempotencyKeyEntity> {
}
