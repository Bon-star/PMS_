import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { HiArrowLeft, HiDocumentText, HiDownload, HiRefresh } from 'react-icons/hi';
import { studentAPI } from '../../api';
import '../../layouts/DashboardLayout.css';

export default function ProjectTaskCodeViewPage() {
  const navigate = useNavigate();
  const { taskId } = useParams();
  const [searchParams] = useSearchParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fileId = searchParams.get('file') || '';

  useEffect(() => {
    load();
  }, [taskId, fileId]);

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await studentAPI.getProjectTaskCode(taskId, fileId || undefined);
      setData(res.data);
    } catch (err) {
      setData(null);
      setError(err.response?.data?.message || 'Unable to load code preview.');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="page-loader"><div className="spinner spinner-lg"></div><span>Loading code preview...</span></div>;
  }

  if (error || !data?.success) {
    return (
      <div>
        <div className="page-header" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button className="btn btn-ghost btn-sm" onClick={() => navigate(-1)}><HiArrowLeft /> Go Back</button>
          <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /> Retry</button>
        </div>
        <div className="card">
          <div className="card-body">
            <div className="empty-state" style={{ padding: '48px 20px' }}>
              <div className="empty-state-title">Preview Unavailable</div>
              <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-size-sm)' }}>{error || 'This code preview could not be loaded.'}</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const lines = (data.content || '').split(/\r?\n/);

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <div>
          <div style={{ marginBottom: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button className="btn btn-ghost btn-sm" onClick={() => navigate(-1)}><HiArrowLeft /> Go Back</button>
            <button className="btn btn-ghost btn-sm" onClick={load}><HiRefresh /> Refresh</button>
          </div>
          <h1 className="page-title">{data.codeTitle || 'Code Preview'}</h1>
          <p className="page-subtitle">
            {data.taskName || 'Task'} {data.projectName ? `- ${data.projectName}` : ''}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <span className="badge badge-primary"><HiDocumentText style={{ marginRight: 6 }} /> {data.inlineCode ? 'Inline Code' : `${data.lineCount || lines.length} lines`}</span>
          {data.downloadUrl && (
            <a className="btn btn-ghost btn-sm" href={data.downloadUrl}>
              <HiDownload /> Download File
            </a>
          )}
        </div>
      </div>

      <div className="card">
        <div className="card-body" style={{ padding: 0 }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed' }}>
              <tbody>
                {lines.map((line, index) => (
                  <tr key={`${index}-${line.length}`} style={{ borderTop: index === 0 ? 'none' : '1px solid var(--border-light)' }}>
                    <td style={{ width: 72, padding: '8px 12px', textAlign: 'right', verticalAlign: 'top', color: 'var(--text-muted)', fontFamily: 'Consolas, Monaco, monospace', fontSize: '12px', userSelect: 'none', background: 'var(--gray-50)' }}>
                      {index + 1}
                    </td>
                    <td style={{ padding: '8px 12px', verticalAlign: 'top', fontFamily: 'Consolas, Monaco, monospace', fontSize: '13px', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                      {line || ' '}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div style={{ marginTop: 12, color: 'var(--text-secondary)', fontSize: 'var(--font-size-xs)' }}>
        {data.inlineCode ? 'This preview is coming from the inline code submitted with the task.' : 'This preview is coming from a viewable attachment file.'}
      </div>
    </div>
  );
}
