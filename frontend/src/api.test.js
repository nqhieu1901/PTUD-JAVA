import { afterEach, describe, expect, it, vi } from "vitest";
import { api, clearCsrf, refreshCsrf } from "./api.js";

describe("API request bodies", () => {
  afterEach(() => {
    clearCsrf();
    vi.unstubAllGlobals();
  });

  it("sends FormData unchanged and lets the browser create its multipart boundary", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: "X-CSRF-TOKEN", token: "csrf-token" }))
      .mockResolvedValueOnce(jsonResponse({ id: 1, originalName: "plan.pdf" }, 201));
    vi.stubGlobal("fetch", fetchMock);
    await refreshCsrf();
    const form = new FormData();
    form.append("file", new Blob(["pdf"], { type: "application/pdf" }), "plan.pdf");

    await api("/api/upload", { method: "POST", body: form });

    const options = fetchMock.mock.calls[1][1];
    expect(options.body).toBe(form);
    expect(options.headers.get("Content-Type")).toBeNull();
    expect(options.headers.get("X-CSRF-TOKEN")).toBe("csrf-token");
  });

  it("keeps JSON serialization for ordinary API requests", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: "X-CSRF-TOKEN", token: "csrf-token" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    await refreshCsrf();

    await api("/api/example", { method: "POST", body: { name: "SmartTask" } });

    const options = fetchMock.mock.calls[1][1];
    expect(options.body).toBe('{"name":"SmartTask"}');
    expect(options.headers.get("Content-Type")).toBe("application/json");
  });

  it("preserves Retry-After seconds on a rate-limited response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "RATE_LIMITED" }), {
      status: 429, headers: { "Content-Type": "application/json", "Retry-After": "30" }
    })));
    await expect(api("/api/example")).rejects.toMatchObject({ status: 429, retryAfterSeconds: 30 });
  });
});

function jsonResponse(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}
