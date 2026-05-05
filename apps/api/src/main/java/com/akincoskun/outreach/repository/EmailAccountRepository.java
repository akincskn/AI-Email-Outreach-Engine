package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, UUID> {

    List<EmailAccount> findAllByCompanyId(UUID companyId);

    boolean existsByCompanyIdAndEmail(UUID companyId, String email);

    Optional<EmailAccount> findByEmail(String email);
}
