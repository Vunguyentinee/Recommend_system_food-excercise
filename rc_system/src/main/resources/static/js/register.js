function setSession(userId, userName, role, token) {
      localStorage.setItem('session', JSON.stringify({ userId, userName, role, token }));
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
      
      const msgEl = document.getElementById('registerMsg');
      const btnEl = document.querySelector('button[onclick="register()"]');
      
      // Cap nhat trang thai dang load
      msgEl.textContent = 'Đang xử lý đăng ký tài khoản...';
      msgEl.style.color = '#3182ce';
      if (btnEl) {
        btnEl.disabled = true;
        btnEl.textContent = 'Đang xử lý...';
      }
      
      fetch(`${baseUrl()}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userName, password, email })
      })
        .then(res => res.json().then(data => ({ ok: res.ok, data })))
        .then(({ ok, data }) => {
          if (!ok) {
            msgEl.textContent = data.message || 'Đăng ký thất bại.';
            msgEl.style.color = '#e53e3e';
            if (btnEl) {
              btnEl.disabled = false;
              btnEl.textContent = 'Đăng ký';
            }
            return;
          }
          
          // Đăng ký thành công
          msgEl.textContent = 'Đăng ký tài khoản thành công! Đang chuyển hướng...';
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
            btnEl.textContent = 'Đăng ký';
          }
        });
    }

    function goLogin() {
      window.location.href = 'login.html';
    }

    function goDashboard() {
      window.location.href = 'overview.html';
    }
