function getValue(id) {
      return document.getElementById(id).value;
    }

    function buildUrl(path, params) {
      const baseUrl = getValue('baseUrl');
      const url = new URL(baseUrl + path);
      Object.keys(params).forEach((key) => {
        const value = params[key];
        if (value !== null && value !== undefined && value !== '') {
          url.searchParams.set(key, value);
        }
      });
      return url.toString();
    }

    async function request(url, method, body) {
      const output = document.getElementById('output');
      output.value = 'Loading...';
      try {
        const options = body
          ? { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
          : { method };
        const response = await fetch(url, options);
        const text = await response.text();
        output.value = text || '<empty response>';
      } catch (err) {
        output.value = err.message;
      }
    }

    function callEndpoint(type) {
      const userId = getValue('userId');
      const topK = getValue('topK');
      const userWeight = getValue('userWeight');
      const weight = getValue('weight');
      const height = getValue('height');
      const age = getValue('age');
      const gender = getValue('gender');
      const activityLevel = getValue('activityLevel');
      const healthGoal = getValue('healthGoal');
      const isTraditional = getValue('isTraditional');
      const foodId = getValue('foodId');
      const rating = getValue('rating');

      let url = '';
      let method = 'GET';

      switch (type) {
        case 'recommend-cf':
          url = buildUrl('/api/recommend-cf', { userId, topK });
          break;
        case 'recommend-cf-item':
          url = buildUrl('/api/recommend-cf-item', { userId, topK });
          break;
        case 'recommend-cf-hybrid':
          url = buildUrl('/api/recommend-cf-hybrid', { userId, topK, userWeight });
          break;
        case 'recommend-tdee':
          url = buildUrl('/api/recommend-tdee', { weight, height, age, gender, activityLevel, healthGoal, isTraditional });
          break;
        case 'recommend-popular':
          url = buildUrl('/api/recommend-popular', { topK });
          break;
        case 'recommend-smart-user':
          url = buildUrl('/api/recommend-smart', { userId, topK });
          break;
        case 'recommend-smart-profile':
          url = buildUrl('/api/recommend-smart', { weight, height, age, gender, activityLevel, healthGoal, topK, isTraditional });
          break;
        case 'onboard':
          url = buildUrl('/api/onboard/initial-recommendation', { weight, height, age, gender, activityLevel, healthGoal, topK: 10, isTraditional });
          break;
        case 'suggest-after-rating':
          url = buildUrl('/api/suggest-after-rating', { userId, topK });
          break;
        case 'user-status':
          url = buildUrl('/api/user-status/' + encodeURIComponent(userId), {});
          break;
        case 'user-ratings':
          url = buildUrl('/api/user-ratings/' + encodeURIComponent(userId), {});
          break;
        case 'debug-cf-status':
          url = buildUrl('/api/debug/cf-status', { userId });
          break;
        case 'debug-cf-detailed':
          url = buildUrl('/api/debug/cf-detailed', { userId });
          break;
        case 'nutrition-favorites':
          url = buildUrl('/api/nutrition/favorites', {});
          method = 'POST';
          request(url, method, { userId: Number(userId), foodIds: [Number(foodId)], rating: Number(rating) });
          return;
          break;
        default:
          return;
      }

      request(url, method);
    }
