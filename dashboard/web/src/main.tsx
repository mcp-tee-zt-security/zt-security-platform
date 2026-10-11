import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import AuthBoundary from './components/AuthBoundary';
import './style.css';

const root = document.getElementById('root');
if (!root) throw new Error('ZT Security dashboard root element not found');
createRoot(root).render(<React.StrictMode>
<AuthBoundary><App /></AuthBoundary>
</React.StrictMode>);
