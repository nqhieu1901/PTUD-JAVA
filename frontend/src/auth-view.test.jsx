import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import AuthView from "./components/AuthView.jsx";

describe("authentication view", () => {
  it("shows Google login when the backend provider is enabled", () => {
    const markup = renderToStaticMarkup(
      <AuthView error="" busy={false} googleEnabled onAuthenticate={() => {}} onGoogleLogin={() => {}} />
    );

    expect(markup).toContain("Tiếp tục với Google");
    expect(markup).toContain("auth-mode-sheet--login");
    expect(markup).not.toContain("Google chưa được cấu hình");
  });

  it("keeps Google login visible but disabled until credentials are configured", () => {
    const markup = renderToStaticMarkup(
      <AuthView error="" busy={false} googleEnabled={false} onAuthenticate={() => {}} onGoogleLogin={() => {}} />
    );

    expect(markup).toContain("Google chưa được cấu hình");
    expect(markup).toContain("google-login-button");
    expect(markup).toContain("disabled");
  });
});
