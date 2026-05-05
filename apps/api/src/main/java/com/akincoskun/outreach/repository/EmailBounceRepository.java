package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.EmailBounce;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailBounceRepository extends JpaRepository<EmailBounce, UUID> {

    List<EmailBounce> findAllBySendId(UUID sendId);

    long countBySendIdAndBounceType(UUID sendId, String bounceType);
}
