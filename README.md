# AI Email Outreach Engine

Automated B2B email outreach system that discovers companies, analyzes their websites with AI, and sends personalized introduction emails about Akın's portfolio of free tools.

**Stack:** N8N · Spring Boot 3 / Java 21 · Next.js 14 · Neon PostgreSQL · Groq Llama 3.3 70B

## Project Structure

```
apps/
  api/        Spring Boot backend (agents, SMTP, tracking)
  web/        Next.js dashboard (approval UI)
workflows/    N8N workflow JSON exports
docs/         Architecture, database schema, prompts, task checklist
```

## Local Development

**Prerequisites:** Docker, Java 21, Node 20+, pnpm

```bash
# 1. Start infrastructure (Postgres + MailHog + N8N)
docker compose up -d

# 2. Copy env file and fill in values
cp .env.example .env.local

# 3. Start backend
cd apps/api
./mvnw spring-boot:run

# 4. Start frontend
cd apps/web
pnpm install && pnpm dev
```

Local services:
- API: http://localhost:8080
- Dashboard: http://localhost:3000
- N8N: http://localhost:5678 (admin / admin)
- MailHog: http://localhost:8025

## Documentation

| Doc | Description |
|-----|-------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture and service design |
| [docs/DATABASE.md](docs/DATABASE.md) | PostgreSQL schema and migration plan |
| [docs/PROMPTS.md](docs/PROMPTS.md) | AI agent prompts (Analyzer + Writer) |
| [docs/CHECKLIST.md](docs/CHECKLIST.md) | Ordered task list (Faz 1) |
| [CLAUDE.md](CLAUDE.md) | Project context for Claude Code sessions |
