package com.impulselock.impulselock.mapper;

import com.impulselock.impulselock.dto.TransactionResponseDto;
import com.impulselock.impulselock.entity.Transaction;
import org.mapstruct.Mapper;

/**
 * Field names match exactly between {@link Transaction} and {@link TransactionResponseDto}, so
 * no {@code @Mapping} overrides are needed - MapStruct maps them by name and silently skips the
 * entity's unmapped {@code id}/{@code user}/{@code createdAt} fields (no corresponding DTO field).
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionResponseDto toResponse(Transaction transaction);
}
