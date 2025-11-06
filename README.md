# BookingSmart

Agentic travel booking platform that lets customers search, compare, and confirm flights and hotels through an AI-driven conversational interface. BookingSmart combines domain microservices, Spring Cloud Gateway backends, and Next.js frontends to provide a cohesive end-to-end travel experience for both storefront users and back-office agents.

## Highlights
- **Conversational booking** – `aiAgent-service` uses LangChain4j and model adapters to interpret natural-language intents, surface itineraries, and drive multi-step transactions.
- **Modular microservices** – dedicated services handle flights, hotels, bookings, payments, customers, notifications, and media assets, coordinated through Eureka discovery.
- **Dual BFF gateways** – `storefront-bff` and `backoffice-bff` expose unified APIs, handle OAuth2/Keycloak authentication, and broker calls to downstream services.
- **Next.js applications** – `storefront-fe` (customer) and `backoffice-fe` (operations) deliver modern UX backed by pnpm tooling and TailwindCSS.
- **Production-ready plumbing** – Nginx reverse proxy, Keycloak realms, Kafka bridges, and Postgres state all orchestrated through `docker-compose.local.yml`.

## Architecture Overview
```
Nginx (nginx.conf)
  ├── /api/*  ─► Storefront BFF (Spring Cloud Gateway)
  │               ├── Flight Service
  │               ├── Hotel Service
  │               ├── Booking Service
  │               ├── Payment Service
  │               ├── Customer Service
  │               ├── Media & Notification Services
  │               └── aiAgent-service (REST + WebSocket)
  └── /          └► Storefront Next.js UI

Backoffice BFF ⇄ Backoffice Next.js UI
Keycloak ⇄ Identity & OAuth2
Postgres, Kafka, Debezium connectors, and supporting infra
```
Service modules are managed from the root Maven `pom.xml`; shared code lives in `common-lib`. Reverse-proxy routing lives in `nginx.conf`, with service discovery driven by Eureka.

## Getting Started
### Prerequisites
- Docker Desktop 4.x+ and Compose plugin
- Java 21 and Maven wrapper (`./mvnw`)
- pnpm ≥ 9 (`corepack enable && corepack prepare pnpm@9 --activate`)

### Quick Start (All Services)
```bash
# Clone and enter the repo
git clone <this-repo>
cd bookingsmart

# Build JVM artifacts (optional but speeds up first run)
./mvnw clean install

# Launch complete stack: microservices, BFFs, Next.js apps, Keycloak, Postgres, Nginx
docker compose -f docker-compose.local.yml up --build
```
Navigate to `http://localhost:3000` for the storefront experience or `http://localhost:3001` for back-office (ports may differ if overridden). Nginx exposes friendly routes like `/flights`, `/hotels`, `/payments`, and `/ai` that map to individual services, while `/api/*` flows through the storefront BFF.

### Developing Services Locally
- Compile a single module (and dependencies) from the repo root:
  ```bash
  ./mvnw clean install -pl booking-service -am
  ```
- Run a service:
  ```bash
  ./mvnw spring-boot:run -pl flight-service
  ```
- Bring up supporting infra only:
  ```bash
  docker compose -f docker-compose.local.yml up postgres keycloak nginx discovery-service
  ```

### Frontend Development
```bash
cd backoffice-fe && pnpm install && pnpm dev      # port 3001
# or
cd storefront-fe && pnpm install && pnpm dev      # port 3000
```
When running UIs standalone, point their environment variables to the relevant BFF URLs (see `next.config.mjs` and `.env.example` files if provided). Use `pnpm build` for production builds and `pnpm lint` before committing.

## Conversational Agent
The AI agent (`aiAgent-service`) aggregates customer profile data and live availability by invoking domain services via the storefront/backoffice gateways. It offers REST endpoints (`/api/ai/*`) and WebSocket channels for streaming multi-turn dialogues. LangChain4j enables tool-augmented reasoning, with connectors to external LLM providers configurable through Keycloak-managed secrets. For local experimentation, provide API keys via the compose environment or your IDE run configuration—never commit secrets.

## Testing & Quality
- Backend: `./mvnw test` (or `./mvnw test -pl hotel-service`) using JUnit 5, Spring Boot Test, and Testcontainers for Postgres/Keycloak scenarios.
- Frontend: `pnpm lint` and add Playwright/Jest suites under `__tests__/` for new features.
- CI/CD recommendations include running both backend and frontend pipelines plus Docker image scans before deploying.

## Change Workflow
- Use the `type(scope): summary` commit style (`feat(aiAgent): add itinerary summarizer`).
- Document Keycloak or database changes alongside compose updates.
- Capture screenshots or screencasts for UI adjustments.
- Note automated test coverage or manual validation performed before merging.
