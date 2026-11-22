import { createRoot } from "react-dom/client";
import App from "./App";
import TrackingApp from "./TrackingApp";
import "./styles.css";
import "./react.css";

const demo = new URLSearchParams(window.location.search).has("demo");
createRoot(document.getElementById("root")!).render(
  demo ? <App /> : <TrackingApp />,
);
