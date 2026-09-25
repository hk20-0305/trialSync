import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export function AuthPage({ mode }) {
    const { token, login, register } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [email, setEmail] = useState('');
    const [displayName, setDisplayName] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);

    if (token)
        return <Navigate to="/" replace/>;

    const submit = async (event) => {
        event.preventDefault();
        setBusy(true);
        setError('');
        try {
            if (mode === 'register')
                await register(email, displayName, password);
            else
                await login(email, password);
            const state = location.state;
            navigate(state?.from ?? '/', { replace: true });
        }
        catch (cause) {
            setError(cause instanceof ApiError ? cause.message : 'Unable to reach TrialSync.');
        }
        finally {
            setBusy(false);
        }
    };

    return (<main className="auth-page">
      <section className="auth-panel route-entry">
        <div className="auth-intro">
          <div className="auth-brand">
            <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
            <span className="auth-brand-title">TrialSync</span>
          </div>
          <p className="auth-brand-tagline">Clinical Trial Patient Matching</p>
        </div>
        <form className="auth-form" onSubmit={submit} autoComplete="on">
          <div className="auth-header">
            <h2>{mode === 'login' ? 'Sign in' : 'Create account'}</h2>
          </div>
          {error && <div className="form-error" role="alert">{error}</div>}
          {mode === 'register' && (<label>Display name<input name="display_name" required minLength={2} autoComplete="name" value={displayName} onChange={(event) => setDisplayName(event.target.value)}/></label>)}
          <label>Email<input name="email" required type="email" autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)}/></label>
          {mode === 'login' ? (<div className="auth-field">
              <label htmlFor="login-password">Password</label>
              <div className="password-field">
                <input id="login-password" name="password" required minLength={1} type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)}/>
                <button aria-controls="login-password" aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} className="password-toggle" type="button" onClick={() => setShowPassword((visible) => !visible)}>
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>) : (<label>Password<input name="password" required minLength={10} type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)}/></label>)}
          {mode === 'login' && (<button className="sample-data-button" type="button" onClick={() => {
                setEmail('demo@trialsync.example');
                setPassword('SyntheticDemo123!');
            }}>
              Use demo account
              <small>Fills synthetic demonstration credentials</small>
            </button>)}
          <button className="primary-button" disabled={busy} type="submit">{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
          <p className="auth-switch">
            {mode === 'login' ? "Don't have an account?" : 'Already registered?'}{' '}
            <Link to={mode === 'login' ? '/register' : '/login'}>{mode === 'login' ? 'Register' : 'Sign in'}</Link>
          </p>
        </form>
      </section>
    </main>);
}
