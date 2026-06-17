// Backend API client. The backend now requires "Authorization: Bearer <API_KEY>"
// (Görev 8.1). To keep API_KEY OFF the browser, calls are routed by environment:
//   - Server (server components / server actions / route handlers): hit the backend
//     directly and attach the key from the server-only API_KEY env var.
//   - Client (browser): hit the same-origin /api/proxy route, which injects the key
//     server-side. The key never ships in the client bundle.
const isServer = typeof window === "undefined";
const BACKEND = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let url: string;
  let headers: Record<string, string>;

  if (isServer) {
    const apiKey = process.env.API_KEY;
    if (!apiKey) throw new Error("API_KEY env var missing (server)");
    url = `${BACKEND}${path}`;
    headers = {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
      ...(init?.headers as Record<string, string> | undefined),
    };
  } else {
    // Browser → same-origin proxy adds the bearer key server-side.
    url = `/api/proxy${path}`;
    headers = {
      "Content-Type": "application/json",
      ...(init?.headers as Record<string, string> | undefined),
    };
  }

  const res = await fetch(url, { ...init, headers, cache: "no-store" });
  if (res.status === 401) {
    throw new Error("Backend auth failed — check API_KEY");
  }
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`API ${res.status}: ${text}`);
  }
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : ({} as T);
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "PUT", body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: "DELETE" }),
};
