import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "../index.css";

async function bootstrap() {
  if (import.meta.env.VITE_MARKETING_MODE === "true") {
    await import("@/shared/marketing/install");
  }
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}

void bootstrap();
