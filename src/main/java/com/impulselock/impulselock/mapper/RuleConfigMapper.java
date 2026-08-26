package com.impulselock.impulselock.mapper;

import com.impulselock.impulselock.dto.RuleConfigResponseDto;
import com.impulselock.impulselock.entity.RuleConfig;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RuleConfigMapper {

    RuleConfigResponseDto toResponse(RuleConfig ruleConfig);
}
