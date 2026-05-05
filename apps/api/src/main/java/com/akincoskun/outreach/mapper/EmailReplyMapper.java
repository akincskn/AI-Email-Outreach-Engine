package com.akincoskun.outreach.mapper;

import com.akincoskun.outreach.domain.EmailReply;
import com.akincoskun.outreach.dto.EmailReplyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EmailReplyMapper {

    @Mapping(target = "sendId",      source = "send.id")
    @Mapping(target = "companyName", expression = "java(r.getSend() != null && r.getSend().getCompany() != null ? r.getSend().getCompany().getName() : null)")
    EmailReplyResponse toResponse(EmailReply r);

    List<EmailReplyResponse> toResponseList(List<EmailReply> replies);
}
