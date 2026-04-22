# Repository Guidelines

## Project Structure & Module Organization
- `src/app`: Next.js App Router layouts and screens; begin with `page.js` when tracing flows.
- `src/components`: Client-side building blocks exported in PascalCase (for example `AddRecordDialog.jsx`).
- `src/lib`: Shared helpers and API surface (`api.js` reads `NEXT_PUBLIC_API_URL`).
- `res`: JSON schema contracts backing agent workflows; update with backend changes.
- `public`: Static assets delivered as-is (icons, downloads, fonts).

## Build, Test, and Development Commands
- `npm run dev`: Turbopack dev server on `http://localhost:3000`.
- `npm run build`: Production bundle; run before release handoff.
- `npm run start`: Serves the built app for smoke checks.
- `npm run lint`: ESLint with `next/core-web-vitals`; must be clean pre-PR.

## Coding Style & Naming Conventions
- Prefer functional React components with explicit `"use client"` boundaries; components in PascalCase, helpers in camelCase.
- Keep 2-space indentation, trailing semicolons, and default to single quotes.
- Compose visuals with Tailwind v4 utilities from `globals.css`; extend theme tokens under `@layer theme`.
- Store multi-use logic in `src/lib` and colocate feature-specific hooks beside their component.

## Tech Guidelines
- tailwindcss v4 for styling and layout.
- shadcn for UI components and design consistency.
- Next.js App Router for routing and server components.
- Fetch API for data retrieval; centralize endpoints in `src/lib/api.js`.
- Olympus for authentication middleware; see [OlympusIntegration.md](OlympusIntegration.md) for details.

## Testing Guidelines
- Automated tests are not yet scaffolded; add React Testing Library + Vitest (or Jest) when introducing coverage.
- Name files `<Component>.test.jsx`, colocate under `src/components/__tests__`, and mirror API payloads with the JSON schemas in `res`.
- Document manual verification steps in PRs until automated suites exist.

## Commit & Pull Request Guidelines
- Use the existing sentence-case, action-oriented commit style (`Added execution stats in a new page`) and keep subject lines under 72 characters.
- Squash only when simplifying your branch; never rewrite shared history.
- PRs must outline impact, validation (`npm run lint`, manual flows), related issues, and attach UI evidence (screenshots or Looms) for visual tweaks.
- Call out environment or schema changes explicitly, especially when editing `src/lib/api.js` or `res/*.json`.

## Configuration & API Contracts
- Provide `NEXT_PUBLIC_API_URL` in `.env.local`; otherwise the client targets `http://localhost:1900`.
- When APIs evolve, adjust `src/lib/api.js` and synchronize the related `res/*-jsonschema.json` contract so agents and UI validations match.


# Low Level Details for Arnold UI

This is a code review website written in react, javascript, tailwindcss. The style is always minimal using
shadcn components. The code should be clean and modular.

Generate a minimal, responsive React (JSX, JavaScript) component using Tailwind that follows shadcn design language. Use
small composable components, expose className, and keep props simple. Use mobile-first classes and accessible markup.
Use ring/border/rounded/shadow-sm and color tokens (background, foreground, primary, secondary, muted, accent, ring,
input). No TypeScript, no external state libs. Keep code readable and modular like a software architect.

The code should be design with a proper app router pattern

## Agent global rules

- Use React (JavaScript + JSX). Do not use TypeScript.
- Technologies to be used
    - lucide-react for icons (optional)
    - Tailwind CSS 4 for styling
    - No external state management libraries (no Redux, MobX, Zustand, etc.). Use React's built-in state
      management (useState, useEffect) only when necessary.
    - shadcn design language for components
    - Create custom react components as needed, but keep them minimal and reusable.
- Keep code minimal, readable, and modular. Favor small composable components and simple props.
- Follow shadcn/ui design language:
    - Utility-first Tailwind classes.
    - Mobile-first, responsive by default (sm, md, lg breakpoints).
    - Use accessible markup and Radix-like patterns (aria-* as needed).
    - Use ring, border, rounded, shadow-sm, and color tokens consistent with shadcn (primary, background, foreground,
      etc.).
