package com.akincoskun.outreach.service.discovery;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.domain.EmailAccount;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.akincoskun.outreach.repository.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailExtractionService {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    private static final Set<String> GENERIC_PREFIXES = Set.of(
        "info", "contact", "hello", "hi", "office",
        "kariyer", "career", "careers", "jobs", "recruitment",
        "hr", "ik", "humanresources",
        "sales", "business", "partnerships",
        "support", "destek", "help",
        "bilgi", "iletisim", "reception",
        "admin", "team"
    );

    // Patterns that suggest a personal email (firstname.lastname style)
    private static final Pattern PERSONAL_PATTERN =
        Pattern.compile("^[a-z]+[._-][a-z]+@", Pattern.CASE_INSENSITIVE);

    /**
     * Placeholder/reserved domains that must never become recipients (Görev 12).
     * Scrapers occasionally surface emails like {@code info@example.com} from a
     * site's boilerplate; sending there is pointless and harms deliverability.
     */
    private static final Set<String> RESERVED_DOMAINS = Set.of(
        "example.com", "example.org", "example.net",
        "test.com", "test.org", "domain.com",
        "localhost", "invalid"
    );

    private final EmailAccountRepository emailAccountRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public List<EmailAccount> extractAndSave(Company company) {
        // Recipient guard: a reserved/placeholder company domain yields no valid
        // address — skip scraping entirely so the pipeline counts it as no-email.
        if (company.getDomain() != null && RESERVED_DOMAINS.contains(company.getDomain().toLowerCase())) {
            log.info("Company '{}': reserved domain, skipping email extraction", company.getDomain());
            company.setStatus(CompanyStatus.NEW);
            companyRepository.save(company);
            return List.of();
        }

        String baseUrl = resolveBaseUrl(company);
        List<String> urlsToScrape = List.of(
            baseUrl,
            baseUrl + "/contact",
            baseUrl + "/iletisim",
            baseUrl + "/about",
            baseUrl + "/contact-us"
        );

        Set<String> foundEmails = new LinkedHashSet<>();
        for (String url : urlsToScrape) {
            try {
                Document doc = Jsoup.connect(url)
                    .timeout(10_000)
                    .ignoreHttpErrors(true)
                    .get();
                extractEmailsFromText(doc.text(), foundEmails);
                extractEmailsFromLinks(doc, foundEmails);
            } catch (IOException e) {
                log.debug("Could not fetch {}: {}", url, e.getMessage());
            }
        }

        List<EmailAccount> saved = new ArrayList<>();
        for (String email : foundEmails) {
            String[] parts = email.split("@");
            String prefix = parts[0].toLowerCase();
            String domain = parts.length > 1 ? parts[1].toLowerCase() : "";
            if (RESERVED_DOMAINS.contains(domain)) continue;
            if (!GENERIC_PREFIXES.contains(prefix)) continue;
            if (PERSONAL_PATTERN.matcher(email).find()) continue;
            if (emailAccountRepository.existsByCompanyIdAndEmail(company.getId(), email)) continue;

            EmailAccount account = EmailAccount.builder()
                .company(company)
                .email(email.toLowerCase())
                .prefixType(prefix)
                .extractedFrom(baseUrl)
                .validFormat(true)
                .generic(true)
                .extractedAt(Instant.now())
                .build();
            saved.add(emailAccountRepository.save(account));
        }

        CompanyStatus next = saved.isEmpty() ? CompanyStatus.NEW : CompanyStatus.EMAILS_EXTRACTED;
        company.setStatus(next);
        companyRepository.save(company);

        log.info("Company '{}': extracted {} generic emails", company.getDomain(), saved.size());
        return saved;
    }

    private void extractEmailsFromText(String text, Set<String> out) {
        Matcher m = EMAIL_PATTERN.matcher(text);
        while (m.find()) out.add(m.group());
    }

    private void extractEmailsFromLinks(Document doc, Set<String> out) {
        doc.select("a[href^=mailto:]").forEach(el -> {
            String href = el.attr("href").replaceFirst("(?i)^mailto:", "").split("\\?")[0].trim();
            if (!href.isBlank()) out.add(href);
        });
    }

    private String resolveBaseUrl(Company company) {
        if (company.getWebsiteUrl() != null && !company.getWebsiteUrl().isBlank()) {
            return company.getWebsiteUrl().replaceAll("/+$", "");
        }
        return "https://" + company.getDomain();
    }
}
