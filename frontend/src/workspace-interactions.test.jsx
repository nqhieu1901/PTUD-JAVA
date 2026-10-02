// @vitest-environment jsdom
import { StrictMode, act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.jsx";
import AdminUsersView from "./components/AdminUsersView.jsx";
import AiAssistantView from "./components/AiAssistantView.jsx";
import CalendarView from "./components/CalendarView.jsx";
import { AccountDialog } from "./components/Dialogs.jsx";
import { api, ApiError } from "./api.js";
import { localDateKey } from "./planning.js";

vi.mock("./api.js", async (importOriginal) => ({
  ...await importOriginal(),
  api: vi.fn(),
  refreshCsrf: vi.fn().mockResolvedValue({ headerName: "X-CSRF-TOKEN", token: "test" }),
  clearCsrf: vi.fn()
}));

let container;
let root;
const user = { id: 10, fullName: "Thành viên", email: "member@example.test", role: "USER" };
const projectA = { id: 1, name: "Dự án A", currentUserRole: "MEMBER", status: "ACTIVE" };
const projectB = { id: 2, name: "Dự án mới", currentUserRole: "MEMBER", status: "ACTIVE" };
const workflow = [{ id: 1, code: "TODO", displayName: "Cần làm", terminal: false, position: 0 }];
const invitation = { id: 8, projectId: 2, taskId: null, type: "PROJECT_MEMBER_ADDED", read: true,
  title: "Bạn được thêm vào dự án", message: "Dự án mới", createdAt: "2026-09-06T10:00:00" };

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  api.mockReset();
  window.localStorage.clear();
  window.history.replaceState({}, "", "/");
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true,
    value: function () { this.open = true; }
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true,
    value: function () { this.open = false; }
  });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.restoreAllMocks();
  delete HTMLDialogElement.prototype.showModal;
  delete HTMLDialogElement.prototype.close;
});

async function render(element) {
  await act(async () => root.render(element));
}

async function click(button) {
  expect(button).toBeTruthy();
  await act(async () => button.click());
}

