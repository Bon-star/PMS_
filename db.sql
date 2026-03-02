USE master
GO

IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'PMS')
BEGIN
    CREATE DATABASE PMS;
END
GO

--USE PMS
GO

-- 2. XÓA BẢNG CŨ NẾU CÓ (Để chạy lại script sạch sẽ - Thứ tự xóa: Con trước Cha sau)
DROP TABLE IF EXISTS Project_Scores;
DROP TABLE IF EXISTS Presentation_Schedules;
DROP TABLE IF EXISTS Tasks;
DROP TABLE IF EXISTS Sprints;
DROP TABLE IF EXISTS Projects;
DROP TABLE IF EXISTS Class_Lecturers;
DROP TABLE IF EXISTS Group_Members;
DROP TABLE IF EXISTS Groups;
DROP TABLE IF EXISTS Group_Registration_Periods;
DROP TABLE IF EXISTS Semesters;
DROP TABLE IF EXISTS Classes;
DROP TABLE IF EXISTS OTP_Verifications;
DROP TABLE IF EXISTS Staff;
DROP TABLE IF EXISTS Lecturers;
DROP TABLE IF EXISTS Students;
DROP TABLE IF EXISTS Accounts;
GO

-- 3. TẠO CÁC BẢNG CHÍNH

-- Bảng Tài khoản (Dùng chung cho cả 3 đối tượng)
CREATE TABLE Accounts (
    AccountID INT PRIMARY KEY IDENTITY(1,1),
    Username VARCHAR(50) UNIQUE NOT NULL, -- Email đăng nhập
    PasswordHash NVARCHAR(MAX) NULL,      -- NULL khi mới tạo (chưa kích hoạt)
    Role NVARCHAR(20) NOT NULL,           -- 'Student', 'Staff', 'Lecturer'
    IsActive BIT DEFAULT 0,               -- 0: Chưa kích hoạt, 1: Đã kích hoạt
    AuthProvider VARCHAR(20) DEFAULT 'LOCAL', -- 'LOCAL', 'GOOGLE', 'FACEBOOK'
    CreatedAt DATETIME DEFAULT GETDATE()
);
GO

-- Bảng Lớp học
CREATE TABLE Classes (
    ClassID INT PRIMARY KEY IDENTITY(1,1),
    ClassName VARCHAR(50) NOT NULL, -- Ví dụ: SE1701
    CourseYear VARCHAR(20)          -- Ví dụ: 2023-2027
);
GO

-- Bảng Học sinh (Student)
CREATE TABLE Students (
    StudentID INT PRIMARY KEY IDENTITY(1,1),
    StudentCode VARCHAR(20) UNIQUE NOT NULL, -- MSSV (HE123456)
    FullName NVARCHAR(100) NOT NULL,
    SchoolEmail VARCHAR(100) UNIQUE NOT NULL, -- Email trường cấp
    PhoneNumber VARCHAR(15) UNIQUE NOT NULL,
    ClassID INT,
    AccountID INT UNIQUE NULL,
    FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    FOREIGN KEY (AccountID) REFERENCES Accounts(AccountID)
);
GO

-- Bảng Giảng viên (Lecturer)
CREATE TABLE Lecturers (
    LecturerID INT PRIMARY KEY IDENTITY(1,1),
    LecturerCode VARCHAR(20) UNIQUE NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    SchoolEmail VARCHAR(100) UNIQUE NOT NULL,
    PhoneNumber VARCHAR(15) UNIQUE NOT NULL,
    AccountID INT UNIQUE NULL,
    FOREIGN KEY (AccountID) REFERENCES Accounts(AccountID)
);
GO

-- Bảng Nhân viên (Staff - Giáo vụ/Đào tạo)
CREATE TABLE Staff (
    StaffID INT PRIMARY KEY IDENTITY(1,1),
    StaffCode VARCHAR(20) UNIQUE NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    SchoolEmail VARCHAR(100) UNIQUE NOT NULL,
    PhoneNumber VARCHAR(15) UNIQUE NOT NULL,
    AccountID INT UNIQUE NULL,
    FOREIGN KEY (AccountID) REFERENCES Accounts(AccountID)
);
GO

-- Bảng OTP
CREATE TABLE OTP_Verifications (
    ID INT PRIMARY KEY IDENTITY(1,1),
    Email VARCHAR(100) NOT NULL,
    OTPCode VARCHAR(6) NOT NULL,
    ExpiredAt DATETIME NOT NULL,
    IsUsed BIT DEFAULT 0
);
GO

-- Bảng Học kỳ
CREATE TABLE Semesters (
    SemesterID INT PRIMARY KEY IDENTITY(1,1),
    SemesterName NVARCHAR(50) NOT NULL, -- Ví dụ: Spring 2026
    StartDate DATE,
    EndDate DATE
);
GO

-- 4. TẠO CÁC BẢNG LIÊN QUAN ĐẾN QUẢN LÝ NHÓM & PROJECT

