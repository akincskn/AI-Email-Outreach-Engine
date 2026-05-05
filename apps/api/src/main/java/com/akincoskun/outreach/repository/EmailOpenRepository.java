package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.EmailOpen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailOpenRepository extends JpaRepository<EmailOpen, UUID> {

    List<EmailOpen> findAllBySendId(UUID sendId);

    boolean existsBySendId(UUID sendId);
}
