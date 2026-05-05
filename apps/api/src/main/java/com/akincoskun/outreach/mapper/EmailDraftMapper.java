package com.akincoskun.outreach.mapper;

import com.akincoskun.outreach.domain.EmailDraft;
import com.akincoskun.outreach.dto.EmailDraftResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EmailDraftMapper {

    @Mapping(target = "companyId",      source = "company.id")
    @Mapping(target = "companyName",    source = "company.name")
    @Mapping(target = "companyDomain",  source = "company.domain")
    @Mapping(target = "emailAccountId", source = "emailAccount.id")
    @Mapping(target = "toEmail",        source = "emailAccount.email")
    @Mapping(target = "status",         expression = "java(d.getStatus().name())")
    EmailDraftResponse toResponse(EmailDraft d);

    List<EmailDraftResponse> toResponseList(List<EmailDraft> drafts);
}
