// 前端用到的轻量封装，供首页 / 仪表盘共用
async function fetchMe() {
  try {
    const res = await fetch('/api/me', { credentials: 'include' });
    if (res.status === 401) return { authenticated: false };
    if (!res.ok) return { authenticated: false, error: 'HTTP ' + res.status };
    const data = await res.json();
    return { authenticated: true, ...data };
  } catch (err) {
    return { authenticated: false, error: String(err) };
  }
}

async function renderAuthStatus({ statusEl, loginBtn, dashBtn, logoutForm }) {
  const me = await fetchMe();
  if (!me.authenticated) {
    statusEl.className = 'status warn';
    statusEl.textContent = '尚未登录。点右侧按钮走 OAuth2 单点登录。';
    loginBtn.style.display = '';
    if (dashBtn) dashBtn.style.display = 'none';
    if (logoutForm) logoutForm.style.display = 'none';
    return;
  }
  statusEl.className = 'status ok';
  statusEl.textContent = `已登录：${me.name || me.email || '(未知)'}`;
  loginBtn.style.display = 'none';
  if (dashBtn) dashBtn.style.display = '';
  if (logoutForm) logoutForm.style.display = '';
}
