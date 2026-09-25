import { createContext, useContext, useEffect, useMemo, useState } from 'react';

const THEME_KEY = 'trialsync_theme';

function resolveInitialTheme() {
    try {
        const saved = localStorage.getItem(THEME_KEY);
        if (saved === 'dark' || saved === 'light') return saved;
        if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark';
    } catch {
        // localStorage unavailable — fall through
    }
    return 'light';
}

const ThemeContext = createContext(null);

export function ThemeProvider({ children }) {
    const [theme, setTheme] = useState(resolveInitialTheme);

    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        try {
            localStorage.setItem(THEME_KEY, theme);
        } catch {
            // optional preference
        }
    }, [theme]);

    const toggleTheme = () => {
        setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
    };

    const value = useMemo(() => ({ theme, setTheme, toggleTheme }), [theme]);
    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme() {
    const ctx = useContext(ThemeContext);
    if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
    return ctx;
}
