import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import {
  AccountDialog, MembersDialog, ProjectDialog, TaskDialog, WorkflowDialog,
  buildProjectPayload, buildTaskPayload,
  buildCommentPayload, buildMemberPayload, buildWorkflowPayload, canDeleteAttachment, canDeleteComment,
  formatFileSize, roleLabel, toDateTimeLocal
} from "./components/Dialogs.jsx";
import KanbanBoard from "./components/KanbanBoard.jsx";
import Workspace from "./components/Workspace.jsx";

describe("project dialog payload", () => {
  it("maps optional project fields to null", () => {
    expect(buildProjectPayload({ name: "Đồ án", description: "  ", startDate: "", dueDate: "" }))
      .toEqual({ name: "Đồ án", description: null, startDate: null, dueDate: null });
  });

  it("renders an archived project as read-only with only the restore lifecycle action", () => {
    const markup = renderProjectDialog({ status: "ARCHIVED" });

    expect(markup).toContain('aria-labelledby="project-dialog-title"');
    expect(markup).toContain('id="project-dialog-title">Mở lại dự án</h2>');
    expect(markup).toContain("Dự án đang ở chế độ chỉ xem");
    expect(markup).toContain("Mở lại dự án");
    expect(markup).toContain("disabled");
    expect(markup).not.toContain('type="submit"');
    expect(markup).not.toContain("Đóng dự án");
  });

  it("keeps update and archive actions for an active project", () => {
    const markup = renderProjectDialog({ status: "ACTIVE" });

    expect(markup).toContain('type="submit"');
    expect(markup).toContain("Lưu thay đổi");
    expect(markup).toContain("Đóng dự án");
    expect(markup).not.toContain("Dự án đang ở chế độ chỉ xem");
  });

  it("shows lifecycle settings to an archived owner but not to a member", () => {
    expect(renderWorkspace("OWNER")).toContain('aria-label="Mở lại dự án"');
    expect(renderWorkspace("MEMBER")).not.toContain('aria-label="Mở lại dự án"');
  });

  it("shows column management directly on the Kanban board only to an active owner", () => {
    expect(renderWorkspace("OWNER", "USER", "ACTIVE")).toContain("Thêm / quản lý cột");
    expect(renderWorkspace("MEMBER", "USER", "ACTIVE")).not.toContain("Thêm / quản lý cột");
  });

  it("renders an isolated administration workspace without project features", () => {
    const markup = renderWorkspace("OWNER", "ADMIN");

    expect(markup).toContain("Quản lý tài khoản");
    expect(markup).toContain("Phạm vi Admin");
    expect(markup).not.toContain("Website tốt nghiệp");
    expect(markup).not.toContain("Bảng công việc");
    expect(markup).not.toContain("Dự án hiện tại");
  });
});

