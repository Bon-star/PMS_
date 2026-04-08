USE master;
GO

-- 1. XÓA VÀ TẠO MỚI DATABASE
IF DB_ID('PMS') IS NOT NULL
BEGIN
    ALTER DATABASE PMS SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE PMS;
END
GO

CREATE DATABASE PMS;
GO

USE PMS;
GO

-- 2. TẠO CÁC BẢNG CƠ SỞ
CREATE TABLE [classes] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [name] varchar(50) NOT NULL,
  [quantity] int,
  [start_date] datetime,
  [end_date] datetime
)
GO

CREATE TABLE [users] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [code] varchar(20) UNIQUE,
  [email] varchar(100) UNIQUE NOT NULL,
  [password] varchar(255) NOT NULL,
  [full_name] nvarchar(100) NOT NULL,
  [phone] varchar(15) UNIQUE,
  [role] tinyint NOT NULL, -- 0: ADMIN, 1: STUDENT, 2: STAFF, 3: LECTURER
  [status] tinyint DEFAULT (1),
  [class_id] int REFERENCES [classes]([id]),
  [created_date] datetime DEFAULT GETDATE()
)
GO

CREATE TABLE [semesters] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [name] varchar(50),
  [start_date] datetime,
  [end_date] datetime
)
GO

CREATE TABLE [groups] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [name] varchar(100) NOT NULL,
  [class_id] int REFERENCES [classes]([id]),
  [semester_id] int REFERENCES [semesters]([id]),
  [status] tinyint,
  [created_at] datetime DEFAULT GETDATE()
)
GO

CREATE TABLE [group_members] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [group_id] int NOT NULL REFERENCES [groups]([id]),
  [user_id] int NOT NULL REFERENCES [users]([id]),
  [role] varchar(20) DEFAULT 'MEMBER',
  [score] float,
  [joined_at] datetime DEFAULT GETDATE(),
  [status] tinyint DEFAULT (1)
)
GO

CREATE TABLE [projects] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [group_id] int NOT NULL REFERENCES [groups]([id]),
  [name] varchar(200),
  [detail] varchar(500),
  [file_url] varchar(500),
  [git_src] varchar(500),
  [status] tinyint DEFAULT (0),
  [start_date] datetime DEFAULT GETDATE(),
  [end_date] datetime
)
GO

CREATE TABLE [sprints] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [project_id] int NOT NULL REFERENCES [projects]([id]),
  [name] varchar(100),
  [status] tinyint DEFAULT (0),
  [start_date] datetime DEFAULT GETDATE(),
  [end_date] datetime
)
GO

CREATE TABLE [tasks] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [project_id] int NOT NULL REFERENCES [projects]([id]),
  [sprint_id] int REFERENCES [sprints]([id]),
  [name] varchar(200),
  [description] varchar(500),
  [image_url] varchar(500),
  [points] float,
  [status] tinyint DEFAULT (0),
  [assigned_by] int REFERENCES [users]([id]),
  [assigned_to] int REFERENCES [users]([id]),
  [priority] int,
  [created_at] datetime DEFAULT GETDATE(),
  [updated_at] datetime,
  [start_date] datetime,
  [due_date] datetime
)
GO

CREATE TABLE [sub_task] (
  [id] int PRIMARY KEY,
  [task_id] int REFERENCES [tasks]([id]),
  [status] tinyint,
  [detail] varchar(200)
)
GO

CREATE TABLE [task_comments] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [task_id] int NOT NULL REFERENCES [tasks]([id]),
  [user_id] int NOT NULL REFERENCES [users]([id]),
  [content] varchar(500),
  [created_at] datetime DEFAULT GETDATE()
)
GO

CREATE TABLE [templates] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [name] varchar(200),
  [detail] varchar(500),
  [source] varchar(50),
  [file_url] varchar(500),
  [status] tinyint,
  [semester_id] int REFERENCES [semesters]([id]),
  [author_id] int REFERENCES [users]([id]),
  [created_at] datetime DEFAULT GETDATE(),
  [version] int
)
GO

