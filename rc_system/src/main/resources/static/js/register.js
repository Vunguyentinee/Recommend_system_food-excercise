function setSession(userId, userName, role) {
      localStorage.setItem('session', JSON.stringify({ userId, userName, role }));
    }

    function baseUrl() {
      return window.location.origin;
    }

    function register() {
      const userName = document.getElementById('registerUserName').value.trim();
      const email = document.getElementById('registerEmail').value.trim();
      const password = document.getElementById('registerPassword').value;
      if (!userName || !email || !password) {
        document.getElementById('registerMsg').textContent = 'Vui lòng nhập đầy đủ thông tin.';
        return;
      }
      fetch(`${baseUrl()}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userName, password, email })
      })
        .then(res => res.json().then(data => ({ ok: res.ok, data })))
        .then(({ ok, data }) => {
          if (!ok) {
            document.getElementById('registerMsg').textContent = data.message || 'Đăng ký thất bại.';
            return;
          }
          setSession(data.userId, data.userName, data.role || 'user');
          window.location.href = 'overview.html';
        })
        .catch(err => {
          document.getElementById('registerMsg').textContent = err.message;
        });
    }

    function goLogin() {
      window.location.href = 'login.html';
    }

    function goDashboard() {
      window.location.href = 'overview.html';
    }
