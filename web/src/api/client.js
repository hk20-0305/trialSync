import { getApiBaseUrl } from './config';
export class ApiError extends Error {
    status;
    code;
    details;
    constructor(message, status, code, details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
async function responseError(response, fallback) {
    const body = await response.json().catch(() => null);
    return new ApiError(body?.error?.message ?? fallback, response.status, body?.error?.code ?? 'API_ERROR', body?.error?.details);
}
export async function apiRequest(path, options = {}, token) {
    const headers = new Headers(options.headers);
    if (options.body)
        headers.set('Content-Type', 'application/json');
    if (token)
        headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(`${getApiBaseUrl()}${path}`, { ...options, headers });
    if (!response.ok) {
        throw await responseError(response, 'The API request failed.');
    }
    if (response.status === 204)
        return undefined;
    return response.json();
}
export async function apiDownload(path, token) {
    const headers = new Headers();
    if (token)
        headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(`${getApiBaseUrl()}${path}`, { headers });
    if (!response.ok) {
        throw await responseError(response, 'The download could not be prepared.');
    }
    return response.blob();
}
