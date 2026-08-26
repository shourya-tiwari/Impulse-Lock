package com.impulselock.impulselock.mapper;

import com.impulselock.impulselock.dto.AuditLogResponseDto;
import com.impulselock.impulselock.entity.AuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    @Mapping(target = "actorUsername",
            expression = "java(auditLog.getActor() != null ? auditLog.getActor().getUsername() : null)")
    AuditLogResponseDto toResponse(AuditLog auditLog);
}