-- Bảng Đợt đăng ký nhóm
CREATE TABLE Group_Registration_Periods (
    PeriodID INT PRIMARY KEY IDENTITY(1,1),
    ClassID INT NOT NULL,
    SemesterID INT NOT NULL,
    StartDate DATETIME NOT NULL,
    EndDate DATETIME NOT NULL,
    IsActive BIT DEFAULT 1,
    FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    FOREIGN KEY (SemesterID) REFERENCES Semesters(SemesterID)
);
GO

-- Bảng Nhóm (Team)
CREATE TABLE Groups (
    GroupID INT PRIMARY KEY IDENTITY(1,1),
    GroupName NVARCHAR(100) NOT NULL,
    ClassID INT NOT NULL,
    SemesterID INT NOT NULL,
    LeaderID INT NULL, -- ID trưởng nhóm (Student)
    CreatedDate DATETIME DEFAULT GETDATE(),
    IsLocked BIT DEFAULT 0,
    FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    FOREIGN KEY (SemesterID) REFERENCES Semesters(SemesterID),
    FOREIGN KEY (LeaderID) REFERENCES Students(StudentID)
);
GO

-- Bảng Thành viên nhóm
CREATE TABLE Group_Members (
    ID INT PRIMARY KEY IDENTITY(1,1),
    GroupID INT NOT NULL,
    StudentID INT NOT NULL,
    IsActive BIT DEFAULT 1,
    JoinedDate DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (GroupID) REFERENCES Groups(GroupID),
    FOREIGN KEY (StudentID) REFERENCES Students(StudentID)
);
GO

-- Bảng Lời mời nhóm
CREATE TABLE Group_Invitations (
    InvitationID INT PRIMARY KEY IDENTITY(1,1),
    GroupID INT NOT NULL,
    StudentID INT NOT NULL,
    InvitedByStudentID INT NOT NULL,
    Status VARCHAR(20) NOT NULL,
    InvitedDate DATETIME NOT NULL,
    RespondedDate DATETIME NULL,
    FOREIGN KEY (GroupID) REFERENCES Groups(GroupID),
    FOREIGN KEY (StudentID) REFERENCES Students(StudentID),
    FOREIGN KEY (InvitedByStudentID) REFERENCES Students(StudentID)
);
GO

-- Bảng Phân công Giảng viên (Mentor/Grader)
CREATE TABLE Class_Lecturers (
    ID INT PRIMARY KEY IDENTITY(1,1),
    ClassID INT NOT NULL,
    LecturerID INT NOT NULL,
    RoleType INT NOT NULL, -- 1: Mentor, 2: Grader
    SemesterID INT NOT NULL,
    FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    FOREIGN KEY (LecturerID) REFERENCES Lecturers(LecturerID),
    FOREIGN KEY (SemesterID) REFERENCES Semesters(SemesterID)
);
GO

-- Bảng Dự án (Project)
CREATE TABLE Projects (
    ProjectID INT PRIMARY KEY IDENTITY(1,1),
    GroupID INT UNIQUE NOT NULL,
    ProjectName NVARCHAR(200),
    Description NVARCHAR(MAX),
    TopicSource NVARCHAR(50), -- 'India', 'Lecturer', 'Student'
    ApprovalStatus INT DEFAULT 1, -- 0: Pending, 1: Approved, 2: Rejected...
    RejectReason NVARCHAR(MAX) NULL,
    SourceCodeUrl NVARCHAR(500),
    DocumentUrl NVARCHAR(500),
    SubmissionDate DATETIME,
    StartDate DATETIME,
    EndDate DATETIME,
    FOREIGN KEY (GroupID) REFERENCES Groups(GroupID)
);
GO

-- Bảng Sprint
CREATE TABLE Sprints (
    SprintID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    SprintName NVARCHAR(50),
    StartDate DATE NOT NULL,
    EndDate DATE NOT NULL,
    IsClosed BIT DEFAULT 0,
    FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID)
);
GO

-- Bảng Task
CREATE TABLE Tasks (
    TaskID INT PRIMARY KEY IDENTITY(1,1),
    SprintID INT NOT NULL,
    TaskName NVARCHAR(200) NOT NULL,
    Description NVARCHAR(MAX),
    TaskImage NVARCHAR(MAX),
    EstimatedPoints FLOAT DEFAULT 0,
    AssigneeID INT NOT NULL, -- StudentID
    ReviewerID INT NULL,     -- StudentID
    Status INT DEFAULT 0,    -- 0: Pending, 1: InProgress, 3: Completed, 4: Failed
    ActualStartTime DATETIME,
    ActualEndTime DATETIME,
    FOREIGN KEY (SprintID) REFERENCES Sprints(SprintID),
    FOREIGN KEY (AssigneeID) REFERENCES Students(StudentID),
    FOREIGN KEY (ReviewerID) REFERENCES Students(StudentID)
);
GO

