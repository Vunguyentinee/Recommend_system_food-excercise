let workoutPlan = null;
    let workoutHistory = null;

    function getTodayKey() {
      const now = new Date();
      const month = String(now.getMonth() + 1).padStart(2, '0');
      const day = String(now.getDate()).padStart(2, '0');
      return `${now.getFullYear()}-${month}-${day}`;
    }

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
      document.getElementById('navUser').textContent = session?.userName || '';
      document.getElementById('navLogin').style.display = session ? 'none' : '';
      document.getElementById('navLogout').style.display = session ? '' : 'none';
    }

    function safetyGateKey(type, userId) {
      return `${type}SafetyAccepted_${userId || 'guest'}`;
    }

    function showSafetyGate(type, userId) {
      if (localStorage.getItem(safetyGateKey(type, userId)) === 'true') return;
      const gate = document.getElementById(`${type}SafetyGate`);
      if (gate) gate.classList.add('open');
    }

    function acceptSafetyGate(type) {
      const session = getSession();
      const userId = session?.userId || 'guest';
      const checkbox = document.getElementById(`${type}SafetyCheck`);
      const message = document.getElementById(`${type}SafetyMsg`);
      if (!checkbox?.checked) {
        if (message) message.textContent = 'Bạn cần tích xác nhận trước khi tiếp tục.';
        return;
      }
      localStorage.setItem(safetyGateKey(type, userId), 'true');
      const gate = document.getElementById(`${type}SafetyGate`);
      if (gate) gate.classList.remove('open');
    }

    function hydrateSessionUser(session) {
      renderNavbar(session);
      if (!session || session.userName) return;
      fetch(`${window.location.origin}/api/auth/users/${session.userId}`)
        .then(res => res.ok ? res.json() : null)
        .then(user => {
          if (!user) return;
          const updated = { ...session, userName: user.userName, role: user.role || session.role };
          localStorage.setItem('session', JSON.stringify(updated));
          renderNavbar(updated);
        })
        .catch(() => {});
    }

    function apiJson(url, options) {
      return fetch(url, options).then(res => res.json().then(data => {
        if (!res.ok) throw new Error(data.message || 'Không thể xử lý yêu cầu.');
        return data;
      }));
    }

    function loadRecommendedWorkouts(regenerate = false) {
      const session = ensureSession();
      if (!session) return;
      const query = new URLSearchParams({ userId: session.userId, regenerate });
      apiJson(`${window.location.origin}/api/workouts/recommend?${query}`)
        .then(data => {
          workoutPlan = data;
          document.getElementById('workoutMessage').textContent =
            `${data.fitnessLevel} · ${data.split} · ${data.workoutDaysPerWeek} buổi/tuần · ${data.intensity}. ${data.cardioNote} ${data.coolDownNote}`;
          renderWorkoutCards();
          renderProgress();
          return loadWorkoutHistory();
        })
        .catch(showError);
    }

    function loadWorkoutHistory() {
      const session = ensureSession();
      if (!session) return Promise.resolve();
      const query = new URLSearchParams({ userId: session.userId, date: getTodayKey() });
      return apiJson(`${window.location.origin}/api/workouts/history?${query}`)
        .then(data => {
          workoutHistory = data;
          renderWorkoutHistory();
        })
        .catch(showError);
    }

    function completeWorkout(detailId) {
      const session = ensureSession();
      if (!session) return;
      apiJson(`${window.location.origin}/api/workouts/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: session.userId, detailId })
      })
        .then(() => loadRecommendedWorkouts())
        .catch(showError);
    }

    function rateWorkout(detailId, rating) {
      const session = ensureSession();
      if (!session) return;
      apiJson(`${window.location.origin}/api/workouts/rate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: session.userId, detailId, rating })
      })
        .then(() => loadRecommendedWorkouts())
        .catch(showError);
    }

    function renderWorkoutCards() {
      const workouts = workoutPlan?.data || [];
      document.getElementById('workoutCards').innerHTML = workouts.map(workout => `
        <article class="card">
          <video class="workout-preview" autoplay muted loop playsinline preload="metadata"
                 onclick="openWorkoutDetails(${workout.detailId})">
            <source src="${escapeHtml(workout.videoUrl)}" type="video/mp4" />
          </video>
          <span class="pill">${escapeHtml(workout.level)}</span>
          <span class="pill phase">${escapeHtml(workout.phase)}</span>
          <div class="workout-title">${escapeHtml(workout.name)}</div>
          <div class="muted">Nhóm cơ: ${escapeHtml(workout.targetMuscle)}</div>
          <div class="muted">Dụng cụ: ${escapeHtml(workout.equipment)}</div>
          <div class="muted">Lý do phù hợp: ${escapeHtml(workout.suitability)}</div>
          <div class="muted">Khối lượng: ${escapeHtml(workout.sets)} hiệp × ${escapeHtml(workout.reps)}</div>
          <div class="muted">Nghỉ giữa hiệp: ${escapeHtml(workout.restSeconds)} giây</div>
          <div class="actions">
            <button class="btn-success" onclick="completeWorkout(${workout.detailId})" ${workout.completed ? 'disabled' : ''}>
              ${workout.completed ? 'Đã hoàn thành' : 'Hoàn thành'}
            </button>
            <button class="btn-light" onclick="openWorkoutDetails(${workout.detailId})">Xem hướng dẫn</button>
          </div>
          ${renderRating(workout)}
        </article>
      `).join('');
      playWorkoutPreviews();
    }

    function playWorkoutPreviews() {
      document.querySelectorAll('.workout-preview').forEach(video => {
        video.play().catch(() => {});
      });
    }

    function renderRating(workout) {
      if (!workout.completed) return '';
      if (workout.rating) return `<div class="muted" style="margin-top: 9px;">Đã đánh giá: ${workout.rating}★</div>`;
      return `<div class="rating" aria-label="Chấm điểm bài tập">
        ${[1, 2, 3, 4, 5].map(score =>
          `<button onclick="rateWorkout(${workout.detailId}, ${score})" title="${score} sao">${score}★</button>`
        ).join('')}
      </div>`;
    }

    function renderProgress() {
      const count = workoutPlan?.count || 0;
      const completed = workoutPlan?.completedCount || 0;
      const percent = count ? Math.round(completed / count * 100) : 0;
      document.getElementById('progressBar').style.width = `${percent}%`;
      document.getElementById('progressText').textContent =
        `${completed} / ${count} bài hoàn thành · ${percent}%`;
    }

    function renderWorkoutHistory() {
      const history = workoutHistory?.data || [];
      if (!history.length) {
        document.getElementById('workoutSummary').textContent = 'Chưa có bài tập hoàn thành hôm nay.';
        document.getElementById('workoutList').innerHTML = '<div class="muted">Không có dữ liệu</div>';
        return;
      }
      document.getElementById('workoutSummary').textContent =
        `${history.length} bài hoàn thành · ${workoutHistory.totalMinutes} phút · ${workoutHistory.totalCalories} kcal`;
      document.getElementById('workoutList').innerHTML = history.map(item => `
        <div class="list-item">
          ${escapeHtml(item.name)} · ${escapeHtml(item.targetMuscle)} · ${item.durationMinutes} phút · ${item.caloriesBurned} kcal
          ${item.rating ? ` · ${item.rating}★` : ''}
        </div>
      `).join('');
    }

    function openWorkoutDetails(detailId) {
      const workout = workoutPlan?.data?.find(item => item.detailId === detailId);
      if (!workout) return;
      const video = document.getElementById('modalVideo');
      video.src = workout.videoUrl;
      video.load();
      video.play().catch(() => {});
      document.getElementById('modalTitle').textContent = workout.name;
      document.getElementById('modalMeta').textContent =
        `${workout.targetMuscle} · ${workout.sets} hiệp × ${workout.reps} · nghỉ ${workout.restSeconds} giây`;
      document.getElementById('modalInstructions').textContent = workout.instructions || 'Chưa có hướng dẫn chi tiết.';
      document.getElementById('workoutOverlay').classList.add('open');
    }

    function closeWorkoutDetails(event) {
      if (event.target.id !== 'workoutOverlay') return;
      const video = document.getElementById('modalVideo');
      video.pause();
      video.removeAttribute('src');
      video.load();
      document.getElementById('workoutOverlay').classList.remove('open');
    }

    function showError(error) {
      document.getElementById('workoutMessage').textContent = error.message;
    }

    function escapeHtml(value) {
      return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }

    const session = ensureSession();
    if (session) {
      showSafetyGate('workout', session.userId);
      hydrateSessionUser(session);
      loadRecommendedWorkouts();
    }
