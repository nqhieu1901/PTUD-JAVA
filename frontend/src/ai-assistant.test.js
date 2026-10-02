import { describe, expect, it } from "vitest";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import AiAssistantView from "./components/AiAssistantView.jsx";
import {
  canManageAi,
  chooseSourceTaskId,
  normalizeSuggestionCount,
  replaceAiSuggestion
} from "./ai-assistant.js";

describe("AI assistant utilities", () => {
  it("keeps the requested suggestion count inside the supported range", () => {
    expect(normalizeSuggestionCount(1)).toBe(2);
    expect(normalizeSuggestionCount("5.9")).toBe(5);
    expect(normalizeSuggestionCount(9)).toBe(6);
    expect(normalizeSuggestionCount("invalid")).toBe(3);
  });

  it("keeps a valid source task and falls back to the first available task", () => {
    const tasks = [{ id: 10 }, { id: 20 }];
    expect(chooseSourceTaskId(tasks, 20)).toBe(20);
    expect(chooseSourceTaskId(tasks, 99)).toBe(10);
    expect(chooseSourceTaskId([], 20)).toBe(20);
  });

  it("allows only an active project owner to decide and replaces one suggestion", () => {
    expect(canManageAi({ currentUserRole: "OWNER", status: "ACTIVE" })).toBe(true);
    expect(canManageAi({ currentUserRole: "MEMBER", status: "ACTIVE" })).toBe(false);
    expect(canManageAi({ currentUserRole: "OWNER", status: "ARCHIVED" })).toBe(false);

    const response = { suggestions: [{ id: 1, decisionStatus: "PENDING" }, { id: 2, decisionStatus: "PENDING" }] };
    const updated = replaceAiSuggestion(response, { id: 2, decisionStatus: "ACCEPTED" });
    expect(updated.suggestions).toEqual([
      { id: 1, decisionStatus: "PENDING" },
      { id: 2, decisionStatus: "ACCEPTED" }
    ]);
    expect(response.suggestions[1].decisionStatus).toBe("PENDING");
  });

  it("renders provider-neutral controls before a generation is created", () => {
    const html = renderToStaticMarkup(React.createElement(AiAssistantView, {
      project: { id: 1, currentUserRole: "OWNER", status: "ACTIVE" },
      tasks: [{ id: 10, title: "Tích hợp thanh toán" }],
      boardLoading: false,
      boardError: "",
      onBoardRetry: () => {},
      onAccepted: async () => {},
      onSessionExpired: () => {}
    }));

    expect(html).toContain("Tạo gợi ý");
    expect(html).toContain("Nguồn tạo gợi ý được hiển thị trong từng kết quả");
    expect(html).not.toContain("Tạo gợi ý DEMO");
  });
});