- Expose className prop on every UI component to allow composition.
- Prefer container layouts, grid/flex, and spacing via gap/space-y utilities.
- Do not add libraries beyond React, Tailwind, and lucide-react icons (optional).
- Avoid complex state management; use useState/useEffect only when needed.
- Avoid lorem ipsum; use clear, concise copy and sensible placeholders.

## Authentication

Authentication should happen in a single middleware, using olympus-node-client, whose details can be found in  [OlympusIntegration.md](OlympusIntegration.md)

### Component generator checklist (Copilot should follow this)

- File: one component per file, named with PascalCase (e.g., Button.jsx).
- Export named component.
- Props: children, className, variant, size where relevant; spread ...props onto the root element.
- Accessibility: role/aria-label where appropriate, focus-visible ring for focus states.
- Mobile-first: base styles optimized for mobile; add sm/md/lg utilities as needed.
- No inline styles unless necessary; rely on Tailwind classes.
- Minimal base components (shadcn-style, JS/JSX)

Button.jsx

```
import React from "react";

export function Button({
  variant = "default",
  size = "md",
  className = "",
  children,
  ...props
}) {
  const base =
    "inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium transition-colors " +
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 " +
    "disabled:pointer-events-none disabled:opacity-50 ring-offset-background";

  const sizes = {
    sm: "h-9 px-3",
    md: "h-10 px-4",
    lg: "h-11 px-6",
  };

  const variants = {
    default: "bg-primary text-primary-foreground hover:bg-primary/90",
    secondary: "bg-secondary text-secondary-foreground hover:bg-secondary/80",
    outline:
      "border border-input bg-background hover:bg-accent hover:text-accent-foreground",
    ghost: "hover:bg-accent hover:text-accent-foreground",
    link: "text-primary underline-offset-4 hover:underline",
  };

  return (
    <button
      className={`${base} ${sizes[size]} ${variants[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
```

Input.jsx

```
import React from "react";
export function Input({ className = "", ...props }) {
  return (
    <input
      className={
        "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm " +
        "placeholder:text-muted-foreground ring-offset-background " +
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 " +
        "disabled:cursor-not-allowed disabled:opacity-50 " +
        className
      }
      {...props}
    />
  );
}

```

Label.jsx

```
import React from "react";

export function Label({ className = "", children, ...props }) {
  return (
    <label
      className={`text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 ${className}`}
      {...props}
    >
      {children}
    </label>
  );
}
```

Card.jsx

```
import React from "react";

export function Card({ className = "", children, ...props }) {
  return (
    <div
      className={`rounded-lg border bg-card text-card-foreground shadow-sm ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}

export function CardHeader({ className = "", children }) {
  return <div className={`p-4 border-b ${className}`}>{children}</div>;
}

export function CardTitle({ className = "", children }) {
  return <h3 className={`text-lg font-semibold ${className}`}>{children}</h3>;
}

export function CardDescription({ className = "", children }) {
  return (
    <p className={`text-sm text-muted-foreground ${className}`}>{children}</p>
  );
}

export function CardContent({ className = "", children }) {
  return <div className={`p-4 ${className}`}>{children}</div>;
}

export function CardFooter({ className = "", children }) {
  return <div className={`p-4 border-t ${className}`}>{children}</div>;
}
```

Container.jsx (layout helper)

```
import React from "react";

export function Container({ className = "", children }) {
  return (
    <div className={`mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8 ${className}`}>
      {children}
    </div>
  );
}
```

Navbar.jsx (responsive, minimal)

```
import React from "react";
import { Button } from "../ui/Button";

export function Navbar() {
  const [open, setOpen] = React.useState(false);

  return (
    <header className="sticky top-0 z-40 w-full border-b bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 h-14 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-md bg-primary text-primary-foreground text-sm font-bold">
            UI
          </span>
          <span className="text-sm font-medium text-foreground">Shad-Style</span>
        </div>

        <nav className="hidden md:flex items-center gap-6 text-sm">
          <a className="text-muted-foreground hover:text-foreground" href="#">Docs</a>
          <a className="text-muted-foreground hover:text-foreground" href="#">Components</a>
          <a className="text-muted-foreground hover:text-foreground" href="#">Themes</a>
        </nav>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm">Sign in</Button>
          <Button size="sm">Get started</Button>

          <button
            aria-label="Open menu"
            className="md:hidden inline-flex h-9 w-9 items-center justify-center rounded-md border border-input bg-background text-foreground"
            onClick={() => setOpen(true)}
          >
            ☰
          </button>
        </div>
      </div>

      {open && (
        <div className="md:hidden border-t bg-background">
          <div className="px-4 py-3 space-y-2">
            <a className="block text-sm text-foreground" href="#">Docs</a>
            <a className="block text-sm text-foreground" href="#">Components</a>
            <a className="block text-sm text-foreground" href="#">Themes</a>
            <div className="flex gap-2 pt-2">
              <Button variant="outline" size="sm" className="w-full">Sign in</Button>
              <Button size="sm" className="w-full">Get started</Button>
            </div>
            <Button variant="ghost" size="sm" className="w-full" onClick={() => setOpen(false)}>
              Close
            </Button>
          </div>
        </div>
      )}
    </header>
  );
}
```

Hero.jsx (minimal landing section)

```
import React from "react";
import { Button } from "../ui/Button";
import { Container } from "../layout/Container";

export function Hero() {
  return (
    <section className="border-b bg-background">
      <Container className="py-16 sm:py-24">
        <div className="grid gap-8 md:grid-cols-2 md:items-center">
          <div className="space-y-4">
            <h1 className="text-3xl sm:text-4xl font-bold tracking-tight">
              Build minimal, responsive UIs
            </h1>
            <p className="text-muted-foreground">
              Composable components that follow the shadcn design language. Mobile-first and accessible by default.
            </p>
            <div className="flex flex-wrap gap-2">
              <Button>Get started</Button>
              <Button variant="outline">View components</Button>
            </div>
          </div>
          <div className="rounded-lg border bg-card p-4 shadow-sm">
            <div className="aspect-video rounded-md bg-muted" />
            <p className="mt-2 text-xs text-muted-foreground">Preview area</p>
          </div>
        </div>
      </Container>
    </section>
  );
}
```

Features.jsx (card grid)

```
import React from "react";
import { Card, CardHeader, CardTitle, CardContent } from "../ui/Card";
import { Container } from "../layout/Container";

const items = [
  { title: "Composable", desc: "Small building blocks you can reuse." },
  { title: "Accessible", desc: "Keyboard and screen-reader friendly." },
  { title: "Responsive", desc: "Mobile-first layouts by default." },
  { title: "Theming", desc: "Use Tailwind + CSS vars for themes." },
];

export function Features() {
  return (
    <section className="bg-background">
      <Container className="py-12 sm:py-16">
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {items.map((it) => (
            <Card key={it.title}>
              <CardHeader>
                <CardTitle>{it.title}</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">{it.desc}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      </Container>
    </section>
  );
}
```

Footer.jsx

```
import React from "react";
import { Container } from "../layout/Container";

export function Footer() {
  return (
    <footer className="border-t bg-background">
      <Container className="h-16 flex items-center justify-between">
        <p className="text-xs text-muted-foreground">© 2025 Minimal UI Co.</p>
        <div className="flex items-center gap-4 text-xs">
          <a href="#" className="text-muted-foreground hover:text-foreground">Privacy</a>
          <a href="#" className="text-muted-foreground hover:text-foreground">Terms</a>
          <a href="#" className="text-muted-foreground hover:text-foreground">Contact</a>
        </div>
      </Container>
    </footer>
  );
}
```

App.jsx (compose the page)

```
import React from "react";
import { Navbar } from "./components/layout/Navbar";
import { Hero } from "./components/sections/Hero";
import { Features } from "./components/sections/Features";
import { Footer } from "./components/layout/Footer";

export default function App() {
  return (
    <div className="min-h-dvh bg-background text-foreground">
      <Navbar />
      <main>
        <Hero />
        <Features />
      </main>
      <Footer />
    </div>
  );
}
```

### Suggested project structure

- src/components/ui/Button.jsx, Input.jsx, Label.jsx, Card.jsx
- src/components/layout/Container.jsx, Navbar.jsx, Footer.jsx
- src/components/sections/Hero.jsx, Features.jsx
- src/App.jsx, src/index.jsx

### Tailwind + theme notes (for shadcn-like tokens)

- Configure Tailwind to use CSS variables for colors (background, foreground, primary, secondary, muted, ring, input).
  In your CSS (e.g., globals.css), set defaults on :root and support dark mode on .dark.
- Keep spacing and radii close to shadcn defaults: rounded-md, rounded-lg; shadow-sm; border; ring-2 on focus-visible.
- Use max-w-7xl containers and px-4 sm:px-6 lg:px-8 for consistent layout.

Example CSS variables (minimal, add to your global stylesheet)

```
:root {
  --background: 255 255 255;
  --foreground: 17 24 39;
  --card: 255 255 255;
  --card-foreground: 17 24 39;
  --primary: 24 119 242;
  --primary-foreground: 255 255 255;
  --secondary: 243 244 246;
  --secondary-foreground: 17 24 39;
  --muted: 243 244 246;
  --muted-foreground: 107 114 128;
  --accent: 243 244 246;
  --accent-foreground: 17 24 39;
  --ring: 24 119 242;
  --input: 229 231 235;
}

.dark {
  --background: 9 9 11;
  --foreground: 229 231 235;
  --card: 17 17 19;
  --card-foreground: 229 231 235;
  --primary: 24 119 242;
  --primary-foreground: 255 255 255;
  --secondary: 31 31 35;
  --secondary-foreground: 229 231 235;
  --muted: 31 31 35;
  --muted-foreground: 160 160 170;
  --accent: 31 31 35;
  --accent-foreground: 229 231 235;
  --ring: 24 119 242;
  --input: 63 63 70;
}
```

Tailwind config hint (colors mapped to CSS vars)

In tailwind.config.js, map theme colors like:
background: hsl(var(--background) / <alpha-value>)
foreground: hsl(var(--foreground) / <alpha-value>)
primary, secondary, muted, accent, ring, input similarly
Enable darkMode: "class".



# Garage Backend

## Preface to the Code Modules

This repository contains multiple modules, each serving a specific purpose in the overall Garage system. Below is a
brief overview of each module:

- `garage-server`: This java module contains the backend server code for Garage. It just wires up the dropwizard
  application but does not include the core logic. It just includes the core-bundle.
- `garage-models`: This java module defines the data models used across the Garage system. It includes classes for
  records, tables, and field values.
- `garage-core`: This java module is the heart of the backend service, managing data storage, retrieval, and core
  functionalities, etc
- `garage-ui`: This react module contains the frontend code for Garage. It provides the user interface for interacting
  with the Garage system, including dashboards and data visualization tools. It makes appropriate API calls to the
  backend service and renders the data accordingly.
- `garage-tools`: This python module contains various scripts and tools to interact with the Garage system. Primarily to
  test the system locally

## High level Details

Garage is a system that is used for playing around with structured data, and then visualizing it. Think of this as an
alternative to business folks trying to maintain data in excel sheets, and then trying to visualize it.
It comes with a UI builder, to build dashboards to visualize the data, save it, and share it with others.

## Definitions:

##### 1. GarageRecord:

The atomic data entity, the represents a single data element

- `recordId` : An ID that uniquely identifies
- `tableId` : The table it belongs to
- Map<String, FieldValue> - Attributes of the record

##### 2. FieldValue:

Represents an attribute of a record.

- `name` : Name of the attribute
- `type` : Type of the attribute (String, Number, Boolean, DateTime, Enum)
- values are sub-typed and will have different fields based on the type

##### 3. Table (TableDef):

The equivalent of a table. A table is a set of records that share the same schema.

- `tableId` or `id` : An ID that uniquely identifies the table
- `name` : Name of the table
- `description` : Description of the table
- `repositoryDefs` : List of repositories enabled, where the table is stored
- `fieldDefs` : List of fields that define the schema of the table
- `workflowDefs` : List of workflows that run on the table
- `timeToLive` : Time to live for the records in the table

##### 4. FieldDef:

Defines a field in the schema of the table

- `name` : Name of the field
- `type` : Type of the field (all types in the enum FieldType). The classes are subtyped on this value
- `primaryKey` : Whether the field is a primary key or not
- `fieldValidator` : Validator for the field (optional)

##### 5. RepositoryDef:

Defines a repository where the table is stored.
We currently support 2 types of repositories.

1. MutableDataRepositoryDef - which is on top of HBASE, and is used as raw storage of data with an index on time for
   retrieval
2. AnalyticsRepositoryDef - which is on top of Elasticsearch, and is used for analytics, aggregation and search usecases

- `type` : Type of the repository (MUTABLE_DATA_STORE,ANALYTICS_STORE)

##### 6. Collection:

A collection is a set of tables that are related to each other. It is tenanted.

# The (expected) WORKFLOW on the UI:

Let's take an example of someone trying to maintain Tasks at a bunch of grocery stores managed by a company.
He needs a dashboard to track all tasks across all stores.

- a view showing all tasks, their status, task assignment, and other details, in an excel like interface
- he should be able to use this interface, to edit and manage every row of data.
- he needs another view, where he can see aggregations over this data. Like, number of pending, completed tasks, time
  series of tasks completed per hour, etc

His workflow could be as follows:

1. Identifying the schema of table for his usecase.
   In this particular case:

   Table 1: Store
    - Store Id (String)
    - Store Name (String)
    - Stage (ENUM)
    - Store Type (ENUM)
    - Created At (DateTime)
    - Updated At (DateTime)

   Table 2: Task
    - Task Id (String)
    - Task Name (String)
    - Task Description (String)
    - Assigned To (String)
    - Status (ENUM)
    - Priority (ENUM)
    - Store Id (String) - Foreign key to Store table
    - Created At (DateTime)
    - Updated At (DateTime)
2. Create a new collection
3. Create the set of tables in Garage using the following API
4. Create a dashboard
5. Create a new page/tab in the dashboard for creating different views
6. Click on the plus button to create a component/widget on the page
7. He can also use AI to generate the component, by describing what he wants
8. Save the page, and the dashboard.

# Local debugging

### For running the server on command line:

```bash
cd garage-server && sh run.sh d
```

You can then connect remotely from intellij remotely, by setting up a remote configuration (Remote JVM debug), and
connecting to the default 5005 port

### For running the server on IntelliJ:

You can run the service from Intellij Idea as well by setting the following in edit configurations

**Prerequisite**

When running service locally, local.yml config would be fetched.  
Pre requisite make sure you have zookeeper running, as the local.yml points to localhost:2181  
Alternatively you can also point to stage zookeeper, just make sure you don't block others.

Select type Application and choose the Main Application class from the server-module

1) *VM args*  
   `-DlocalConfig=true -Dcom.sun.security.enableAIAcaIssuers=true`

2) *Program args*  
   `server garage-server/config/local.yml`

3) *Environment variables*  
   `ZK_CONNECTION_STRING=internal.example:2181;HOST=config.internal.example;PORT_8080=443;ROSEY_PATH=garage-server/config/rosey.yml`

Voila! Visit `localhost:1200/swagger/#` to check the housekeeping api.
