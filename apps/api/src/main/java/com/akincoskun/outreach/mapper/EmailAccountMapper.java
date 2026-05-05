package com.akincoskun.outreach.mapper;

import com.akincoskun.outreach.domain.EmailAccount;
import com.akincoskun.outreach.dto.EmailAccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EmailAccountMapper {

    @Mapping(target = "companyId", source = "company.id")
    EmailAccountResponse toResponse(EmailAccount a);

    List<EmailAccountResponse> toResponseList(List<EmailAccount> accounts);
}
