import { auth } from "@/auth";

// Server-side proxy: the browser calls /api/proxy/<backend-path>; this handler
// verifies the dashboard session, then forwards to the Spring Boot backend with
// the Bearer API_KEY attached. The key stays server-side, never in the browser.
const BACKEND = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function handle(req: Request, ctx: { params: { path: string[] } }): Promise<Response> {
  const session = await auth();
  if (!session) {
    return Response.json({ error: "Unauthorized" }, { status: 401 });
  }

  const apiKey = process.env.API_KEY;
  if (!apiKey) {
    return Response.json({ error: "API_KEY env var missing" }, { status: 500 });
  }

  const path = "/" + ctx.params.path.join("/");
  const search = new URL(req.url).search;
  const method = req.method.toUpperCase();
  const hasBody = method !== "GET" && method !== "HEAD" && method !== "DELETE";

  const backendRes = await fetch(`${BACKEND}${path}${search}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
    },
    body: hasBody ? await req.text() : undefined,
    cache: "no-store",
  });

  const text = await backendRes.text();
  return new Response(text || null, {
    status: backendRes.status,
    headers: {
      "Content-Type": backendRes.headers.get("content-type") ?? "application/json",
    },
  });
}

export {
  handle as GET,
  handle as POST,
  handle as PUT,
  handle as DELETE,
};
