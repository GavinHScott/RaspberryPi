import React from "react";
import ReactDOM from "react-dom/client";
import { SaltProvider } from "@salt-ds/core";
import { SmartDeviceDashboard } from "./SmartDeviceDashboard";
import "@salt-ds/theme/css/theme.css";
import "@salt-ds/core/css/salt-core.css";
import "../styles.css";

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <SaltProvider mode="dark" density="medium">
      <SmartDeviceDashboard />
    </SaltProvider>
  </React.StrictMode>,
);
