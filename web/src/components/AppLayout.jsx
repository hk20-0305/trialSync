import { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const SIDEBAR_KEY = 'trialsync_sidebar_collapsed';
const THEME_KEY = 'trialsync_theme';

const navItems = [
    { to: '/', label: 'Overview', glyph: 'W', end: true },
    { to: '/patients', label: 'Patients', glyph: 'P' },
    { to: '/trials', label: 'Trials', glyph: 'T' },
    { to: '/screenings', label: 'Screenings', glyph: 'S' },
    { to: '/help', label: 'Help', glyph: '?' },
];

function initialSidebarState() {
    try {
        return localStorage.getItem(SIDEBAR_KEY) === 'true';
    }
    catch {
        return false;
    }
}

function initialThemeState() {
    try {
        const saved = localStorage.getItem(THEME_KEY);
        if (saved === 'dark' || saved === 'light') return saved;
        if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark';
    }
    catch {
        // fallback
    }
    return 'light';
}

function initials(name) {
    return (name ?? 'TrialSync').split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
}

export function AppLayout() {
    const { user, logout } = useAuth();
    const [collapsed, setCollapsed] = useState(initialSidebarState);
    const [theme, setTheme] = useState(initialThemeState);
    const [sliderOpen, setSliderOpen] = useState(false);

    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        try {
            localStorage.setItem(THEME_KEY, theme);
        }
        catch { /* optional theme preference */ }
    }, [theme]);

    useEffect(() => {
        if (!sliderOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') setSliderOpen(false);
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [sliderOpen]);

    const toggleSidebar = () => setCollapsed((current) => {
        const next = !current;
        try {
            localStorage.setItem(SIDEBAR_KEY, String(next));
        }
        catch { /* optional preference */ }
        return next;
    });

    return (<div className={`app-shell${collapsed ? ' sidebar-collapsed' : ''}`}>
      <header className="topbar">
        <NavLink className="brand" to="/" aria-label="TrialSync workspace">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          <span><strong>TrialSync</strong><small>Trial workspace</small></span>
        </NavLink>
        <button
          className="account-trigger"
          type="button"
          onClick={() => setSliderOpen(true)}
          aria-expanded={sliderOpen}
          aria-controls="user-slider-drawer"
          aria-label={`${user?.display_name ?? 'User'} account menu`}
        >
          <span className="account-name">{user?.display_name}</span>
          <span className="account-avatar" aria-hidden="true">
            {initials(user?.display_name)}
          </span>
        </button>
      </header>

      <div className="shell-grid">
        <aside className="sidebar" aria-label="Primary navigation">
          <div className="sidebar-main">
            <button className="sidebar-toggle" type="button" onClick={toggleSidebar} aria-expanded={!collapsed} aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'} title={collapsed ? 'Expand navigation' : 'Collapse navigation'}>
              <span className="menu-icon" aria-hidden="true"><i /><i /><i /></span>
            </button>
            <nav>
              {[...navItems, ...(user?.is_catalog_admin ? [{ to: '/catalog', label: 'Catalog', glyph: 'C', end: undefined }] : [])].map((item) => (<NavLink key={item.to} to={item.to} end={item.end} aria-label={item.label} title={collapsed ? item.label : undefined} className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
                  <span className="nav-glyph" aria-hidden="true">{item.glyph}</span>
                  <span className="nav-label">{item.label}</span>
                </NavLink>))}
            </nav>
          </div>
        </aside>

        <main className="page" id="main-content"><Outlet /></main>
      </div>

      {/* User Preferences Slider Drawer & Backdrop */}
      <div
        className={`user-slider-backdrop${sliderOpen ? ' open' : ''}`}
        onClick={() => setSliderOpen(false)}
        aria-hidden="true"
      />
      <aside
        id="user-slider-drawer"
        className={`user-slider${sliderOpen ? ' open' : ''}`}
        aria-label="Account preferences"
        aria-hidden={!sliderOpen}
      >
        <div className="user-slider-header">
          <div className="user-slider-profile">
            <span className="user-slider-avatar" aria-hidden="true">
              {initials(user?.display_name)}
            </span>
            <div className="user-slider-info">
              <strong>{user?.display_name ?? 'Coordinator'}</strong>
              <small>{user?.is_catalog_admin ? 'Catalog Administrator' : 'Clinical Research Coordinator'}</small>
            </div>
          </div>
          <button
            type="button"
            className="user-slider-close"
            onClick={() => setSliderOpen(false)}
            aria-label="Close menu"
          >
            ✕
          </button>
        </div>

        <div className="user-slider-body">
          <div className="user-slider-section">
            <span className="user-slider-section-title">Theme</span>
            <div className="theme-toggle-group" role="group" aria-label="Theme options">
              <button
                type="button"
                className={`theme-toggle-btn${theme === 'light' ? ' active' : ''}`}
                onClick={() => setTheme('light')}
                aria-pressed={theme === 'light'}
              >
                <span className="theme-icon" aria-hidden="true">☀️</span>
                <span>Light</span>
              </button>
              <button
                type="button"
                className={`theme-toggle-btn${theme === 'dark' ? ' active' : ''}`}
                onClick={() => setTheme('dark')}
                aria-pressed={theme === 'dark'}
              >
                <span className="theme-icon" aria-hidden="true">🌙</span>
                <span>Dark</span>
              </button>
            </div>
          </div>
        </div>

        <div className="user-slider-footer">
          <button
            type="button"
            className="slider-signout-btn"
            onClick={() => {
              setSliderOpen(false);
              logout();
            }}
          >
            <span className="nav-glyph" aria-hidden="true">↗</span>
            <span>Sign out</span>
          </button>
        </div>
      </aside>
    </div>);
}
