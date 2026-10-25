/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Backend origin for a frontend served elsewhere (e.g. GitHub Pages); empty = same origin.
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
