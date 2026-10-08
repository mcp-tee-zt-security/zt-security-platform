import * as React from 'react';
import { apiGet } from '../api/client';

export function useApi<T>(path: string, fallback: T) {
  const [data, setData] = React.useState<T>(fallback);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      setData(await apiGet(path) as T);
      setError('');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [path]);

  React.useEffect(() => { void load(); }, [load]);
  return {data, loading, error, reload: load};
}
