UPDATE l
SET
    l.Interaction_type = N'Food_Survey',
    l.Is_completed = COALESCE(l.Is_completed, 1),
    l.Meal_Time = COALESCE(l.Meal_Time, f.Meal_type, N'Unknown'),
    l.Log_date = COALESCE(l.Log_date, SYSDATETIME())
FROM dbo.Interaction_Logs l
JOIN dbo.Food_Library f ON f.FoodID = l.FoodID
WHERE l.FoodID IS NOT NULL
  AND l.Rating IS NOT NULL
  AND (l.Interaction_type IS NULL OR l.Interaction_type <> N'Food_Survey');

UPDATE l
SET
    l.Meal_Time = COALESCE(NULLIF(LTRIM(RTRIM(f.Meal_type)), N''), N'Unknown'),
    l.Is_completed = COALESCE(l.Is_completed, 1),
    l.Log_date = COALESCE(l.Log_date, SYSDATETIME())
FROM dbo.Interaction_Logs l
LEFT JOIN dbo.Food_Library f ON f.FoodID = l.FoodID
WHERE l.FoodID IS NOT NULL
  AND (l.Meal_Time IS NULL OR LTRIM(RTRIM(l.Meal_Time)) = N'');

UPDATE dbo.Interaction_Logs
SET
    Interaction_type = N'Excercise_Survey',
    Is_completed = COALESCE(Is_completed, 1),
    Log_date = COALESCE(Log_date, SYSDATETIME())
WHERE ExerciseID IS NOT NULL
  AND Rating IS NOT NULL
  AND (Interaction_type IS NULL OR Interaction_type <> N'Excercise_Survey');

UPDATE dbo.Interaction_Logs
SET
    Is_completed = COALESCE(Is_completed, 1),
    Log_date = COALESCE(Log_date, SYSDATETIME())
WHERE ExerciseID IS NOT NULL
  AND Interaction_type = N'COMPLETED';

UPDATE dp
SET dp.Total_target_calories = hp.Target_Calories
FROM dbo.Daily_Plans dp
CROSS APPLY (
    SELECT TOP 1 Target_Calories
    FROM dbo.Health_Profiles hp
    WHERE hp.UserID = dp.UserID
    ORDER BY hp.Last_updated DESC, hp.ProfileID DESC
) hp
WHERE dp.Total_target_calories IS NULL
  AND hp.Target_Calories IS NOT NULL;
