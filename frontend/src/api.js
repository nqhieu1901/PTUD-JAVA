let csrf = null;

export class ApiError extends Error {
  constructor(status, payload, headers) {
    super(payload?.message || "Yêu cầu không thành công");
    this.name = "ApiError";
    this.status = status;
    this.code = payload?.code || "REQUEST_FAILED";
    this.fieldErrors = payload?.fieldErrors || null;
    const retryAfter = Number(headers?.get("Retry-After"));
    this.retryAfterSeconds = Number.isFinite(retryAfter) && retryAfter > 0 ? Math.ceil(retryAfter) : null;
  }
}

async function parseResponse(response) {
  if (response.status === 204) return null;
  if (!(response.headers.get("content-type") || "").includes("application/json")) return null;
  try {
    return await response.json();
  } catch {
    return null;
  }
}

export async function refreshCsrf() {
  const response = await fetch("/api/auth/csrf", {
    credentials: "same-origin",
    headers: { Accept: "application/json" }
  });
  const payload = await parseResponse(response);
  if (!response.ok) throw new ApiError(response.status, payload, response.headers);
  csrf = payload;
  return csrf;
}

export function clearCsrf() {
  csrf = null;
}

export async function api(path, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers || {});
  const multipart = typeof FormData !== "undefined" && options.body instanceof FormData;
  headers.set("Accept", "application/json");

  if (options.body !== undefined && !multipart) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    csrf ??= await refreshCsrf();
    headers.set(csrf.headerName, csrf.token);
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: "same-origin",
    body: options.body === undefined ? undefined : multipart ? options.body : JSON.stringify(options.body)
  });
  const payload = await parseResponse(response);
  if (!response.ok) throw new ApiError(response.status, payload, response.headers);
  return payload;
}

export async function downloadFile(path, filename) {
  const response = await fetch(path, {
    credentials: "same-origin",
    headers: { Accept: "*/*" }
  });
  if (!response.ok) {
    const payload = await parseResponse(response);
    throw new ApiError(response.status, payload, response.headers);
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
