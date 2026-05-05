package com.akincoskun.outreach.mapper;

import com.akincoskun.outreach.domain.DiscoveryFilter;
import com.akincoskun.outreach.dto.DiscoveryFilterRequest;
import com.akincoskun.outreach.dto.DiscoveryFilterResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DiscoveryFilterMapper {

    DiscoveryFilterResponse toResponse(DiscoveryFilter f);

    List<DiscoveryFilterResponse> toResponseList(List<DiscoveryFilter> filters);

    DiscoveryFilter toEntity(DiscoveryFilterRequest req);

    void updateEntity(DiscoveryFilterRequest req, @MappingTarget DiscoveryFilter target);
}
