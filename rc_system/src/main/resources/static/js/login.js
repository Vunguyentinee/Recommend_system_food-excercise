function setSession(userId, userName, role) {
      localStorage.setItem('session', JSON.stringify({ userId, userName, role }));
    }

    function baseUrl() {
      return window.location.origin;
    }

    function login() {
      const userName = document.getElementById('loginUserName').value.trim();
      const password = document.getElementById('loginPassword').value;
      if (!userName || !password) {
        document.getElementById('loginMsg').textContent = 'Vui lòng nhập UserName và mật  khẩu';
        return;
      }
      fetch(`${baseUrl()}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userName, password })
      })
        .then(res => res.json().then(data => ({ ok: res.ok, data })))
        .then(({ ok, data }) => {
          if (!ok) {
            document.getElementById('loginMsg').textContent = data.message || 'Đăng nhập thất bại';
            return;
          }
          setSession(data.userId, data.userName, data.role || 'user');
          window.location.href = 'overview.html';
        })
        .catch(err => {
          document.getElementById('loginMsg').textContent = err.message;
        });
    }

    function goRegister() {
      window.location.href = 'register.html';
    }

    function goDashboard() {
      window.location.href = 'overview.html';
    }