-- Bảng Lịch Báo cáo
CREATE TABLE Presentation_Schedules (
    ScheduleID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    PresentationTime DATETIME NOT NULL,
    Location NVARCHAR(100),
    CreatedByStaffID INT,
    FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    FOREIGN KEY (CreatedByStaffID) REFERENCES Staff(StaffID)
);
GO

-- Bảng Điểm số
CREATE TABLE Project_Scores (
    ScoreID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    StudentID INT NOT NULL,
    LecturerID INT NOT NULL,
    LecturerScore FLOAT,
    LecturerComment NVARCHAR(MAX),
    StaffAdjustedScore FLOAT,
    StaffNote NVARCHAR(MAX),
    IsPublished BIT DEFAULT 0,
    FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    FOREIGN KEY (StudentID) REFERENCES Students(StudentID),
    FOREIGN KEY (LecturerID) REFERENCES Lecturers(LecturerID)
);
GO

IF OBJECT_ID('dbo.Group_Invitations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Group_Invitations (
        InvitationID INT PRIMARY KEY IDENTITY(1,1),
        GroupID INT NOT NULL,
        StudentID INT NOT NULL,
        InvitedByStudentID INT NOT NULL,
        Status VARCHAR(20) NOT NULL,
        InvitedDate DATETIME NOT NULL CONSTRAINT DF_GroupInv_InvitedDate DEFAULT GETDATE(),
        RespondedDate DATETIME NULL,
        CONSTRAINT FK_GroupInv_Group FOREIGN KEY (GroupID) REFERENCES dbo.Groups(GroupID),
        CONSTRAINT FK_GroupInv_Student FOREIGN KEY (StudentID) REFERENCES dbo.Students(StudentID),
        CONSTRAINT FK_GroupInv_InvitedBy FOREIGN KEY (InvitedByStudentID) REFERENCES dbo.Students(StudentID)
    );

    CREATE INDEX IX_GroupInv_Group_Status ON dbo.Group_Invitations(GroupID, Status);
    CREATE INDEX IX_GroupInv_Student_Status ON dbo.Group_Invitations(StudentID, Status);
END
GO

-- 5. SEED DATA (DỮ LIỆU MẪU)

-- 5.1 Thêm Học kỳ & Lớp
INSERT INTO Semesters (SemesterName, StartDate, EndDate) VALUES ('Spring 2026', '2026-01-01', '2026-04-30');
INSERT INTO Classes (ClassName, CourseYear) VALUES ('SE1701', '2023-2027'), ('SE1702', '2023-2027'), ('AI1801', '2024-2028');

-- 5.2 Thêm STAFF (Nhân viên)
-- Tạo Account Staff trước
INSERT INTO Accounts (Username, Role, IsActive) VALUES ('duc47xuan@gmail.com', 'Staff', 0); -- Pass NULL
DECLARE @AccStaff1 INT = SCOPE_IDENTITY();
-- Tạo Profile Staff
INSERT INTO Staff (StaffCode, FullName, SchoolEmail, PhoneNumber, AccountID)
VALUES ('STF001', N'Nhan Vien 1', 'duc47xuan@gmail.com', '0812559433', @AccStaff1);

-- 5.3 Thêm LECTURER (Giảng viên)
-- GV 1
INSERT INTO Accounts (Username, Role, IsActive) VALUES ('dxuan191205@gmail.com', 'Lecturer', 0);
DECLARE @AccLec1 INT = SCOPE_IDENTITY();
INSERT INTO Lecturers (LecturerCode, FullName, SchoolEmail, PhoneNumber, AccountID)
VALUES ('LEC001', N'Giang Vien 1', 'dxuan191205@gmail.com', '0812559433', @AccLec1);

-- 5.4 Thêm STUDENT (Học sinh)
DECLARE @ClassA INT = (SELECT TOP 1 ClassID FROM Classes WHERE ClassName='SE1701');
DECLARE @ClassB INT = (SELECT TOP 1 ClassID FROM Classes WHERE ClassName='SE1702');

-- HS 1 (Email bạn cung cấp)
INSERT INTO Accounts (Username, Role, IsActive) VALUES ('dic47xuan@gmail.com', 'Student', 0);
DECLARE @AccStu1 INT = SCOPE_IDENTITY();
INSERT INTO Students (StudentCode, FullName, SchoolEmail, PhoneNumber, ClassID, AccountID)
VALUES ('HE123456', N'Duc Xuan', 'dic47xuan@gmail.com', '0812559433', @ClassA, @AccStu1);

-- HS 2
INSERT INTO Accounts (Username, Role, IsActive) VALUES ('zooxoox3@gmail.com', 'Student', 0);
DECLARE @AccStu2 INT = SCOPE_IDENTITY();
INSERT INTO Students (StudentCode, FullName, SchoolEmail, PhoneNumber, ClassID, AccountID)
VALUES ('HE123457', N'Tri Dung', 'zooxoox3@gmail.com', '0123456789', @ClassA, @AccStu2);
GO