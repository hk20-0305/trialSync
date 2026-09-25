import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../app/ThemeContext';

const SIDEBAR_KEY = 'trialsync_sidebar_collapsed';

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

function initials(name) {
    return (name ?? 'TrialSync').split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
}

export function AppLayout() {
    const { user, logout } = useAuth();
    const { theme, setTheme } = useTheme();
    const [collapsed, setCollapsed] = useState(initialSidebarState);
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef(null);
    const navigate = useNavigate();

    useEffect(() => {
        if (!dropdownOpen) return;
        const handleClickOutside = (e) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
                setDropdownOpen(false);
            }
        };
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') setDropdownOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [dropdownOpen]);

    const toggleSidebar = () => setCollapsed((current) => {
        const next = !current;
        try {
            localStorage.setItem(SIDEBAR_KEY, String(next));
        }
        catch { /* optional preference */ }
        return next;
    });

    const handleSignOut = () => {
        setDropdownOpen(false);
        logout();
        navigate('/login');
    };

    return (<div className={`app-shell${collapsed ? ' sidebar-collapsed' : ''}`}>
      <header className="topbar">
        <NavLink className="brand" to="/" aria-label="TrialSync workspace">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          <span><strong>TrialSync</strong><small>Trial workspace</small></span>
        </NavLink>
        <div className="account-area">
          <button
            className="theme-toggle"
            type="button"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            <span aria-hidden="true">{theme === 'dark' ? '☀️' : '🌙'}</span>
          </button>
          <div className="account-menu" ref={dropdownRef}>
            <button
              className="account-trigger"
              type="button"
              onClick={() => setDropdownOpen((open) => !open)}
              aria-expanded={dropdownOpen}
              aria-haspopup="menu"
              aria-label={`${user?.display_name ?? 'User'} account menu`}
            >
              <span className="account-avatar" aria-hidden="true">
                {initials(user?.display_name)}
              </span>
              <span className="account-name">{user?.display_name}</span>
              <span className="account-arrow" aria-hidden="true">{dropdownOpen ? '▴' : '▾'}</span>
            </button>
            {dropdownOpen && (
              <div className="account-popover" role="menu">
                <div className="account-identity">
                  <strong>{user?.display_name ?? 'User'}</strong>
                  <span className="account-email">{user?.email ?? 'demo@trialsync.example'}</span>
                </div>
                <button
                  type="button"
                  className="dropdown-signout-btn"
                  role="menuitem"
                  onClick={handleSignOut}
                >
                  Sign out
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      <div className="shell-grid">
        <aside className="sidebar" aria-label="Primary navigation">
          <div className="sidebar-main">
            <button
              className="sidebar-collapse-button"
              type="button"
              onClick={toggleSidebar}
              aria-expanded={!collapsed}
              aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'}
              title={collapsed ? 'Expand navigation' : 'Collapse navigation'}
            >
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
    </div>);
}
