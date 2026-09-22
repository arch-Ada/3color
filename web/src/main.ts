import { mount } from 'svelte';
import App from './App.svelte';
import { initializeInterface } from './ui/preferences';
import './style.css';
initializeInterface();
mount(App, { target: document.getElementById('app')! });
