import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import { LanguageProvider } from "@/i18n/i18n";
import { queryClient } from "@/lib/queryClient";
import { registerServiceWorker } from "@/lib/pwa";
import "./styles.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <LanguageProvider>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </QueryClientProvider>
      </LanguageProvider>
    </ErrorBoundary>
  </React.StrictMode>,
);

registerServiceWorker();
