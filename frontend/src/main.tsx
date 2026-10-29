import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import { LocaleProvider } from './i18n/LocaleProvider';
import './styles/tokens.css';
import './styles/app.css';

const container = document.getElementById('root');
if (!container) throw new Error('Không tìm thấy vùng hiển thị ứng dụng.');

createRoot(container).render(
  <StrictMode>
    <BrowserRouter>
      <LocaleProvider><App /></LocaleProvider>
    </BrowserRouter>
  </StrictMode>,
);
