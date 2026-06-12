let latestFoods = [];
    let selectedFoodId = null;
    let selectedDetailId = null;
    let selectedRating = 5;
    let currentProfile = null;
    let ratedFoodIdsInCurrentSuggestion = new Set();

    function getValue(id) { return document.getElementById(id).value; }
    function baseUrl() { return window.location.origin; }

    function getSession() {
      const raw = localStorage.getItem('session');
      return raw ? JSON.parse(raw) : null;
    }

    function getProfile(userId) {
      if (currentProfile && Number(currentProfile.userId) === Number(userId)) return currentProfile;
      const raw = localStorage.getItem(`profile_${userId}`);
      return raw ? JSON.parse(raw) : null;
    }

    function setProfile(userId, profile) {
      currentProfile = profile;
      localStorage.setItem(`profile_${userId}`, JSON.stringify(profile));
      fillProfileForm(profile);
    }

    function fillProfileForm(profile) {
      if (!profile) return;
      document.getElementById('weight').value = profile.weight ?? '';
      document.getElementById('height').value = profile.height ?? '';
      document.getElementById('age').value = profile.age ?? '';
      document.getElementById('gender').value = profile.gender ?? 'male';
      document.getElementById('activityLevel').value = profile.activityLevel ?? '1.55';
      document.getElementById('healthGoal').value = profile.healthGoal ?? 'maintain';
      document.getElementById('fitnessLevel').value = profile.fitnessLevel ?? 'Beginner';
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
          if (profile) {
            setProfile(userId, profile);
          }
          return profile;
        })
        .catch(() => getProfile(userId));
    }

    function ensureSession() {
      const session = getSession();
      if (!session) {
        window.location.href = 'index.html';
        return null;
      }
      return session;
    }

    function updateSessionInfo() {
      const session = getSession();
      document.getElementById('navUser').textContent = session?.userName || '';
      document.getElementById('navLogin').style.display = session ? 'none' : '';
      document.getElementById('navLogout').style.display = session ? '' : 'none';
    }

    function hydrateSessionUser(session) {
      updateSessionInfo();
      if (!session || session.userName) return;
      fetch(`${baseUrl()}/api/auth/users/${session.userId}`)
        .then(res => res.ok ? res.json() : null)
        .then(user => {
          if (!user) return;
          localStorage.setItem('session', JSON.stringify({
            ...session,
            userName: user.userName,
            role: user.role || session.role
          }));
          updateSessionInfo();
        })
        .catch(() => {});
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

    function showPage(pageId) {
      const pages = ['page-survey', 'page-onboarding', 'page-dashboard', 'page-admin'];
      pages.forEach(id => {
        document.getElementById(id).classList.toggle('hidden', id !== pageId);
      });
    }

    function logout() {
      localStorage.removeItem('session');
      window.location.href = 'index.html';
    }

    function goToOverview() {
      window.location.href = 'overview.html';
    }

    function goToWorkout() {
      window.location.href = 'workout.html';
    }

    function routeAfterLogin(session) {
      if (!session) return;
      if (session.role === 'admin') {
        showPage('page-admin');
        loadAdminFoods();
        return;
      }

      fetchProfile(session.userId).then(profile => {
        if (!profile) {
          showPage('page-survey');
        } else {
          showPage('page-dashboard');
          refreshDashboard();
        }
      });
    }

    function handleHashRoute() {
      if (window.location.hash === '#survey') {
        showPage('page-survey');
      }
      if (window.location.hash === '#admin') {
        showPage('page-admin');
        loadAdminFoods();
      }
    }

    function saveProfile() {
      const session = ensureSession();
      if (!session) return;
      const payload = {
        userId: session.userId,
        weight: parseFloat(getValue('weight')) || 0,
        height: parseFloat(getValue('height')) || 0,
        age: parseInt(getValue('age'), 10) || 0,
        gender: getValue('gender'),
        activityLevel: parseFloat(getValue('activityLevel')) || 0,
        healthGoal: getValue('healthGoal'),
        fitnessLevel: getValue('fitnessLevel')
      };
      fetch(`${baseUrl()}/api/health-profiles`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })
        .then(res => res.json().then(data => {
          if (!res.ok) {
            throw new Error(data.message || 'Không thể lưu hồ sơ.');
          }
          return data;
        }))
        .then(profile => {
          setProfile(session.userId, profile);
          document.getElementById('surveyMsg').textContent = 'Đã lưu hồ sơ (server).';
          showPage('page-onboarding');
          loadPopularFoods();
        })
        .catch(err => {
          document.getElementById('surveyMsg').textContent = err.message;
        });
    }

    function goToSurvey() {
      showPage('page-survey');
    }

    function refreshTdee() {
      goToSurvey();
    }

    function goToDashboard() {
      showPage('page-dashboard');
      refreshDashboard();
    }

    function logAction(message) {
      const log = document.getElementById('actionLog');
      log.value = `[${new Date().toLocaleTimeString()}] ${message}\n` + log.value;
    }

    function calculateTargetCalories() {
      const profile = currentProfile || {};
      const savedTarget = parseFloat(profile.targetCalories);
      if (Number.isFinite(savedTarget)) return Math.round(savedTarget);
      const weight = parseFloat(profile.weight ?? getValue('weight')) || 0;
      const height = parseFloat(profile.height ?? getValue('height')) || 0;
      const age = parseFloat(profile.age ?? getValue('age')) || 0;
      const gender = profile.gender ?? getValue('gender');
      const activity = parseFloat(profile.activityLevel ?? getValue('activityLevel')) || 1;

      const bmr = gender === 'male'
        ? (10 * weight) + (6.25 * height) - (5 * age) + 5
        : (10 * weight) + (6.25 * height) - (5 * age) - 161;
      return Math.round(bmr * activity);
    }

    function loadPopularFoods() {
      const url = `${baseUrl()}/api/recommend-popular?topK=20`;
      fetch(url)
        .then(res => res.json())
        .then(data => {
          const foods = data.data || [];
          const container = document.getElementById('popularFoods');
          container.innerHTML = foods.map(food => `
            <div class="food-item">
              ${renderFoodImage(food)}
              <label>
                <input type="checkbox" class="favFood" value="${food.id}" />
                <span class="food-title">${food.name}</span>
              </label>
              <div class="food-meta">${food.category} • ${food.calories} kcal</div>
            </div>
          `).join('');
        })
        .catch(err => {
          document.getElementById('onboardMsg').textContent = err.message;
        });
    }

    function saveFavorites() {
      const session = ensureSession();
      if (!session) return;
      const userId = session.userId;
      const selected = Array.from(document.querySelectorAll('.favFood:checked'))
        .map(el => el.value);
      if (selected.length < 3) {
        document.getElementById('onboardMsg').textContent = 'Vui lòng chọn ít nhất 3 món.';
        return;
      }

      fetch(`${baseUrl()}/api/nutrition/favorites`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, foodIds: selected.map(Number), rating: 5 })
      })
        .then(res => res.json().then(data => {
          if (!res.ok) {
            throw new Error(data.error || 'Không thể lưu món yêu thích.');
          }
          return data;
        }))
        .then(() => {
          document.getElementById('onboardMsg').textContent = 'Đã lưu ưa thích thành Rating=5.';
          logAction('Onboarding: lưu món ăn yêu thích thành Rating=5');
          showPage('page-dashboard');
          refreshDashboard();
        })
        .catch(err => {
          document.getElementById('onboardMsg').textContent = err.message;
        });
    }

    function refreshDashboard(regenerate = false) {
      const session = ensureSession();
      if (!session) return;
      const userId = session.userId;
      const url = `${baseUrl()}/api/nutrition/plan?userId=${userId}&regenerate=${regenerate}`;
      fetch(url)
        .then(res => res.json().then(data => {
          if (!res.ok) {
            throw new Error(data.error || 'Không thể xử lý yêu cầu.');
          }
          return data;
        }))
        .then(data => {
          latestFoods = data.data || [];
          ratedFoodIdsInCurrentSuggestion = new Set();
          if (data.meals) {
            renderMealGroups(data.meals);
          } else {
            renderMeals(latestFoods);
          }
          renderActionFoods(latestFoods);
          renderCalories(latestFoods);
          document.getElementById('dashMsg').textContent = `Đã tải lên ${latestFoods.length} món.`;
        })
        .catch(err => {
          document.getElementById('dashMsg').textContent = err.message;
        });
    }

    function renderMealGroups(meals) {
      const breakfast = meals.breakfast || [];
      const lunch = meals.lunch || [];
      const dinner = meals.dinner || [];

      document.getElementById('breakfastList').innerHTML = renderFoodCards(breakfast);
      document.getElementById('lunchList').innerHTML = renderFoodCards(lunch);
      document.getElementById('dinnerList').innerHTML = renderFoodCards(dinner);
    }

    function renderMeals(foods) {
      const breakfast = foods.filter(f => (f.mealType || '').toLowerCase().includes('breakfast'));
      const lunch = foods.filter(f => (f.mealType || '').toLowerCase().includes('lunch'));
      const dinner = foods.filter(f => (f.mealType || '').toLowerCase().includes('dinner'));

      document.getElementById('breakfastList').innerHTML = renderFoodCards(breakfast);
      document.getElementById('lunchList').innerHTML = renderFoodCards(lunch);
      document.getElementById('dinnerList').innerHTML = renderFoodCards(dinner);
    }

    function renderFoodCards(list) {
      if (!list.length) return '<div class="muted">Không có dữ liệu</div>';
      return list.slice(0, 3).map(food => `
        <div class="food-item">
          ${renderFoodImage(food)}
          <div class="food-title">${food.name}</div>
          <div class="food-meta">${formatFoodMeta(food)}</div>
        </div>
      `).join('');
    }

    const FOOD_IMAGE_EXTENSIONS = ['webp', 'png', 'jpg', 'jpeg'];

    function foodImageUrl(foodId, extensionIndex) {
      return `/images/foods/${encodeURIComponent(foodId)}.${FOOD_IMAGE_EXTENSIONS[extensionIndex]}`;
    }

    function renderFoodImage(food) {
      return `<img class="food-image" src="${foodImageUrl(food.id, 0)}" data-food-id="${food.id}" data-extension-index="0" data-food-name="${escapeHtmlAttribute(food.name)}" data-food-meta="${escapeHtmlAttribute(formatFoodMeta(food))}" onerror="loadNextFoodImage(this)" onclick="openFoodDetails(this)" alt="${escapeHtmlAttribute(food.name)}" loading="lazy" />`;
    }

    function loadNextFoodImage(image) {
      const nextIndex = Number(image.dataset.extensionIndex) + 1;
      if (nextIndex < FOOD_IMAGE_EXTENSIONS.length) {
        image.dataset.extensionIndex = String(nextIndex);
        image.src = foodImageUrl(image.dataset.foodId, nextIndex);
        return;
      }
      image.onerror = null;
      image.style.display = 'none';
    }

    function escapeHtmlAttribute(value) {
      return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
    }

    function openFoodDetails(image) {
      document.getElementById('foodDetailsImage').src = image.currentSrc || image.src;
      document.getElementById('foodDetailsName').textContent = image.dataset.foodName;
      document.getElementById('foodDetailsMeta').textContent = image.dataset.foodMeta;
      document.getElementById('foodDetailsOverlay').style.display = 'flex';
    }

    function closeFoodDetails(event) {
      if (event && event.target !== event.currentTarget) return;
      document.getElementById('foodDetailsOverlay').style.display = 'none';
    }

    function formatFoodMeta(food) {
      const portionMultiplier = parseFloat(food.portionMultiplier);
      const portionText = Number.isFinite(portionMultiplier)
        ? ` • ${portionMultiplier} ${formatServingUnit(food.servingUnit)}`
        : '';
      return `${food.category} • ${food.calories} kcal${portionText}`;
    }

    function formatServingUnit(servingUnit) {
      const normalized = String(servingUnit ?? '').trim();
      return normalized ? normalized.toLocaleLowerCase('vi-VN') : 'khẩu phần';
    }

    function renderCalories(foods) {
      const total = foods.reduce((sum, f) => sum + (f.calories || 0), 0);
      const target = calculateTargetCalories();
      const percent = target ? Math.min(100, Math.round((total / target) * 100)) : 0;
      document.getElementById('caloBar').style.width = `${percent}%`;
      document.getElementById('caloText').textContent = `Calo thực đơn gợi ý: ${Math.round(total)} kcal / Mục tiêu: ${target} kcal (${percent}%)`;
    }

    function renderActionFoods(foods) {
      const container = document.getElementById('actionFoods');
      if (!foods.length) {
        container.innerHTML = '<div class="muted">Hãy tải gợi ý từ Dashboar trước.</div>';
        return;
      }
      const foodsToRate = foods.filter(food =>
        !food.completed && !food.rating && !ratedFoodIdsInCurrentSuggestion.has(String(food.detailId || food.id))
      );
      if (!foodsToRate.length) {
        container.innerHTML = '<div class="muted">Tất cả các món trong lượt gợi ý này đã được chấm điểm.</div>';
        return;
      }
      container.innerHTML = foodsToRate.map(food => `
        <div class="food-item">
          ${renderFoodImage(food)}
          <div class="food-title">${food.name}</div>
          <div class="food-meta">${formatFoodMeta(food)}</div>
          <div class="actions">
            <button class="btn-success" onclick="openRating(${food.id}, '${food.name.replace(/'/g, "\\'")}')">Chấm điểm yêu thích</button>
          </div>
        </div>
      `).join('');
    }

    function openRating(foodId, detailId, name) {
      if (name === undefined) {
        name = detailId;
        detailId = null;
      }
      selectedFoodId = foodId;
      selectedDetailId = detailId;
      selectedRating = 5;
      document.getElementById('ratingFoodName').textContent = name;
      const stars = document.getElementById('ratingStars');
      stars.innerHTML = '';
      for (let i = 1; i <= 5; i++) {
        const span = document.createElement('span');
        span.className = 'star' + (i <= selectedRating ? ' active' : '');
        span.textContent = '★';
        span.onclick = () => setRating(i);
        stars.appendChild(span);
      }
      document.getElementById('ratingOverlay').style.display = 'flex';
    }

    function setRating(value) {
      selectedRating = value;
      const stars = document.querySelectorAll('#ratingStars .star');
      stars.forEach((star, idx) => {
        star.classList.toggle('active', idx < value);
      });
    }

    function closeRating() {
      document.getElementById('ratingOverlay').style.display = 'none';
    }

    function submitRating() {
      const session = ensureSession();
      if (!session) return;
      const userId = session.userId;
      const food = latestFoods.find(item => item.id === selectedFoodId);
      if (!food) return;
      const url = `${baseUrl()}/api/nutrition/rate`;
      fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId,
          detailId: selectedDetailId || food.detailId,
          foodId: selectedFoodId,
          rating: selectedRating
        })
      })
        .then(res => res.json())
        .then(data => {
          if (data.error) throw new Error(data.error);
          food.completed = true;
          food.rating = selectedRating;
          food.completedAt = new Date().toISOString();
          ratedFoodIdsInCurrentSuggestion.add(String(food.detailId || food.id));
          logAction(`Đã hoàn thành và chấm điểm ${selectedRating}★ cho foodId=${selectedFoodId}`);
          closeRating();
          renderActionFoods(latestFoods);
        })
        .catch(err => {
          logAction(`Lỗi: ${err.message}`);
        });
    }

    function loadAdminFoods() {
      const url = `${baseUrl()}/api/admin/foods`;
      fetch(url)
        .then(res => res.json())
        .then(data => {
          const rows = data.map(food => `
            <tr>
              <td>${food.id}</td>
              <td>${food.name}</td>
              <td>${food.calories}</td>
              <td>${food.category}</td>
              <td><button onclick="selectFood(${food.id})">Chọn</button></td>
            </tr>
          `).join('');
          document.getElementById('adminFoodTable').innerHTML = rows;
        })
        .catch(err => { document.getElementById('adminMsg').textContent = err.message; });
    }

    function selectFood(id) {
      const url = `${baseUrl()}/api/admin/foods/${id}`;
      fetch(url)
        .then(res => res.json())
        .then(food => {
          document.getElementById('adminName').value = food.name || '';
          document.getElementById('adminCalories').value = food.calories || '';
          document.getElementById('adminProtein').value = food.protein || '';
          document.getElementById('adminCarbs').value = food.carbs || '';
          document.getElementById('adminFat').value = food.fat || '';
          document.getElementById('adminUnit').value = food.servingUnit || '';
          document.getElementById('adminMealType').value = food.mealType || '';
          document.getElementById('adminCategory').value = food.category || 'Món tinh bột';
          document.getElementById('adminMsg').textContent = `Dang chon foodId=${food.id}`;
          document.getElementById('adminMsg').dataset.selectedId = food.id;
        });
    }

    function buildFoodPayload() {
      return {
        name: getValue('adminName'),
        calories: parseFloat(getValue('adminCalories')) || 0,
        protein: parseFloat(getValue('adminProtein')) || 0,
        carbs: parseFloat(getValue('adminCarbs')) || 0,
        fat: parseFloat(getValue('adminFat')) || 0,
        servingUnit: getValue('adminUnit'),
        mealType: getValue('adminMealType'),
        category: getValue('adminCategory')
      };
    }

    function createFood() {
      const url = `${baseUrl()}/api/admin/foods`;
      fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildFoodPayload())
      })
        .then(res => res.json())
        .then(() => { document.getElementById('adminMsg').textContent = 'Đã thêm món.'; loadAdminFoods(); })
        .catch(err => { document.getElementById('adminMsg').textContent = err.message; });
    }

    function updateFood() {
      const selectedId = document.getElementById('adminMsg').dataset.selectedId;
      if (!selectedId) { document.getElementById('adminMsg').textContent = 'Chưa chọn món để cập nhật.'; return; }
      const url = `${baseUrl()}/api/admin/foods/${selectedId}`;
      fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildFoodPayload())
      })
        .then(res => res.json())
        .then(() => { document.getElementById('adminMsg').textContent = 'Đã cập nhật món.'; loadAdminFoods(); })
        .catch(err => { document.getElementById('adminMsg').textContent = err.message; });
    }

    function deleteFood() {
      const selectedId = document.getElementById('adminMsg').dataset.selectedId;
      if (!selectedId) { document.getElementById('adminMsg').textContent = 'Chưa chọn món để xóa.'; return; }
      const url = `${baseUrl()}/api/admin/foods/${selectedId}`;
      fetch(url, { method: 'DELETE' })
        .then(res => res.json())
        .then(() => { document.getElementById('adminMsg').textContent = 'Đã xóa món.'; loadAdminFoods(); })
        .catch(err => { document.getElementById('adminMsg').textContent = err.message; });
    }

    const session = ensureSession();
    if (session) {
      showSafetyGate('nutrition', session.userId);
      hydrateSessionUser(session);
      routeAfterLogin(session);
      handleHashRoute();
    }