CREATE TABLE [notification] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [type] nvarchar(50),
  [recipient_id] int REFERENCES [users]([id]),
  [sender_id] int REFERENCES [users]([id]),
  [target_id] int,
  [target_type] nvarchar(200),
  [detail] nvarchar(max),
  [status] tinyint, -- 1: unread, 2: read, 3: actioned
  [date] datetime DEFAULT GETDATE()
)
GO

CREATE TABLE [group_invitations] (
  [id] int PRIMARY KEY IDENTITY(1, 1),
  [group_id] int REFERENCES [groups]([id]),
  [inviter_id] int REFERENCES [users]([id]),
  [invited_id] int REFERENCES [users]([id]),
  [status] tinyint DEFAULT 0, -- 0: pending, 1: accept, 2: deny
  [date_create] datetime DEFAULT GETDATE(),
  [date_respond] datetime
)
GO

-- 3. PHÂN QUYỀN VÀ ROLE
CREATE ROLE db_student;
CREATE ROLE db_lecturer;
CREATE ROLE db_staff;
CREATE ROLE db_admin;
GO

-- Gán quyền cơ bản
GRANT SELECT ON [classes] TO db_student;
GRANT SELECT ON [semesters] TO db_student;
GRANT SELECT, INSERT, UPDATE, DELETE ON [templates] TO db_lecturer;
GRANT SELECT, INSERT, UPDATE, DELETE ON [templates] TO db_staff;
GO

-- 4. THIẾT LẬP SECURITY (RLS)
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'Security')
    EXEC('CREATE SCHEMA Security');
GO

-- A. Hàm bảo mật cho bảng liên quan đến Group (Groups, Group_Members, Projects)
CREATE OR ALTER FUNCTION Security.fn_GroupPredicate(@group_id AS int)
    RETURNS TABLE
WITH SCHEMABINDING
AS
    RETURN SELECT 1 AS fn_relresults
    WHERE 
        IS_MEMBER('db_admin') = 1 OR IS_MEMBER('db_lecturer') = 1 OR IS_MEMBER('db_staff') = 1
        OR EXISTS (
            SELECT 1 FROM dbo.group_members gm
            INNER JOIN dbo.users u ON gm.user_id = u.id
            WHERE gm.group_id = @group_id 
            AND u.email = USER_NAME()
        );
GO

-- B. Hàm bảo mật cho bảng liên quan đến Project (Tasks, Sprints)
-- Vì bảng tasks không có group_id, ta phải join qua bảng projects
CREATE OR ALTER FUNCTION Security.fn_ProjectPredicate(@project_id AS int)
    RETURNS TABLE
WITH SCHEMABINDING
AS
    RETURN SELECT 1 AS fn_relresults
    WHERE 
        IS_MEMBER('db_admin') = 1 OR IS_MEMBER('db_lecturer') = 1 OR IS_MEMBER('db_staff') = 1
        OR EXISTS (
            SELECT 1 FROM dbo.projects p
            INNER JOIN dbo.group_members gm ON p.group_id = gm.group_id
            INNER JOIN dbo.users u ON gm.user_id = u.id
            WHERE p.id = @project_id 
            AND u.email = USER_NAME()
        );
GO

-- C. Hàm bảo mật cho bảng Notification (Chỉ người nhận mới thấy)
CREATE OR ALTER FUNCTION Security.fn_NotificationPredicate(@recipient_id AS int)
    RETURNS TABLE
WITH SCHEMABINDING
AS
    RETURN SELECT 1 AS fn_relresults
    WHERE 
        IS_MEMBER('db_admin') = 1 
        OR EXISTS (
            SELECT 1 FROM dbo.users u 
            WHERE u.id = @recipient_id AND u.email = USER_NAME()
        );
GO

-- 5. ÁP DỤNG CHÍNH SÁCH BẢO MẬT (POLICIES)
IF EXISTS (SELECT * FROM sys.security_policies WHERE name = 'PMS_SecurityPolicy')
    DROP SECURITY POLICY PMS_SecurityPolicy;
