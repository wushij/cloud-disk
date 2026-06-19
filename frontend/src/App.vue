<template>
  <router-view />
</template>

<style>
:root {
  /* 默认主题：石墨 slate */
  --theme-primary: #010710;
  --theme-primary-hover: #0f1a2e;
  --theme-primary-active: #000000;
  --theme-primary-muted: rgba(1, 7, 16, 0.08);
  --theme-primary-muted-strong: rgba(1, 7, 16, 0.14);
  --theme-bg: #f3f5f8;
  --theme-sidebar-bg: #ffffff;
  --theme-text-base: #1e293b;
  --theme-text-secondary: #64748b;
  --theme-border: #e2e8f0;
}

html,
body,
#app {
  margin: 0;
  padding: 0;
  width: 100%;
  min-height: 100%;
  height: 100%;
  overflow: hidden;
  font-family:
    'Inter',
    system-ui,
    -apple-system,
    'Segoe UI',
    Roboto,
    'Helvetica Neue',
    Arial,
    'Noto Sans SC',
    sans-serif;
  background: var(--theme-bg, var(--cd-bg));
  color: var(--theme-text-base, var(--cd-text-primary));
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
</style>
