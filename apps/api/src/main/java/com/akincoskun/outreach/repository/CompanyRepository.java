package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByDomain(String domain);

    Optional<Company> findByDomain(String domain);

    Page<Company> findAllByStatus(CompanyStatus status, Pageable pageable);

    List<Company> findAllByStatus(CompanyStatus status);

    Page<Company> findAllByCountryCode(String countryCode, Pageable pageable);
}
