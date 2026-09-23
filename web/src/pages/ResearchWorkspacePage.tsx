/**
 * ResearchWorkspacePage — deployed-style research workspace shell.
 *
 * Renders at /research with a `research-subnav` matching the deployed site:
 *   Dropout  → /research/dropout
 *   Cohorts  → /research/cohort
 *
 * Sub-routes are rendered via <Outlet />. The /research index redirects
 * to /research/dropout via the router configuration.
 */

import { NavLink, Outlet } from 'react-router-dom'

export function ResearchWorkspacePage() {
  return (
    <section className="route-entry research-workspace-page" aria-label="Research workspace">
      {/* Deployed-style subnav — NavLinks with active class */}
      <nav className="research-subnav" aria-label="Research sections">
        <NavLink
          id="research-nav-dropout"
          to="/research/dropout"
          className={({ isActive }) => (isActive ? 'active' : '')}
        >
          Dropout
        </NavLink>
        <NavLink
          id="research-nav-cohorts"
          to="/research/cohort"
          className={({ isActive }) => (isActive ? 'active' : '')}
        >
          Cohorts
        </NavLink>
        <NavLink
          id="research-nav-rag"
          to="/research/rag"
          className={({ isActive }) => (isActive ? 'active' : '')}
        >
          Criteria Search
        </NavLink>
      </nav>

      {/* Sub-route content */}
      <Outlet />
    </section>
  )
}
