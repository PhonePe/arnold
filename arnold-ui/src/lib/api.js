const API_BASE = process.env.NEXT_PUBLIC_API_URL || '';

async function fetchJSON(url, options = {}) {
  const res = await fetch(`${API_BASE}${url}`, {
    credentials: 'include',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }

  return res.json();
}

/** Get current authenticated user info from Olympus session */
export function fetchCurrentUser() {
  return fetchJSON('/api/me');
}

/** Search GitLab user by username */
export function fetchGitLabUser(username) {
  return fetchJSON(`/api/gitlab/users?username=${encodeURIComponent(username)}`);
}

/** Get merge requests assigned to a user */
export function fetchAssignedMRs(username, { perPage = 20, state = 'opened' } = {}) {
  const params = new URLSearchParams({
    assignee_username: username,
    state,
    per_page: String(perPage),
    order_by: 'updated_at',
    sort: 'desc',
  });
  return fetchJSON(`/api/gitlab/merge_requests?${params}`);
}