describe("task dialog payload", () => {
  it("maps edit fields to the backend TaskRequest contract", () => {
    expect(buildTaskPayload({
      title: "Hoàn thiện giao diện",
      description: "  Kiểm tra responsive  ",
      priority: "HIGH",
      startAt: "2026-08-23T09:00",
      dueAt: "2026-08-24T17:00",
      estimatedMinutes: "90",
      assigneeId: "12",
      parentTaskId: "7"
    })).toEqual({
      title: "Hoàn thiện giao diện",
      description: "Kiểm tra responsive",
      priority: "HIGH",
      startAt: "2026-08-23T09:00",
      dueAt: "2026-08-24T17:00",
      estimatedMinutes: 90,
      assigneeId: 12,
      parentTaskId: 7
    });
  });

  it("uses null for optional fields left blank", () => {
    expect(buildTaskPayload({
      title: "Viết test",
      description: "   ",
      priority: "MEDIUM",
      startAt: "",
      dueAt: "2026-08-24T17:00",
      estimatedMinutes: "",
      assigneeId: "",
      parentTaskId: ""
    })).toMatchObject({
      description: null,
      startAt: null,
      estimatedMinutes: null,
      assigneeId: null,
      parentTaskId: null
    });
  });

  it("formats backend LocalDateTime values for datetime-local inputs", () => {
    expect(toDateTimeLocal("2026-08-23T09:30:45")).toBe("2026-08-23T09:30");
    expect(toDateTimeLocal(null)).toBe("");
  });

  it("renders a member task notification in read-only mode", () => {
    const markup = renderToStaticMarkup(createElement(TaskDialog, {
      open: true,
      task: {
        id: 12,
        projectId: 11,
        title: "Kiểm tra deadline",
        priority: "HIGH",
        dueAt: "2026-08-25T17:00:00"
      },
      tasks: [],
      members: [],
      busy: false,
      error: "",
      readOnly: true,
      onClose: () => {},
      onSubmit: () => {},
      onDelete: () => {}
    }));

    expect(markup).toContain('aria-labelledby="task-dialog-title"');
    expect(markup).toContain('id="task-dialog-title">Xem công việc</h2>');
    expect(markup).toContain("disabled");
    expect(markup).not.toContain('type="submit"');
    expect(markup).not.toContain("Xóa công việc");
  });

  it("lets a member comment and delete only their own comment while task fields stay read-only", () => {
    const markup = renderTaskDialog("MEMBER");

    expect(markup).toContain('id="task-dialog-title">Xem công việc</h2>');
    expect(markup).toContain('id="task-comment-content"');
    expect(markup).toContain('aria-label="Xóa bình luận của Minh An"');
    expect(markup).not.toContain('aria-label="Xóa bình luận của Lan Anh"');
    expect(markup).toContain('id="task-attachment-file"');
    expect(markup).toContain('aria-label="Xóa tệp cua-toi.pdf"');
    expect(markup).not.toContain('aria-label="Xóa tệp cua-nguoi-khac.pdf"');
    expect(markup).not.toContain("Xóa công việc");
  });

  it("keeps comments fully read-only for a viewer", () => {
    const markup = renderTaskDialog("VIEWER");

    expect(markup).toContain("quyền Người xem");
    expect(markup).toContain("không thể thêm hoặc xóa bình luận");
    expect(markup).toContain("Người xem có thể tải xuống nhưng không thể thêm hoặc xóa tệp");
    expect(markup).toContain("Tải xuống");
    expect(markup).not.toContain('id="task-comment-content"');
    expect(markup).not.toContain('id="task-attachment-file"');
    expect(markup).not.toContain('aria-label="Xóa bình luận');
    expect(markup).not.toContain('aria-label="Xóa tệp');
  });

  it("allows an owner to delete every comment", () => {
    const markup = renderTaskDialog("OWNER");

    expect(markup).toContain('aria-label="Xóa bình luận của Minh An"');
    expect(markup).toContain('aria-label="Xóa bình luận của Lan Anh"');
    expect(markup).toContain('aria-label="Xóa tệp cua-toi.pdf"');
    expect(markup).toContain('aria-label="Xóa tệp cua-nguoi-khac.pdf"');
    expect(canDeleteComment(activeProject("OWNER"), { id: 1 }, { author: { id: 2 } })).toBe(true);
  });

  it("formats attachment sizes and applies uploader-or-owner delete policy", () => {
    const ownAttachment = { uploadedBy: { id: 1 } };
    const otherAttachment = { uploadedBy: { id: 2 } };

    expect(formatFileSize(512)).toBe("512 B");
    expect(formatFileSize(1536)).toBe("1.5 KB");
    expect(formatFileSize(2 * 1024 * 1024)).toBe("2.0 MB");
    expect(canDeleteAttachment(activeProject("MEMBER"), { id: 1 }, ownAttachment)).toBe(true);
    expect(canDeleteAttachment(activeProject("MEMBER"), { id: 1 }, otherAttachment)).toBe(false);
    expect(canDeleteAttachment(activeProject("OWNER"), { id: 1 }, otherAttachment)).toBe(true);
    expect(canDeleteAttachment(activeProject("VIEWER"), { id: 1 }, ownAttachment)).toBe(false);
  });
});

