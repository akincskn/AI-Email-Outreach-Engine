package com.akincoskun.outreach.mapper;

import com.akincoskun.outreach.domain.SuppressionEntry;
import com.akincoskun.outreach.dto.SuppressionEntryResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SuppressionMapper {

    SuppressionEntryResponse toResponse(SuppressionEntry e);

    List<SuppressionEntryResponse> toResponseList(List<SuppressionEntry> entries);
}
