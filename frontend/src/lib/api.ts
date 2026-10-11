export class ApiError extends Error {
  code: string;
  status: number;
  details: string[];

  constructor(message: string, code = "ERROR", status = 400, details: string[] = []) {
    super(message);
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

let accessToken: string | null = null;
let refreshPromise: Promise<boolean> | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

async function refresh() {
  if (!refreshPromise) {
    refreshPromise = fetch("/api/v1/auth/refresh", { method: "POST", credentials: "include" })
      .then(async (response) => {
        if (!response.ok) return false;
        const body = await response.json();
        if (!body?.success || !body.data?.accessToken) return false;
        accessToken = body.data.accessToken as string;
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function api<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  const response = await fetch(path, { ...options, headers, credentials: "include" });
  if (response.status === 401 && retry && !path.includes("/api/v1/auth/")) {
    const ok = await refresh();
    if (ok) return api<T>(path, options, false);
  }
  const body = await response.json().catch(() => null);
  if (!response.ok || body?.success === false) {
    throw new ApiError(
      body?.error?.message || "The request failed.",
      body?.error?.code || "ERROR",
      response.status,
      body?.error?.details || []
    );
  }
  return body.data as T;
}

export function apiErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.details.length ? `${error.message} ${error.details.join(" ")}` : error.message;
  }
  return "Something went wrong. Try again.";
}
