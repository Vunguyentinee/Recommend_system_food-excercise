function getTodayKey() {
      const now = new Date();
      const month = String(now.getMonth() + 1).padStart(2, '0');
      const day = String(now.getDate()).padStart(2, '0');
      return `${now.getFullYear()}-${month}-${day}`;
    }

    function baseUrl() {
      return window.location.origin;
    }

    let activityCalendarDate = new Date();

    function getSession() {
      const raw = localStorage.getItem('session');
      return raw ? JSON.parse(raw) : null;
    }

    function ensureSession() {
      const session = getSession();
      if (!session) {
        window.location.href = 'index.html';
        return null;
      }
      return session;
    }

    function logout() {
      localStorage.removeItem('session');
      window.location.href = 'index.html';
    }

    function renderNavbar(session) {
      const userName = session?.userName || '';
      document.getElementById('navUser').textContent = userName;
      document.getElementById('navLogin').style.display = session ? 'none' : '';
      document.getElementById('navLogout').style.display = session ? '' : 'none';
      document.getElementById('welcomeMessage').textContent =
        `Chào mừng ${userName || 'bạn'} đã quay trở lại`;
    }

    function hydrateSessionUser(session) {
      renderNavbar(session);
      if (!session || session.userName) return;
      fetch(`${baseUrl()}/api/auth/users/${session.userId}`)
        .then(res => res.ok ? res.json() : null)
        .then(user => {
          if (!user) return;
          const updatedSession = { ...session, userName: user.userName, role: user.role || session.role };
          localStorage.setItem('session', JSON.stringify(updatedSession));
          renderNavbar(updatedSession);
        })
        .catch(() => {});
    }

    function goToFood() {
      window.location.href = 'nutrition.html';
    }

    function goToWorkout() {
      window.location.href = 'workout.html';
    }

    function goToSurvey() {
      window.location.href = 'nutrition.html#survey';
    }

    function getProfile(userId) {
      const raw = localStorage.getItem(`profile_${userId}`);
      return raw ? JSON.parse(raw) : null;
    }

    function setProfile(userId, profile) {
      localStorage.setItem(`profile_${userId}`, JSON.stringify(profile));
    }

    function fetchProfile(userId) {
      return fetch(`${baseUrl()}/api/health-profiles/${userId}`)
        .then(res => {
          if (res.status === 404) {
            localStorage.removeItem(`profile_${userId}`);
            return null;
          }
          if (!res.ok) {
            throw new Error('Không thể tải hồ sơ.');
          }
          return res.json();
        })
        .then(profile => {
          if (profile) setProfile(userId, profile);
          return profile;
        })
        .catch(() => getProfile(userId));
    }

    function calculateTargetCalories(profile) {
      if (!profile) return 0;
      const savedTarget = parseFloat(profile.targetCalories);
      if (Number.isFinite(savedTarget)) return Math.round(savedTarget);
      const weight = parseFloat(profile.weight) || 0;
      const height = parseFloat(profile.height) || 0;
      const age = parseFloat(profile.age) || 0;
      const gender = profile.gender || 'male';
      const activity = parseFloat(profile.activityLevel) || 1;

      const bmr = gender === 'male'
        ? (10 * weight) + (6.25 * height) - (5 * age) + 5
        : (10 * weight) + (6.25 * height) - (5 * age) - 161;
      return Math.round(bmr * activity);
    }

    function genderLabel(value) {
      const normalized = String(value || '').trim().toLowerCase();
      if (normalized === 'male' || normalized === 'nam') return 'Nam';
      if (normalized === 'female' || normalized === 'nu' || normalized === 'nữ') return 'Nữ';
      return value || '-';
    }

    function healthGoalLabel(value) {
      const normalized = String(value || '').trim().toLowerCase();
      if (normalized === 'gain' || normalized.includes('tăng')) return 'Tăng cân';
      if (normalized === 'lose' || normalized.includes('giảm')) return 'Giảm cân';
      if (normalized === 'maintain' || normalized.includes('giữ')) return 'Giữ dáng';
      return value || '-';
    }

    function activityLevelLabel(value) {
      const numeric = Number(value);
      if (numeric === 1.2) return 'Ít vận động';
      if (numeric === 1.375) return 'Nhẹ';
      if (numeric === 1.55) return 'Vừa';
      if (numeric === 1.725) return 'Vận động nhiều';
      return value || '-';
    }

    function renderProfile(profile) {
      const profileSummary = document.getElementById('profileSummary');
      const targetSummary = document.getElementById('targetSummary');
      if (!profile) {
        profileSummary.textContent = 'Chưa có hồ sơ. Hãy cập nhật để tính toán mục tiêu calo.';
        targetSummary.textContent = 'Chưa có dữ liệu mục tiêu.';
        return;
      }
      profileSummary.innerHTML = `Cân nặng: ${profile.weight || '-'} kg<br/>Chiều cao: ${profile.height || '-'} cm<br/>Tuổi: ${profile.age || '-'}<br/>Giới tính: ${genderLabel(profile.gender)}<br/>Mức vận động: ${activityLevelLabel(profile.activityLevel)}<br/>Mục tiêu: ${healthGoalLabel(profile.healthGoal)}`;
      const target = calculateTargetCalories(profile);
      targetSummary.textContent = `Mục tiêu calo/ngày: ${target || '-'} kcal`;
    }

    function formatDateKey(date) {
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      return `${date.getFullYear()}-${month}-${day}`;
    }

    function monthLabel(date) {
      return `Tháng ${date.getMonth() + 1}/${date.getFullYear()}`;
    }

    function hasRatedItems(history) {
      return Array.isArray(history?.data)
        && history.data.some(item => item.rating !== null && item.rating !== undefined && item.rating !== '');
    }

    function fetchDailyHistory(endpoint, userId, dateKey) {
      const query = new URLSearchParams({ userId, date: dateKey });
      return fetch(`${baseUrl()}${endpoint}?${query}`)
        .then(res => res.ok ? res.json() : null)
        .catch(() => null);
    }

    function renderActivityCalendarGrid(statusByDate) {
      const calendar = document.getElementById('activityCalendar');
      const label = document.getElementById('calendarMonthLabel');
      const year = activityCalendarDate.getFullYear();
      const month = activityCalendarDate.getMonth();
      const firstDay = new Date(year, month, 1);
      const dayCount = new Date(year, month + 1, 0).getDate();
      const mondayFirstOffset = (firstDay.getDay() + 6) % 7;
      const todayKey = getTodayKey();
      const weekdays = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

      label.textContent = monthLabel(activityCalendarDate);

      let html = weekdays.map(day => `<div class="calendar-weekday">${day}</div>`).join('');
      for (let i = 0; i < mondayFirstOffset; i++) {
        html += '<div class="calendar-day empty"></div>';
      }

      for (let day = 1; day <= dayCount; day++) {
        const date = new Date(year, month, day);
        const dateKey = formatDateKey(date);
        const status = statusByDate[dateKey] || {};
        const active = status.food || status.workout;
        const classes = [
          'calendar-day',
          active ? 'active' : '',
          status.food ? 'has-food' : '',
          status.workout ? 'has-workout' : '',
          dateKey === todayKey ? 'today' : ''
        ].filter(Boolean).join(' ');
        const titleParts = [];
        if (status.food) titleParts.push('có đánh giá món ăn');
        if (status.workout) titleParts.push('có đánh giá bài tập');
        const title = titleParts.length ? `${dateKey}: ${titleParts.join(', ')}` : dateKey;
        const markers = active
          ? `<span class="markers">${status.food ? '<span class="calendar-badge food-badge">Ăn</span>' : ''}${status.workout ? '<span class="calendar-badge workout-badge">Tập</span>' : ''}</span>`
          : '<span class="markers"></span>';
        html += `<div class="${classes}" title="${title}"><span class="day-number">${day}</span>${markers}</div>`;
      }

      calendar.innerHTML = html;
    }

    function loadActivityCalendar(session) {
      const calendar = document.getElementById('activityCalendar');
      if (!calendar) return;

      document.getElementById('calendarMonthLabel').textContent = monthLabel(activityCalendarDate);
      calendar.innerHTML = '<div class="muted calendar-message">Đang tải lịch hoạt động...</div>';

      const year = activityCalendarDate.getFullYear();
      const month = activityCalendarDate.getMonth();
      const dayCount = new Date(year, month + 1, 0).getDate();
      const checks = [];

      for (let day = 1; day <= dayCount; day++) {
        const dateKey = formatDateKey(new Date(year, month, day));
        checks.push(
          Promise.all([
            fetchDailyHistory('/api/nutrition/history', session.userId, dateKey),
            fetchDailyHistory('/api/workouts/history', session.userId, dateKey)
          ]).then(([foodHistory, workoutHistory]) => ({
            dateKey,
            food: hasRatedItems(foodHistory),
            workout: hasRatedItems(workoutHistory)
          }))
        );
      }

      Promise.all(checks)
        .then(results => {
          const statusByDate = {};
          results.forEach(result => {
            statusByDate[result.dateKey] = {
              food: result.food,
              workout: result.workout
            };
          });
          renderActivityCalendarGrid(statusByDate);
        })
        .catch(() => {
          calendar.innerHTML = '<div class="muted calendar-message">Không thể tải lịch hoạt động.</div>';
        });
    }

    function changeCalendarMonth(delta) {
      const session = getSession();
      if (!session) return;
      activityCalendarDate = new Date(
        activityCalendarDate.getFullYear(),
        activityCalendarDate.getMonth() + delta,
        1
      );
      loadActivityCalendar(session);
    }

    function renderFoodSummary(session) {
      const query = new URLSearchParams({ userId: session.userId, date: getTodayKey() });
      fetch(`${baseUrl()}/api/nutrition/history?${query}`)
        .then(res => res.json().then(data => {
          if (!res.ok) throw new Error(data.message || 'Không thể tải lịch sử món ăn.');
          return data;
        }))
        .then(history => {
          if (!history.data.length) {
            document.getElementById('foodSummary').textContent = 'Chưa có món hoàn thành hôm nay.';
            document.getElementById('foodList').innerHTML = '<div class="muted">Không có dữ liệu</div>';
            return;
          }

          const avgRating = history.averageRating ? history.averageRating.toFixed(1) : 'chưa có';
          document.getElementById('foodSummary').textContent =
            `Đã ăn ${history.completedCount} món • ${history.totalCalories} kcal • Đánh giá TB: ${avgRating}`;

          document.getElementById('foodList').innerHTML = history.data.map(item => {
            const ratingText = item.rating ? `${item.rating}★` : 'Chưa đánh giá';
            const mealTime = item.mealTime ? ` • ${item.mealTime}` : '';
            return `<div class="list-item">${item.name} • ${item.calories} kcal • ${ratingText}${mealTime}</div>`;
          }).join('');
        })
        .catch(error => {
          document.getElementById('foodSummary').textContent = error.message;
          document.getElementById('foodList').innerHTML = '<div class="muted">Không có dữ liệu</div>';
        });
    }

    function renderWorkoutSummary(session) {
      const query = new URLSearchParams({ userId: session.userId, date: getTodayKey() });
      fetch(`${baseUrl()}/api/workouts/history?${query}`)
        .then(res => res.json().then(data => {
          if (!res.ok) throw new Error(data.message || 'Không thể tải lịch sử bài tập.');
          return data;
        }))
        .then(history => {
          if (!history.data.length) {
            document.getElementById('workoutSummary').textContent = 'Chưa có bài tập hôm nay.';
            document.getElementById('workoutList').innerHTML = '<div class="muted">Không có dữ liệu</div>';
            return;
          }

          const muscles = history.trainedMuscles.length ? ` • ${history.trainedMuscles.join(', ')}` : '';
          document.getElementById('workoutSummary').textContent =
            `Đã tập ${history.completedCount} bài • ${history.totalMinutes} phút • ${history.totalCalories} kcal${muscles}`;

          document.getElementById('workoutList').innerHTML = history.data.map(item => {
            const rating = item.rating ? ` • ${item.rating}★` : '';
            return `<div class="list-item">${item.name} • ${item.durationMinutes} phút • ${item.caloriesBurned} kcal${rating}</div>`;
          }).join('');
        })
        .catch(error => {
          document.getElementById('workoutSummary').textContent = error.message;
          document.getElementById('workoutList').innerHTML = '<div class="muted">Không có dữ liệu</div>';
        });
    }

    const session = ensureSession();
    if (session) {
      hydrateSessionUser(session);
      if (session.role === 'admin') {
        window.location.replace('nutrition.html#admin');
      } else {
        fetchProfile(session.userId).then(renderProfile);
        loadActivityCalendar(session);
        renderFoodSummary(session);
        renderWorkoutSummary(session);
      }
    }