describe("project member roles", () => {
  it("maps invite and comment values to their API request contracts", () => {
    expect(buildMemberPayload("  viewer@example.com ", "VIEWER"))
      .toEqual({ email: "viewer@example.com", role: "VIEWER" });
    expect(buildCommentPayload("  Cần xác nhận deadline.  "))
      .toEqual({ content: "Cần xác nhận deadline." });
  });

  it("renders invite and role-management controls for the owner", () => {
    const markup = renderMembersDialog("OWNER");

    expect(markup).toContain("Thêm bằng email");
    expect(markup).toContain('<option value="VIEWER">Người xem</option>');
    expect(markup).toContain('aria-label="Vai trò của Lan Anh"');
    expect(roleLabel("VIEWER")).toBe("Người xem");
  });

  it("shows Viewer access as read-only without management controls", () => {
    const markup = renderMembersDialog("VIEWER");

    expect(markup).toContain("Bạn đang có quyền Người xem");
    expect(markup).not.toContain("Thêm bằng email");
    expect(markup).not.toContain('aria-label="Vai trò của Lan Anh"');
    expect(markup).toContain("Người xem");
  });

  it("lets viewers open task details but not move tasks", () => {
    const task = sampleTask();
    const markup = renderToStaticMarkup(createElement(KanbanBoard, {
      tasks: [task],
      user: { id: 1 },
      project: activeProject("VIEWER"),
      filters: { query: "", priority: "" },
      loading: false,
      error: "",
      movingIds: new Set(),
      onFilterChange: () => {},
      onRetry: () => {},
      onMove: () => {},
      onEdit: () => {}
    }));

    expect(markup).toContain('aria-label="Xem công việc Kiểm tra phân quyền"');
    expect(markup).toContain("không thể thay đổi bảng");
    expect(markup).not.toContain('draggable="true"');
  });
});

describe("account dialog", () => {
  it("renders separate profile and password forms with immutable email", () => {
    const markup = renderToStaticMarkup(createElement(AccountDialog, {
      open: true,
      user: { id: 1, fullName: "Nguyễn Minh An", email: "an@example.com" },
      busyAction: null,
      profileError: "",
      passwordError: "",
      onClose: () => {},
      onUpdateProfile: () => {},
      onChangePassword: () => {}
    }));

    expect(markup).toContain("Cài đặt tài khoản");
    expect(markup).toContain('aria-labelledby="account-dialog-title"');
    expect(markup).toContain('name="fullName"');
    expect(markup).toContain('name="currentPassword"');
    expect(markup).toContain('name="newPassword"');
    expect(markup).toContain('name="confirmPassword"');
    expect(markup).toContain('readOnly=""');
  });
});

describe("project workflow", () => {
  const statuses = [
    { id: 1, code: "TODO", displayName: "Cần làm", displayOrder: 1, terminal: false },
    { id: 2, code: "REVIEW", displayName: "Kiểm thử", displayOrder: 2, terminal: false },
    { id: 3, code: "DONE", displayName: "Hoàn thành", displayOrder: 3, terminal: true }
  ];

  it("maps ordered editable rows to the atomic workflow API contract", () => {
    expect(buildWorkflowPayload([
      { id: 2, displayName: "  Đang kiểm thử ", terminal: false },
      { id: null, displayName: " Đã duyệt ", terminal: true }
    ])).toEqual([
      { id: 2, displayName: "Đang kiểm thử", terminal: false },
      { id: null, displayName: "Đã duyệt", terminal: true }
    ]);
  });

  it("renders ordered status controls and the terminal selector", () => {
    const markup = renderToStaticMarkup(createElement(WorkflowDialog, {
      open: true,
      project: activeProject("OWNER"),
      statuses,
      busy: false,
      error: "",
      onClose: () => {},
      onSubmit: () => {}
    }));

    expect(markup).toContain("Cột trạng thái Kanban");
    expect(markup).toContain('aria-label="Tên trạng thái 2"');
    expect(markup).toContain('value="Kiểm thử"');
    expect(markup).toContain('name="terminalStatus"');
    expect(markup).toContain("+ Thêm cột");
  });
});

