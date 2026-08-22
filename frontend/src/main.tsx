import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ControlPage } from "./ControlPage";
import { HubPage } from "./HubPage";
import { StagePage } from "./StagePage";
import "./styles.css";

/**
 * Drei Seiten, ein Bundle.
 *
 * `/` ist die Übersicht: Von dort führt je ein Feld zu den Bildschirmen, die es gibt. Kommen später
 * ein Soundboard oder Spieleroberflächen dazu, sind das weitere Felder — die Startseite bleibt die
 * eine Adresse, die man sich merken muss.
 *
 * Für so wenige Routen wäre eine Router-Bibliothek mehr Abhängigkeit als Nutzen; die Zuordnung
 * passiert beim Start einmal anhand des Pfads. Spring Boot liefert für alle drei dieselbe
 * index.html aus.
 */
const path = window.location.pathname.replace(/\/+$/, "") || "/";
const stageRoute = /^\/stage\/(.+)$/.exec(path);

function route() {
  if (stageRoute) {
    return <StagePage surfaceId={decodeURIComponent(stageRoute[1])} />;
  }
  if (path === "/control") {
    return <ControlPage />;
  }
  return <HubPage />;
}

createRoot(document.getElementById("root")!).render(<StrictMode>{route()}</StrictMode>);