function buttonWithText(text) {
  return [...container.querySelectorAll("button")].find((button) => button.textContent.trim() === text);
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function workspaceApi({ getProjects = () => [projectA], notifications = [invitation], getUser = () => user } = {}) {
  api.mockImplementation(async (path) => {
    if (path === "/api/auth/providers") return { google: false };
    if (path === "/api/auth/me" || path === "/api/auth/login") return getUser();
    if (path === "/api/auth/logout") return null;
    if (path === "/api/projects") return getProjects();
    if (path === "/api/notifications?unreadOnly=false") return notifications;
    if (/\/workflow$/.test(path)) return workflow;
    if (/\/(tasks|members)$/.test(path)) return [];
    throw new Error(`Unexpected request: ${path}`);
  });
}

describe("mounted account screens", () => {
  it("loads Admin users under StrictMode and ignores the stale first effect response", async () => {
    const oldRequest = deferred();
    const latestRequest = deferred();
    api.mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(latestRequest.promise);
    const account = { id: 11, fullName: "Người dùng mới", email: "latest@example.test",
      role: "USER", status: "ACTIVE", createdAt: "2026-09-06" };
    await render(<StrictMode><AdminUsersView currentUser={{ ...user, role: "ADMIN" }}
      onSessionExpired={vi.fn()} /></StrictMode>);
    expect(api).toHaveBeenCalledTimes(2);
    await act(async () => latestRequest.resolve({ users: [account], totalElements: 1 }));
    expect(container.textContent).toContain("latest@example.test");
    expect(container.textContent).not.toContain("Đang tải tài khoản…");
    await act(async () => oldRequest.resolve({ users: [], totalElements: 0 }));
    expect(container.textContent).toContain("latest@example.test");
  });

  it("shows Google account instructions without an unusable current-password form", async () => {
    await render(<AccountDialog open user={{ ...user, passwordLoginEnabled: false }}
      onClose={vi.fn()} onUpdateProfile={vi.fn()} onChangePassword={vi.fn()} />);
    expect(container.textContent).toContain("Đăng nhập bằng Google");
    expect(container.querySelector('input[name="currentPassword"]')).toBeNull();
    expect(container.querySelector('input[name="fullName"]')).not.toBeNull();
    await render(<AccountDialog open user={user} onClose={vi.fn()}
      onUpdateProfile={vi.fn()} onChangePassword={vi.fn()} />);
    expect(container.querySelector('input[name="currentPassword"]')).not.toBeNull();
  });

  it("explains a Google email conflict and removes the callback reason from the URL", async () => {
    window.history.replaceState({}, "", "/?authError=google&reason=link_required");
    api.mockImplementation(async (path) => {
      if (path === "/api/auth/providers") return { google: true };
      throw new ApiError(401, {});
    });
    await render(<App />);
    expect(container.textContent).toContain("Hãy đăng nhập bằng email và mật khẩu");
    expect(window.location.search).toBe("");
  });

  it("explains a locked Google account without encouraging another login attempt", async () => {
    window.history.replaceState({}, "", "/?authError=google&reason=locked");
    api.mockImplementation(async (path) => {
      if (path === "/api/auth/providers") return { google: true };
      throw new ApiError(401, {});
    });
    await render(<App />);
    expect(container.textContent).toContain("Tài khoản đã bị khóa");
    expect(window.location.search).toBe("");
  });
});

describe("project and notification synchronization", () => {
  it("opens a new project invitation using a fresh project list instead of the login cache", async () => {
    let availableProjects = [projectA];
    workspaceApi({ getProjects: () => availableProjects });
    await render(<StrictMode><App /></StrictMode>);
    availableProjects = [projectA, projectB];
    await click(container.querySelector('[aria-label="Thông báo"]'));
    await click(buttonWithText("Mở dự án"));
    expect(container.querySelector("#project-select").value).toBe("2");
    expect(container.textContent).not.toContain("Bạn không còn quyền truy cập");
    expect(api).toHaveBeenCalledWith("/api/projects/2/tasks");
    expect(container.querySelector("dialog")).toBeNull();
  });

  it("refreshes project membership when the page becomes visible without reloading the current board", async () => {
    let availableProjects = [projectA];
    workspaceApi({ getProjects: () => availableProjects });
    await render(<App />);
    availableProjects = [projectA, projectB];
    vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
    await act(async () => document.dispatchEvent(new Event("visibilitychange")));
    expect(container.querySelectorAll("#project-select option")).toHaveLength(2);
    expect(container.querySelector("#project-select").value).toBe("1");
    expect(api.mock.calls.filter(([path]) => path === "/api/projects/1/tasks")).toHaveLength(1);
  });

  it("allows a user with no projects to refresh and discover a newly added project", async () => {
    let availableProjects = [];
    workspaceApi({ getProjects: () => availableProjects, notifications: [] });
    await render(<App />);
    availableProjects = [projectB];
    const refresh = container.querySelector('[aria-label="Tải lại bảng"]');
    expect(refresh.disabled).toBe(false);
    await click(refresh);
    expect(container.querySelector("#project-select").value).toBe("2");
  });

  it("does not navigate from a slow notification after the user selects another project", async () => {
    const lookup = deferred();
    let projectCalls = 0;
    workspaceApi({ getProjects: () => ++projectCalls === 1 ? [projectA, projectB] : lookup.promise });
    await render(<App />);
    await click(container.querySelector('[aria-label="Thông báo"]'));
    await click(buttonWithText("Mở dự án"));
    await act(async () => {
      const select = container.querySelector("#project-select");
      select.value = "1";
      select.dispatchEvent(new Event("change", { bubbles: true }));
    });
    await act(async () => lookup.resolve([projectA, projectB]));
    expect(container.querySelector("#project-select").value).toBe("1");
    expect(api.mock.calls.some(([path]) => path === "/api/projects/2/tasks")).toBe(false);
  });

  it.each(["success", "unauthorized"])("ignores a stale project %s after logout and login as another user", async (outcome) => {
    const lookup = deferred();
    let projectCalls = 0;
    let currentUser = user;
    workspaceApi({ getUser: () => currentUser,
      getProjects: () => ++projectCalls === 2 ? lookup.promise : [projectA] });
    await render(<App />);
    await click(container.querySelector('[aria-label="Thông báo"]'));
    await click(buttonWithText("Mở dự án"));
    await click(container.querySelector('[aria-label="Đăng xuất"]'));
    currentUser = { ...user, id: 20, fullName: "Phiên mới" };
    await act(async () => {
      container.querySelector('input[name="email"]').value = "new@example.test";
      container.querySelector('input[name="password"]').value = "test-password";
      container.querySelector("form").dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    });
    expect(container.textContent).toContain("Phiên mới");
    await act(async () => {
      if (outcome === "success") lookup.resolve([projectB]);
      else lookup.reject(new ApiError(401, {}));
    });
    expect(container.textContent).toContain("Phiên mới");
    expect(container.querySelector("#project-select").value).toBe("1");
    expect(container.querySelectorAll("#project-select option")).toHaveLength(1);
    expect(container.textContent).not.toContain("Phiên đăng nhập đã hết hạn");
  });
});

describe("calendar and AI interactions", () => {
  it("expands a busy day and opens the fourth task", async () => {
    const key = localDateKey(new Date());
    const tasks = Array.from({ length: 4 }, (_, index) => ({ id: index + 1,
      title: `Công việc ${index + 1}`, dueAt: `${key}T12:00:00`, status: workflow[0] }));
    const onOpenTask = vi.fn();
    await render(<CalendarView tasks={tasks} onOpenTask={onOpenTask} />);
    expect(container.querySelectorAll(".calendar-task")).toHaveLength(3);
    await click(container.querySelector(".calendar-more"));
    expect(container.querySelectorAll(".calendar-task")).toHaveLength(4);
    await click(container.querySelectorAll(".calendar-task")[3]);
    expect(onOpenTask).toHaveBeenCalledWith(tasks[3]);
    await click(container.querySelector(".calendar-more"));
    expect(container.querySelectorAll(".calendar-task")).toHaveLength(3);
  });

  it("clears old successful suggestions when a new generation fails", async () => {
    api.mockImplementation(async (path) => {
      if (path.endsWith("/latest")) return { requestId: 1, status: "SUCCEEDED", suggestions: [
        { id: 99, decisionStatus: "PENDING", proposed: { title: "Gợi ý cũ" } }
      ] };
      throw new ApiError(502, { message: "Không kết nối được AI" });
    });
    await render(<AiAssistantView project={{ ...projectA, currentUserRole: "OWNER" }}
      tasks={[{ id: 5, title: "Viết báo cáo" }]} onAccepted={vi.fn()} onSessionExpired={vi.fn()} />);
    expect(container.textContent).toContain("Gợi ý cũ");
    await click(buttonWithText("Tạo gợi ý"));
    expect(container.textContent).toContain("Không kết nối được AI");
    expect(container.textContent).not.toContain("Gợi ý cũ");
    expect(buttonWithText("Chấp nhận & tạo task")).toBeUndefined();
  });
});
