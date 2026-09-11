# RoutePulse SEO / AEO Structure

Updated: 2026-09-11

## Goal

RoutePulse uses the same general search architecture as Renatus—clear page titles and descriptions, canonical URLs, social metadata, structured data, answer-first copy, FAQ content, internal links, robots.txt, and a sitemap—but the topic cluster is built around employee shuttle operations rather than web/software agency services.

## Indexable public pages

| Route | Primary intent |
| --- | --- |
| `/` | RoutePulse brand + employee shuttle operations software |
| `/employee-shuttle-management-software` | Core commercial category page |
| `/workforce-transportation-planning` | Schedule-to-demand planning |
| `/shuttle-driver-scheduling` | Driver coverage, fairness, overrides, scheduled vs. actual |
| `/shuttle-operations-verification` | QR/NFC sessions, live status, planned vs. actual verification |
| `/employee-shuttle-software-vs-spreadsheets` | Informational comparison / answer-engine page |

All pages are included in `/sitemap.xml` and linked through the public content cluster.

## Search metadata

The homepage and topic pages include:

- Unique `<title>` text
- Unique meta descriptions
- `index, follow` robots directives
- Canonical URLs on `https://routepulse.technology`
- Open Graph metadata
- X/Twitter card metadata
- A 1200×630 RoutePulse social image
- One clear H1 per page

## Structured data

The homepage establishes the core entities:

- `Organization` — Renatus as publisher
- `Brand` — RoutePulse
- `WebSite` — routepulse.technology
- `SoftwareApplication` — RoutePulse
- `WebPage` — homepage

Topic pages connect back to the same `SoftwareApplication` entity and add:

- `WebPage`
- `BreadcrumbList`
- `FAQPage` where the visible page contains matching FAQ content
- `Article` on the spreadsheet comparison guide

RoutePulse does not publish a fake price simply to satisfy SoftwareApplication rich-result requirements. The software markup is used to describe the product accurately; pricing/Offer markup should only be added after public pricing is real and stable.

## AEO content pattern

Each topic page follows an answer-first structure:

1. Specific search-intent H1
2. Short summary in the hero
3. A clearly labeled "Direct answer" section
4. Deeper operational explanation
5. Questions written in natural language
6. Short, explicit answers
7. Contextual links to related RoutePulse pages
8. Demo CTA after the answer content

The content deliberately explains both what RoutePulse does and what it does not do. For example, the verification page states that the current product is focused on operational verification rather than continuous GPS telematics.

## Index control

The following public utility pages are marked `noindex, nofollow` so they do not compete with marketing pages in search:

- Login
- Organization registration
- Password recovery/reset
- Verification recovery
- Invitation acceptance
- Rider status pages
- Error / blocked-organization pages

Authenticated manager and driver pages remain protected by Spring Security.

## Discovery files

- `/robots.txt` allows crawling and points to the sitemap.
- `/sitemap.xml` contains only canonical public marketing URLs.

## After production deployment

1. Confirm every sitemap URL returns HTTP 200 while signed out.
2. Confirm `https://routepulse.technology/robots.txt` and `/sitemap.xml` are publicly reachable.
3. Run the homepage and topic pages through Google's Rich Results Test / Schema Markup Validator.
4. Add the sitemap in Google Search Console.
5. Request indexing for the homepage and the core `/employee-shuttle-management-software` page first.
6. Let the supporting pages be discovered through the sitemap and internal links.
7. Review Search Console queries before adding more pages. New pages should answer genuinely different search intent rather than creating near-duplicate keyword pages.

## Future content opportunities

Only add these when there is enough real product/customer evidence to support them:

- Employee shuttle software pricing / cost guide
- Shuttle operations case study
- Dayforce schedule import guide
- Employee transportation operations checklist
- Shuttle driver fairness / workload guide
- Planned vs. actual transportation reporting guide
- Product integrations page
- Security / privacy / data-retention page

