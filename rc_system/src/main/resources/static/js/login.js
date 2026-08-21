function setSession(userId, userName, role, token) {
      localStorage.setItem('session', JSON.stringify({ userId, userName, role, token }));
    }

    function baseUrl() {
      if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        return window.location.origin;
      }
      return "https://rc-system-health-backend.onrender.com";
    }

    function login() {
      const userName = document.getElementById('loginUserName').value.trim();
      const password = document.getElementById('loginPassword').value;
      if (!userName || !password) {
        document.getElementById('loginMsg').textContent = 'Vui lòng nhập UserName và mật khẩu';
        return;
      }
      
      const msgEl = document.getElementById('loginMsg');
      const btnEl = document.querySelector('button[onclick="login()"]');
      
      // Cap nhat trang thai dang load
      msgEl.textContent = 'Đang xử lý đăng nhập...';
      msgEl.style.color = '#3182ce';
      if (btnEl) {
        btnEl.disabled = true;
        btnEl.textContent = 'Đang xử lý...';
      }
      
      fetch(`${baseUrl()}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userName, password })
      })
        .then(res => res.json().then(data => ({ ok: res.ok, data })))
        .then(({ ok, data }) => {
          if (!ok) {
            msgEl.textContent = data.message || 'Đăng nhập thất bại';
            msgEl.style.color = '#e53e3e';
            if (btnEl) {
              btnEl.disabled = false;
              btnEl.textContent = 'Đăng nhập';
            }
            return;
          }
          
          // Đăng nhập thành công
          msgEl.textContent = 'Đăng nhập thành công! Đang chuyển hướng...';
          msgEl.style.color = '#38a169';
          setSession(data.userId, data.userName, data.role || 'user', data.token);
          
          setTimeout(() => {
            window.location.href = 'overview.html';
          }, 800);
        })
        .catch(err => {
          msgEl.textContent = 'Lỗi kết nối: ' + err.message;
          msgEl.style.color = '#e53e3e';
          if (btnEl) {
            btnEl.disabled = false;
            btnEl.textContent = 'Đăng nhập';
          }
        });
    }

    function goRegister() {
      window.location.href = 'register.html';
    }

    function goDashboard() {
      window.location.href = 'overview.html';
    }
