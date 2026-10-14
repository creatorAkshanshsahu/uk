// AKTV stream proxy for Cloudflare Workers (free plan is enough for personal use)
//
// Usage:  https://<your-worker>.workers.dev/?url=<encoded stream URL>[&k=<key>][&ua=<UA>][&ref=<Referer>]
//
// Optional: in Worker → Settings → Variables and Secrets add a secret named PROXY_KEY.
// When it is set, every request must include &k=<same value>, so strangers can't use your proxy.
// HLS playlists (.m3u8) are rewritten so every segment/key also goes through the Worker.

const DEFAULT_UA =
  "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "*",
  "Access-Control-Expose-Headers": "*",
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

    const self = new URL(request.url);
    const target = self.searchParams.get("url");
    if (!target) return new Response("AKTV proxy is running.", { headers: CORS });

    const key = self.searchParams.get("k") || "";
    if (env.PROXY_KEY && key !== env.PROXY_KEY) return new Response("Forbidden", { status: 403, headers: CORS });

    let t;
    try { t = new URL(target); } catch { return new Response("Bad url", { status: 400, headers: CORS }); }
    if (t.protocol !== "http:" && t.protocol !== "https:") return new Response("Bad url", { status: 400, headers: CORS });

    const ua = self.searchParams.get("ua") || DEFAULT_UA;
    const referer = self.searchParams.get("ref") || "";

    const headers = new Headers({ "User-Agent": ua });
    if (referer) headers.set("Referer", referer);
    const range = request.headers.get("Range");
    if (range) headers.set("Range", range);

    let upstream;
    try {
      upstream = await fetch(t.toString(), { headers, redirect: "follow" });
    } catch (e) {
      return new Response("Upstream error: " + e.message, { status: 502, headers: CORS });
    }

    const ctype = upstream.headers.get("content-type") || "";
    const looksHls = /mpegurl/i.test(ctype) || t.pathname.toLowerCase().endsWith(".m3u8");

    if (looksHls) {
      const text = await upstream.text();
      if (text.trimStart().startsWith("#EXTM3U")) {
        const base = upstream.url || t.toString();
        const wrap = (u) => {
          const abs = new URL(u, base).toString();
          const p = new URLSearchParams({ url: abs });
          if (key) p.set("k", key);
          if (ua !== DEFAULT_UA) p.set("ua", ua);
          if (referer) p.set("ref", referer);
          return `${self.origin}${self.pathname}?${p.toString()}`;
        };
        const out = text
          .split("\n")
          .map((line) => {
            const l = line.trim();
            if (!l) return line;
            if (l.startsWith("#")) return line.replace(/URI="([^"]+)"/g, (_, u) => `URI="${wrap(u)}"`);
            return wrap(l);
          })
          .join("\n");
        return new Response(out, {
          status: upstream.status,
          headers: { ...CORS, "Content-Type": "application/vnd.apple.mpegurl", "Cache-Control": "no-store" },
        });
      }
      return new Response(text, { status: upstream.status, headers: { ...CORS, "Content-Type": ctype } });
    }

    // Video segments and everything else: stream straight through
    const h = new Headers(CORS);
    for (const name of ["content-type", "content-length", "content-range", "accept-ranges"]) {
      const v = upstream.headers.get(name);
      if (v) h.set(name, v);
    }
    return new Response(upstream.body, { status: upstream.status, headers: h });
  },
};
