package com.akincoskun.outreach.mapper;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.dto.CompanyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    @Mapping(target = "status", expression = "java(c.getStatus().name())")
    CompanyResponse toResponse(Company c);

    List<CompanyResponse> toResponseList(List<Company> companies);
}
