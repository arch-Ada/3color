export const demoMode = import.meta.env.MODE === 'demo';
export const demoStoragePrefix = `3color-demo-v2-${import.meta.env.VITE_DEMO_COLLECTION ?? 'development'}`;
