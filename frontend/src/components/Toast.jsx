import { createContext, useContext, useState, useCallback } from 'react';
import { HiCheckCircle, HiXCircle, HiInformationCircle, HiX } from 'react-icons/hi';

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'info', duration = 4000) => {
    const id = Date.now() + Math.random();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, duration);
  }, []);

  const success = useCallback((msg) => addToast(msg, 'success'), [addToast]);
  const error = useCallback((msg) => addToast(msg, 'error'), [addToast]);
  const info = useCallback((msg) => addToast(msg, 'info'), [addToast]);

  const removeToast = (id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  };

  const icons = {
    success: <HiCheckCircle size={22} />,
    error: <HiXCircle size={22} />,
    info: <HiInformationCircle size={22} />,
  };

  return (
    <ToastContext.Provider value={{ addToast, success, error, info }}>
      {children}
      <div style={{ position: 'fixed', top: 20, right: 20, zIndex: 9999, display: 'flex', flexDirection: 'column', gap: 10 }}>
        {toasts.map(toast => (
          <div key={toast.id} className={`toast toast-${toast.type}`}>
            {icons[toast.type]}
            <span style={{ flex: 1, fontSize: '0.875rem' }}>{toast.message}</span>
            <button onClick={() => removeToast(toast.id)} style={{ background: 'none', color: 'inherit', padding: 4, display: 'flex', alignItems: 'center' }}>
              <HiX size={16} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
