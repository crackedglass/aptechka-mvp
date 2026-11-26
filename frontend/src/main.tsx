import { createRoot } from "react-dom/client";
import App from "./App";
import TrackingApp from "./TrackingApp";
import "./styles.css";
import "./react.css";

const params = new URLSearchParams(window.location.search);
const tracking = params.has("tracking");
createRoot(document.getElementById("root")!).render(
  tracking ? <TrackingApp /> : <App />,
);