function renderProjectDialog(projectOverrides) {
  return renderToStaticMarkup(createElement(ProjectDialog, {
    open: true,
    project: {
      id: 11,
      name: "Website tốt nghiệp",
      description: "Dự án mẫu",
      startDate: "2026-08-01",
      dueDate: "2026-09-01",
      currentUserRole: "OWNER",
      ...projectOverrides
    },
    busy: false,
    error: "",
    onClose: () => {},
    onSubmit: () => {},
    onArchive: () => {},
    onRestore: () => {}
  }));
}

function renderWorkspace(role, systemRole = "USER", projectStatus = "ARCHIVED") {
  const project = {
    id: 11,
    name: "Website tốt nghiệp",
    status: projectStatus,
    currentUserRole: role,
    dueDate: null
  };
  const noop = () => {};
  return renderToStaticMarkup(createElement(Workspace, {
    user: { id: 1, fullName: "Nguyễn Minh An", email: "an@example.com", role: systemRole },
    projects: [project],
    project,
    tasks: [],
    statuses: [],
    filters: { query: "", priority: "" },
    loading: false,
    boardError: "",
    movingIds: new Set(),
    notifications: [],
    notificationsLoading: false,
    notificationsError: "",
    markingNotificationIds: new Set(),
    markingAllNotifications: false,
    onSelectProject: noop,
    onNewProject: noop,
    onNewTask: noop,
    onEditTask: noop,
    onManageProject: noop,
    onManageMembers: noop,
    onManageWorkflow: noop,
    onRefresh: noop,
    onRefreshNotifications: noop,
    onMarkNotificationRead: noop,
    onMarkAllNotificationsRead: noop,
    onAiAccepted: noop,
    onSessionExpired: noop,
    onFilterChange: noop,
    onMove: noop,
    onLogout: noop
  }));
}

function renderTaskDialog(role) {
  return renderToStaticMarkup(createElement(TaskDialog, {
    open: true,
    task: sampleTask(),
    tasks: [sampleTask()],
    members: [],
    busy: false,
    error: "",
    readOnly: role !== "OWNER",
    user: { id: 1, fullName: "Minh An", email: "an@example.com" },
    project: activeProject(role),
    comments: [
      { id: 1, content: "Bình luận của tôi", author: { id: 1, fullName: "Minh An" }, createdAt: "2026-08-31T08:00:00" },
      { id: 2, content: "Bình luận khác", author: { id: 2, fullName: "Lan Anh" }, createdAt: "2026-08-31T08:05:00" }
    ],
    attachments: [
      { id: 3, originalName: "cua-toi.pdf", sizeBytes: 2048, uploadedBy: { id: 1, fullName: "Minh An" }, createdAt: "2026-08-31T08:10:00" },
      { id: 4, originalName: "cua-nguoi-khac.pdf", sizeBytes: 4096, uploadedBy: { id: 2, fullName: "Lan Anh" }, createdAt: "2026-08-31T08:15:00" }
    ],
    onClose: () => {},
    onSubmit: () => {},
    onDelete: () => {},
    onAddComment: () => {},
    onDeleteComment: () => {}
  }));
}

function renderMembersDialog(role) {
  return renderToStaticMarkup(createElement(MembersDialog, {
    open: true,
    project: activeProject(role),
    members: [
      { userId: 1, fullName: "Minh An", email: "an@example.com", role },
      { userId: 2, fullName: "Lan Anh", email: "lan@example.com", role: "VIEWER" }
    ],
    busy: false,
    error: "",
    onClose: () => {},
    onAdd: () => {},
    onRemove: () => {},
    onChangeRole: () => {}
  }));
}

function activeProject(role) {
  return { id: 11, name: "Website tốt nghiệp", status: "ACTIVE", currentUserRole: role, dueDate: null };
}

function sampleTask() {
  return {
    id: 12,
    projectId: 11,
    title: "Kiểm tra phân quyền",
    description: "Kiểm tra giao diện Viewer",
    priority: "HIGH",
    status: { code: "TODO", name: "Cần làm" },
    boardOrder: 0,
    dueAt: "2026-09-05T17:00:00",
    assignee: { id: 1, fullName: "Minh An" }
  };
}
