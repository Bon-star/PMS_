import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import { HiChatAlt2, HiX, HiPaperAirplane, HiSparkles } from 'react-icons/hi';
import './ChatWidget.css';

export default function ChatWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([
    { role: 'assistant', content: 'Hello! I am your AI assistant for the Project Management System. How can I help you today?' }
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isOpen]);

  const handleSend = async (e) => {
    e?.preventDefault();
    if (!input.trim() || loading) return;

    const userMessage = { role: 'user', content: input.trim() };
    const newMessages = [...messages, userMessage];
    setMessages(newMessages);
    setInput('');
    setLoading(true);

    try {
      const response = await axios.post('/api/student/ai-chat/message', {
        message: userMessage.content,
        history: messages.map(m => ({ role: m.role, content: m.content }))
      }, { withCredentials: true });

      setMessages([...newMessages, { role: 'assistant', content: response.data.reply }]);
    } catch (error) {
      const errorMessage = error.response?.data?.message || 'Sorry, I encountered an error communicating with the server.';
      setMessages([...newMessages, { role: 'assistant', content: errorMessage, isError: true }]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      {/* Floating Button */}
      <button 
        className={`chat-widget-fab ${isOpen ? 'hidden' : ''}`}
        onClick={() => setIsOpen(true)}
        aria-label="Open AI Assistant"
      >
        <HiSparkles className="fab-icon-bg" />
        <HiChatAlt2 className="fab-icon" />
      </button>

      {/* Chat Window */}
      <div className={`chat-widget-window ${isOpen ? 'open' : ''}`}>
        <div className="chat-header">
          <div className="chat-header-info">
            <HiSparkles className="chat-header-icon" />
            <div>
              <h3>AI Assistant</h3>
              <span>Always here to help</span>
            </div>
          </div>
          <button className="chat-close-btn" onClick={() => setIsOpen(false)}>
            <HiX />
          </button>
        </div>

        <div className="chat-messages">
          {messages.map((msg, idx) => (
            <div key={idx} className={`chat-message-wrapper ${msg.role}`}>
              <div className={`chat-message ${msg.role} ${msg.isError ? 'error' : ''}`}>
                {msg.content}
              </div>
            </div>
          ))}
          {loading && (
            <div className="chat-message-wrapper assistant">
              <div className="chat-message assistant typing">
                <span className="dot"></span>
                <span className="dot"></span>
                <span className="dot"></span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <form className="chat-input-area" onSubmit={handleSend}>
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask me anything..."
            disabled={loading}
          />
          <button type="submit" disabled={!input.trim() || loading} className="chat-send-btn">
            <HiPaperAirplane style={{ transform: 'rotate(90deg)' }} />
          </button>
        </form>
      </div>
    </>
  );
}