GO

CREATE SECURITY POLICY PMS_SecurityPolicy
-- Áp dụng cho nhóm bảng liên quan trực tiếp group_id
ADD FILTER PREDICATE Security.fn_GroupPredicate(id) ON dbo.groups,
ADD FILTER PREDICATE Security.fn_GroupPredicate(group_id) ON dbo.group_members,
ADD FILTER PREDICATE Security.fn_GroupPredicate(group_id) ON dbo.projects,
ADD FILTER PREDICATE Security.fn_GroupPredicate(group_id) ON dbo.group_invitations,
-- Áp dụng cho nhóm bảng liên quan qua project_id
ADD FILTER PREDICATE Security.fn_ProjectPredicate(project_id) ON dbo.tasks,
ADD FILTER PREDICATE Security.fn_ProjectPredicate(project_id) ON dbo.sprints,
-- Áp dụng cho thông báo
ADD FILTER PREDICATE Security.fn_NotificationPredicate(recipient_id) ON dbo.notification
WITH (STATE = ON);
GO
-- INDEX
	ALTER TABLE [users] ADD CONSTRAINT CHK_UserRole CHECK ([role] IN (0, 1, 2, 3));

-- Dữ liệu mẫu

	-- 1. CHÈN DỮ LIỆU BẢNG CLASSES & SEMESTERS
	INSERT INTO [classes] ([name], [quantity], [start_date], [end_date])
	VALUES 
	('SE1601', 30, '2026-01-01', '2026-05-30'),
	('SE1602', 25, '2026-01-01', '2026-05-30'),
	('AI1603', 20, '2026-02-15', '2026-06-15');

	INSERT INTO [semesters] ([name], [start_date], [end_date])
	VALUES 
	('Spring 2026', '2026-01-01', '2026-04-30'),
	('Summer 2026', '2026-05-01', '2026-08-30');
	GO

	-- 2. CHÈN DỮ LIỆU BẢNG USERS (Mật khẩu mẫu là '123')
	-- Role: 0: ADMIN, 1: STUDENT, 2: STAFF, 3: LECTURER
	INSERT INTO [users] ([code], [email], [password], [full_name], [phone], [role], [class_id])
	VALUES 
	('ADMIN01', 'admin@pms.com', '123', N'Nguyễn Quản Trị', '090111222', 0, NULL),
	('STAFF01', 'staff.lan@pms.com', '123', N'Lê Thị Lan', '090333444', 2, NULL),
	('LECT01', 'teacher.dung@pms.com', '123', N'Trần Tiến Dũng', '090555666', 3, NULL),
	('LECT02', 'teacher.ha@pms.com', '123', N'Hoàng Thu Hà', '090777888', 3, NULL),
	('SV001', 'an.nv@pms.com', '123', N'Nguyễn Văn An', '091100001', 1, 1),
	('SV002', 'binh.lt@pms.com', '123', N'Lý Thanh Bình', '091100002', 1, 1),
	('SV003', 'cuong.dv@pms.com', '123', N'Đỗ Văn Cường', '091100003', 1, 1),
	('SV004', 'dung.tt@pms.com', '123', N'Trịnh Tiến Dũng', '091100004', 1, 1),
	('SV005', 'em.nt@pms.com', '123', N'Ngô Thị Em', '091100005', 1, 2),
	('SV006', 'phuong.hp@pms.com', '123', N'Hoàng Phi Phương', '091100006', 1, 2);
	GO

	-- 3. CHÈN DỮ LIỆU BẢNG GROUPS & GROUP MEMBERS
	INSERT INTO [groups] ([name], [class_id], [semester_id], [status])
	VALUES 
	(N'Nhóm 1 - Web App', 1, 1, 1),
	(N'Nhóm 2 - Mobile Game', 1, 1, 1),
	(N'Nhóm 3 - AI Chatbot', 2, 1, 1);

	INSERT INTO [group_members] ([group_id], [user_id], [role], [score], [status])
	VALUES 
	(1, 5, 'LEADER', NULL, 1), -- An là leader nhóm 1
	(1, 6, 'MEMBER', NULL, 1), -- Bình nhóm 1
	(2, 7, 'LEADER', NULL, 1), -- Cường leader nhóm 2
	(2, 8, 'MEMBER', NULL, 1), -- Dũng nhóm 2
	(3, 9, 'LEADER', NULL, 1); -- Em leader nhóm 3
	GO

	-- 4. CHÈN DỮ LIỆU BẢNG PROJECTS & SPRINTS
	INSERT INTO [projects] ([group_id], [name], [detail], [status])
	VALUES 
	(1, N'Hệ thống quản lý đồ án', N'Xây dựng website quản lý tiến độ đồ án sinh viên', 1),
	(2, N'Ứng dụng học tiếng Anh', N'App học từ vựng cho trẻ em', 1);

	INSERT INTO [sprints] ([project_id], [name], [status], [start_date], [end_date])
	VALUES 
	(1, 'Sprint 1: UI/UX Design', 2, '2026-01-01', '2026-01-15'),
	(1, 'Sprint 2: Database Setup', 1, '2026-01-16', '2026-01-30'),
	(2, 'Sprint 1: Research', 2, '2026-01-01', '2026-01-20');
	GO

	-- 5. CHÈN DỮ LIỆU BẢNG TASKS
	-- Status: 0: Todo, 1: Doing, 2: Done
	INSERT INTO [tasks] ([project_id], [sprint_id], [name], [description], [points], [status], [assigned_by], [assigned_to], [priority])
	VALUES 
	(1, 1, N'Thiết kế giao diện Login', N'Sử dụng Figma để thiết kế', 3, 2, 5, 5, 1),
	(1, 1, N'Thiết kế giao diện Dashboard', N'Các biểu đồ thống kê', 5, 2, 5, 6, 2),
	(1, 2, N'Tạo Database trên SQL Server', N'Viết script tạo bảng', 8, 1, 5, 5, 1),
	(1, 2, N'Viết API đăng nhập', N'Sử dụng Node.js/Express', 5, 0, 5, 6, 2);
	GO

	-- 6. CHÈN DỮ LIỆU BẢNG NOTIFICATION & INVITATIONS
	INSERT INTO [notification] ([type], [recipient_id], [sender_id], [target_id], [target_type], [detail], [status])
	VALUES 
	('SYSTEM_POPUP', 5, 1, NULL, NULL, N'Chào mừng bạn đến với hệ thống PMS!', 1),
	('TASK_ASSIGN', 6, 5, 2, 'TASK', N'Bạn đã được phân công task: Thiết kế Dashboard', 1),
	('GROUP_INVITE', 10, 9, 1, 'INVITATION', N'Bạn có lời mời tham gia Nhóm 3', 1);

	INSERT INTO [group_invitations] ([group_id], [inviter_id], [invited_id], [status])
	VALUES 
	(3, 9, 10, 0); -- Nhóm 3 mời Sinh viên 10
	GO

	-- 7. TEMPLATES
	INSERT INTO [templates] ([name], [detail], [source], [status], [semester_id], [author_id])
	VALUES 
	(N'Mẫu báo cáo tiến độ', N'Mẫu file Word chuẩn để báo cáo hàng tuần', 'Office', 1, 1, 3);
	GO
-- HOÀN TẤT
PRINT '-------------------------------------------------------------------';
PRINT 'Database PMS đã được khởi tạo thành công!';
PRINT '1. Các bảng và khóa ngoại đã được thiết lập.';
PRINT '2. Các Role (Admin, Student, Lecturer, Staff) đã được tạo.';
PRINT '3. Row-Level Security đã được kích hoạt:';
PRINT '   - Sinh viên chỉ xem được Group/Project/Task của chính mình.';
PRINT '   - Admin/Lecturer/Staff có quyền xem toàn bộ để quản lý.';
PRINT '   - Thông báo (Notification) chỉ hiển thị cho đúng người nhận.';
PRINT '-------------------------------------------------------------------';