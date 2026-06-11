ALTER TABLE dbo.Exercise_Library ALTER COLUMN [Name] NVARCHAR(255) NOT NULL;
ALTER TABLE dbo.Exercise_Library ALTER COLUMN [Target_muscle] NVARCHAR(255) NULL;
ALTER TABLE dbo.Exercise_Library ALTER COLUMN [Type] NVARCHAR(255) NULL;
ALTER TABLE dbo.Exercise_Library ALTER COLUMN [Level] NVARCHAR(255) NULL;
ALTER TABLE dbo.Exercise_Library ALTER COLUMN [Goal_Tag] NVARCHAR(255) NULL;
ALTER TABLE dbo.Exercise_Library ALTER COLUMN [Equipment] NVARCHAR(255) NULL;
ALTER TABLE dbo.Plan_Details ALTER COLUMN [Reps] NVARCHAR(255) NULL;
ALTER TABLE dbo.Plan_Details ALTER COLUMN [Workout_Phase] NVARCHAR(255) NULL;

UPDATE exercise
SET exercise.[Name] = restored.[Name],
    exercise.[Target_muscle] = restored.[Target_muscle],
    exercise.[Equipment] = restored.[Equipment]
FROM dbo.Exercise_Library exercise
JOIN (VALUES
    (1,  N'Đạp xe tập (Stationary Bike)', N'Toàn thân, Cơ mông, Cơ đùi', N'Máy xe đạp'),
    (2,  N'Nhảy dây (Jump Rope)', N'Toàn thân, Cơ chân', N'Dây nhảy'),
    (3,  N'Butt Kick', N'Toàn thân, Cơ đùi sau', N'Không'),
    (4,  N'Hít đất (Push-up)', N'Ngực', N'Cân nặng cơ thể'),
    (5,  N'Đẩy tạ đòn ghế phẳng (Bench Press)', N'Ngực', N'Tạ đòn'),
    (6,  N'Ép ngực trên máy (Machine Chest Press)', N'Ngực', N'Máy ép ngực'),
    (7,  N'Kéo xà máy (Machine Pulldown)', N'Lưng (Lat)', N'Máy kéo xà'),
    (8,  N'Kéo cáp ngang (Cable Lat Prayer)', N'Lưng (Lat)', N'Máy kéo cáp'),
    (9,  N'Hít xà đơn (Pull-ups)', N'Lưng (Trap)', N'Cân nặng cơ thể'),
    (10, N'Đẩy tạ đơn qua đầu (Overhead Press)', N'Vai', N'Tạ đơn'),
    (11, N'Nâng tạ hai bên (Lateral Raise)', N'Vai', N'Tạ đơn'),
    (12, N'Kéo máy ngược (Machine Reverse Fly)', N'Vai', N'Máy kéo cáp ngược'),
    (13, N'Cuốn tạ tay trước (Dumbbell Curl)', N'Tay trước', N'Tạ đơn'),
    (14, N'Cuốn cáp xoắn (Cable Twisting Curl)', N'Tay trước', N'Máy kéo cáp'),
    (15, N'Cuốn tạ đòn tay trước (Barbell Curl)', N'Tay trước', N'Tạ đòn'),
    (16, N'Nâng tạ đơn qua đầu (Dumbbell Overhead Tricep Extension)', N'Tay sau', N'Tạ đơn'),
    (17, N'Đẩy cáp bằng dây thừng (Cable Rope Pushdown)', N'Tay sau', N'Máy cáp'),
    (18, N'Nằm đẩy tạ đòn tay cầm hẹp (Barbell Close Grip Bench Press)', N'Tay sau', N'Tạ đòn'),
    (19, N'Hít xà ngửa tay (Chin Ups)', N'Cẳng tay', N'Cân nặng cơ thể'),
    (20, N'Chèo tạ đơn một bên (Dumbbell Row Unilateral)', N'Cẳng tay', N'Tạ đơn'),
    (21, N'Cuốn cổ tay với tạ đơn (Dumbbell Wrist Curl)', N'Cẳng tay', N'Tạ đơn'),
    (22, N'Gánh tạ đòn (Barbell Squat)', N'Cơ mông', N'Tạ đòn'),
    (23, N'Gánh tạ đơn Goblet (Dumbbell Goblet Squat) - Mông', N'Cơ mông', N'Tạ đơn'),
    (24, N'Squat trọng lượng cơ thể (Bodyweight Squat)', N'Cơ mông', N'Cân nặng cơ thể'),
    (25, N'Đạp đùi bằng máy (Machine Leg Press)', N'Cơ mông, Cơ đùi trước', N'Máy đạp đùi'),
    (26, N'Đá đùi trước bằng máy (Machine Leg Extension)', N'Đùi trước', N'Máy đá đùi'),
    (27, N'Bước lên bậc với tạ ấm (Kettlebell Step Up)', N'Đùi trước', N'Tạ ấm'),
    (28, N'Cuốn đùi sau bằng máy (Machine Hamstring Curl)', N'Đùi sau', N'Máy cuốn đùi'),
    (29, N'Cúi người chào buổi sáng với tạ đơn Goblet (Dumbbell Goblet Good Morning)', N'Đùi sau', N'Tạ đơn'),
    (30, N'Nhón bắp chân đứng bằng máy (Machine Standing Calf Raises)', N'Bắp chân', N'Máy nhón bắp chân đứng'),
    (31, N'Nhón bắp chân ngồi bằng máy (Machine Seated Calf Raises)', N'Bắp chân', N'Máy nhón bắp chân ngồi'),
    (32, N'Nhón bắp chân với tạ đòn (Barbell Calf Raises)', N'Bắp chân', N'Tạ đòn')
) restored([ExerciseID], [Name], [Target_muscle], [Equipment])
    ON restored.[ExerciseID] = exercise.[ExerciseID];

UPDATE dbo.Plan_Details
SET [Workout_Phase] = N'KHỞI ĐỘNG'
WHERE [Item_type] = 'EXERCISE'
  AND [Sort_Order] = 1
  AND ([Workout_Phase] LIKE N'%?%' OR [Workout_Phase] LIKE N'%Ð%');

UPDATE dbo.Plan_Details
SET [Workout_Phase] = N'BÀI PHỤ'
WHERE [Item_type] = 'EXERCISE'
  AND [Workout_Phase] LIKE N'BÀI PH%';
