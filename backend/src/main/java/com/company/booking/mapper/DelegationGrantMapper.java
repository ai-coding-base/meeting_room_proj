package com.company.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.company.booking.domain.entity.DelegationGrantEntity;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

@Mapper
public interface DelegationGrantMapper extends BaseMapper<DelegationGrantEntity> {

    /**
     * 检查代订授权是否在有效期内。
     */
    default boolean existsValid(Long delegateId, Long principalId, LocalDate today) {
        Long count = selectCount(Wrappers.<DelegationGrantEntity>lambdaQuery()
                .eq(DelegationGrantEntity::getDelegateId, delegateId)
                .eq(DelegationGrantEntity::getPrincipalId, principalId)
                .and(w -> w.isNull(DelegationGrantEntity::getValidFrom)
                        .or().le(DelegationGrantEntity::getValidFrom, today))
                .and(w -> w.isNull(DelegationGrantEntity::getValidTo)
                        .or().ge(DelegationGrantEntity::getValidTo, today)));
        return count != null && count > 0;
    }
}
