<template>
  <router-view />
</template>

<style>
:root {
  /* 默认主题：淡粉 blush */
  --theme-primary: #E89696;
  --theme-primary-hover: #DC8585;
  --theme-primary-active: #CF7575;
  --theme-primary-muted: rgba(232, 150, 150, 0.08);
  --theme-primary-muted-strong: rgba(232, 150, 150, 0.14);
  --theme-bg: #fffbfb;
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
