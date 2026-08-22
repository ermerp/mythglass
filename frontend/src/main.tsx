import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ControlPage } from "./ControlPage";
import { StagePage } from "./StagePage";
import "./styles.css";

/**
 * Zwei Seiten, ein Bundle. Für genau zwei Routen wäre eine Router-Bibliothek mehr Abhängigkeit als
 * Nutzen; die Zuordnung passiert beim Start einmal anhand des Pfads. Spring Boot leitet
 * `/stage/{id}` serverseitig auf dieselbe index.html weiter.
 */
const stageRoute = /^\/stage\/(.+?)\/?$/.exec(window.location.pathname);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    {stageRoute ? <StagePage surfaceId={decodeURIComponent(stageRoute[1])} /> : <ControlPage />}
  </StrictMode>,
);
