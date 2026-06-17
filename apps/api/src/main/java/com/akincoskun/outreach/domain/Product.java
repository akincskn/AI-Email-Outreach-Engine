package com.akincoskun.outreach.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Akın's 7 demo products — the fixed catalog the Matcher Agent picks from and
 * the Writer Agent promotes. Hardcoded by design (see ARCHITECTURE.md trade-off
 * #3: the catalog rarely changes, so a DB table would be overkill).
 */
public enum Product {

    RIVALRADAR("rivalradar", "RivalRadar",
        "AI competitor analysis — paste a competitor URL, get an AI breakdown in seconds.",
        "https://rivalradar-three.vercel.app", false),

    AI_CHATBOT("ai-chatbot-platform", "AI Chatbot Platform",
        "24/7 FAQ chatbot built from your own documents (menus, policies, manuals).",
        "https://chatbot-web-peach.vercel.app", false),

    GEO_ANALYZER("geo-analyzer", "GEO Analyzer",
        "Scores how visible a business is inside AI search engines (ChatGPT, Perplexity).",
        "https://geo-analyzer-sepia.vercel.app", false),

    LEADPILOT("leadpilot", "LeadPilot",
        "AI SDR agent that finds leads and drafts personalized outreach emails.",
        "https://leadpilot-silk.vercel.app", false),

    KOLAYAIDAT("kolayaidat", "KolayAidat",
        "Apartment/site dues (aidat) tracking — replaces manual Excel, sends reminders, online payment.",
        "https://kolayaidat.vercel.app", true),

    FORMJET("formjet", "FormJet",
        "No-code form builder for collecting submissions without writing code.",
        "https://formjet.vercel.app", false),

    CEREZMATIK("cerezmatik", "Çerezmatik",
        "KVKK-compliant cookie consent banner generator for Turkish websites.",
        "https://cerezmatik.vercel.app", true);

    private final String slug;
    private final String displayName;
    private final String description;
    private final String url;
    /** Turkey-specific product (KVKK/Turkish market) — irrelevant for non-TR companies. */
    private final boolean turkeyOnly;

    Product(String slug, String displayName, String description, String url, boolean turkeyOnly) {
        this.slug = slug;
        this.displayName = displayName;
        this.description = description;
        this.url = url;
        this.turkeyOnly = turkeyOnly;
    }

    public String slug() { return slug; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String url() { return url; }
    public boolean turkeyOnly() { return turkeyOnly; }

    public static Optional<Product> fromSlug(String slug) {
        if (slug == null) return Optional.empty();
        String normalized = slug.strip().toLowerCase();
        return Arrays.stream(values())
            .filter(p -> p.slug.equals(normalized))
            .findFirst();
    }
}
