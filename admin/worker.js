const ADMIN_HTML = `__ADMIN_HTML__`;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "access-control-allow-origin": "*" } });
}
function auth(request, env) { const value = request.headers.get("authorization") || ""; return !!env.ADMIN_TOKEN && value === `Bearer ${env.ADMIN_TOKEN}`; }

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { headers: { "access-control-allow-origin": "*", "access-control-allow-headers": "authorization,content-type", "access-control-allow-methods": "GET,POST,PUT,DELETE,OPTIONS" } });
    if (url.pathname === "/" || url.pathname === "/admin") return new Response(ADMIN_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });

    if (url.pathname === "/api/client/subscription" && request.method === "GET") {
      const rows = await env.DB.prepare("SELECT content FROM configs WHERE enabled=1 ORDER BY updated_at DESC").all();
      return new Response((rows.results || []).map(r => r.content).join("\n"), { headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" } });
    }
    if (url.pathname === "/api/client/subscriptions" && request.method === "GET") {
      const rows = await env.DB.prepare("SELECT id,name FROM configs WHERE enabled=1 ORDER BY updated_at DESC").all();
      return json({ subscriptions: (rows.results || []).map(r => ({ id: r.id, name: r.name, url: `${url.origin}/api/client/config/${encodeURIComponent(r.id)}` })) });
    }
    if (url.pathname.startsWith("/api/client/config/") && request.method === "GET") {
      const id = decodeURIComponent(url.pathname.split("/").pop());
      const row = await env.DB.prepare("SELECT content FROM configs WHERE id=? AND enabled=1").bind(id).first();
      if (!row) return new Response("not found", { status: 404 });
      return new Response(row.content, { headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" } });
    }

    if (!auth(request, env)) return json({ error: "unauthorized" }, 401);
    if (url.pathname === "/api/configs" && request.method === "GET") {
      const rows = await env.DB.prepare("SELECT id,name,type,enabled,created_at,updated_at,content FROM configs ORDER BY updated_at DESC").all();
      return json({ configs: rows.results || [] });
    }
    if (url.pathname === "/api/configs" && (request.method === "POST" || request.method === "PUT")) {
      const body = await request.json();
      if (!body.name || !body.content) return json({ error: "name and content are required" }, 400);
      const id = body.id || crypto.randomUUID(), now = Date.now();
      await env.DB.prepare(`INSERT INTO configs(id,name,type,content,enabled,created_at,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,type=excluded.type,content=excluded.content,enabled=excluded.enabled,updated_at=excluded.updated_at`).bind(id, String(body.name), String(body.type || "auto"), String(body.content), body.enabled === false ? 0 : 1, now, now).run();
      return json({ ok: true, id });
    }
    const match = url.pathname.match(/^\/api\/configs\/([^/]+)$/);
    if (match && request.method === "DELETE") {
      await env.DB.prepare("DELETE FROM configs WHERE id=?").bind(decodeURIComponent(match[1])).run();
      return json({ ok: true });
    }
    return json({ error: "not found" }, 404);
  }
};
