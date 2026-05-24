import React from "react";
import ReactDOM from "react-dom/client";
import { SmartDeviceDashboard } from "./SmartDeviceDashboard";
import "../styles.css";

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <SmartDeviceDashboard />
  </React.StrictMode>,
);
