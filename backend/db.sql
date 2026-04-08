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

-- 2. TẠO CÁC BẢNG CƠ SỞ (NHÓM KHÔNG PHỤ THUỘC)
CREATE TABLE Accounts (
    AccountID INT PRIMARY KEY IDENTITY(1,1),
    Username VARCHAR(100) UNIQUE NOT NULL,
    PasswordHash NVARCHAR(MAX) NULL,
    Role NVARCHAR(20) NOT NULL,
    IsActive BIT NOT NULL DEFAULT 0,
    AuthProvider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
);

CREATE TABLE Classes (
    ClassID INT PRIMARY KEY IDENTITY(1,1),
    ClassName VARCHAR(50) NOT NULL,
    StartDate DATE NULL,
    EndDate DATE NULL
);

CREATE TABLE Semesters (
    SemesterID INT PRIMARY KEY IDENTITY(1,1),
    SemesterName NVARCHAR(50) NOT NULL,
    StartDate DATE NULL,
    EndDate DATE NULL
);

CREATE TABLE OTP_Verifications (
    ID INT PRIMARY KEY IDENTITY(1,1),
    Email VARCHAR(100) NOT NULL,
    OTPCode VARCHAR(6) NOT NULL,
    ExpiredAt DATETIME NOT NULL,
    IsUsed BIT NOT NULL DEFAULT 0
);
GO

-- 3. TẠO NHÓM NHÂN SỰ (STUDENTS, LECTURERS, STAFF)
CREATE TABLE Students (
    StudentID INT PRIMARY KEY IDENTITY(1,1),
    StudentCode VARCHAR(20) UNIQUE NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    SchoolEmail VARCHAR(100) UNIQUE NOT NULL,
    PhoneNumber VARCHAR(15) UNIQUE NOT NULL,
    ClassID INT NULL,
    AccountID INT NULL,
    CONSTRAINT FK_Students_Classes FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    CONSTRAINT FK_Students_Accounts FOREIGN KEY (AccountID) REFERENCES Accounts(AccountID)
);

CREATE TABLE Lecturers (
    LecturerID INT PRIMARY KEY IDENTITY(1,1),
    LecturerCode VARCHAR(20) UNIQUE NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    SchoolEmail VARCHAR(100) UNIQUE NOT NULL,
    PhoneNumber VARCHAR(15) UNIQUE NOT NULL,
    AccountID INT NULL,
    CONSTRAINT FK_Lecturers_Accounts FOREIGN KEY (AccountID) REFERENCES Accounts(AccountID)
);

CREATE TABLE Staff (
    StaffID INT PRIMARY KEY IDENTITY(1,1),
    StaffCode VARCHAR(20) UNIQUE NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    SchoolEmail VARCHAR(100) UNIQUE NOT NULL,
    PhoneNumber VARCHAR(15) UNIQUE NOT NULL,
    AccountID INT NULL,
    CONSTRAINT FK_Staff_Accounts FOREIGN KEY (AccountID) REFERENCES Accounts(AccountID)
);
GO

-- 4. TẠO CÁC INDEX DUY NHẤT CHO ACCOUNT
CREATE UNIQUE INDEX UX_Students_AccountID ON Students(AccountID) WHERE AccountID IS NOT NULL;
CREATE UNIQUE INDEX UX_Lecturers_AccountID ON Lecturers(AccountID) WHERE AccountID IS NOT NULL;
CREATE UNIQUE INDEX UX_Staff_AccountID ON Staff(AccountID) WHERE AccountID IS NOT NULL;
GO

-- 5. TẠO NHÓM ĐĂNG KÝ VÀ LỚP HỌC
CREATE TABLE Group_Registration_Periods (
    PeriodID INT PRIMARY KEY IDENTITY(1,1),
    ClassID INT NOT NULL,
    SemesterID INT NOT NULL,
    StartDate DATETIME NOT NULL,
    EndDate DATETIME NOT NULL,
    IsActive BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_GroupRegPeriods_Classes FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    CONSTRAINT FK_GroupRegPeriods_Semesters FOREIGN KEY (SemesterID) REFERENCES Semesters(SemesterID)
);

CREATE TABLE Groups (
    GroupID INT PRIMARY KEY IDENTITY(1,1),
    GroupName NVARCHAR(100) NOT NULL,
    ClassID INT NOT NULL,
    SemesterID INT NOT NULL,
    LeaderID INT NULL,
    CreatedDate DATETIME NOT NULL DEFAULT GETDATE(),
    IsLocked BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Groups_Classes FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    CONSTRAINT FK_Groups_Semesters FOREIGN KEY (SemesterID) REFERENCES Semesters(SemesterID),
    CONSTRAINT FK_Groups_Leader FOREIGN KEY (LeaderID) REFERENCES Students(StudentID)
);

CREATE TABLE Group_Members (
    ID INT PRIMARY KEY IDENTITY(1,1),
    GroupID INT NOT NULL,
    StudentID INT NOT NULL,
    IsActive BIT NOT NULL DEFAULT 1,
    JoinedDate DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_GroupMembers_Groups FOREIGN KEY (GroupID) REFERENCES Groups(GroupID),
    CONSTRAINT FK_GroupMembers_Students FOREIGN KEY (StudentID) REFERENCES Students(StudentID)
);

CREATE TABLE Group_Invitations (
    InvitationID INT PRIMARY KEY IDENTITY(1,1),
    GroupID INT NOT NULL,
    StudentID INT NOT NULL,
    InvitedByStudentID INT NOT NULL,
    Status VARCHAR(20) NOT NULL,
    InvitedDate DATETIME NOT NULL DEFAULT GETDATE(),
    RespondedDate DATETIME NULL,
    CONSTRAINT FK_GroupInv_Group FOREIGN KEY (GroupID) REFERENCES Groups(GroupID),
    CONSTRAINT FK_GroupInv_Student FOREIGN KEY (StudentID) REFERENCES Students(StudentID),
    CONSTRAINT FK_GroupInv_InvitedBy FOREIGN KEY (InvitedByStudentID) REFERENCES Students(StudentID)
);

CREATE TABLE Class_Lecturers (
    ID INT PRIMARY KEY IDENTITY(1,1),
    ClassID INT NOT NULL,
    LecturerID INT NOT NULL,
    RoleType INT NOT NULL,
    SemesterID INT NOT NULL,
    CONSTRAINT FK_ClassLecturers_Classes FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    CONSTRAINT FK_ClassLecturers_Lecturers FOREIGN KEY (LecturerID) REFERENCES Lecturers(LecturerID),
    CONSTRAINT FK_ClassLecturers_Semesters FOREIGN KEY (SemesterID) REFERENCES Semesters(SemesterID)
);
GO

-- 6. TẠO NHÓM QUẢN LÝ DỰ ÁN (PROJECTS)
CREATE TABLE ProjectTemplates (
    TemplateID INT PRIMARY KEY IDENTITY(1,1),
    Name NVARCHAR(200) NOT NULL,
    Description NVARCHAR(MAX),
    Source NVARCHAR(50) NOT NULL, -- India / Lecturer
    ImageUrl NVARCHAR(500) NULL,
    Version INT NOT NULL DEFAULT 1,
    IsActive BIT NOT NULL DEFAULT 1,
    SemesterID INT NULL,
    Year INT NULL,
    CreatedByStaffID INT NULL,
    CreatedAt DATETIME DEFAULT GETDATE(),
    CONSTRAINT FK_ProjectTemplates_Staff FOREIGN KEY (CreatedByStaffID) REFERENCES Staff(StaffID)
);

CREATE TABLE ProjectTemplateAttachments (
    AttachmentID INT PRIMARY KEY IDENTITY(1,1),
    TemplateID INT NOT NULL,
    FileName NVARCHAR(255) NOT NULL,
    StoredName NVARCHAR(255) NOT NULL,
    FileUrl NVARCHAR(500) NOT NULL,
    ContentType NVARCHAR(100) NULL,
    FileSize BIGINT NULL,
    CreatedAt DATETIME DEFAULT GETDATE(),
    CONSTRAINT FK_ProjectTemplateAttachments_Template FOREIGN KEY (TemplateID) REFERENCES ProjectTemplates(TemplateID)
);

CREATE TABLE ProjectAssignments (
    AssignmentID INT PRIMARY KEY IDENTITY(1,1),
    TemplateID INT NOT NULL,
    GroupID INT NOT NULL,
    StartDate DATETIME NOT NULL,
    EndDate DATETIME NOT NULL,
    Status VARCHAR(20) DEFAULT 'ASSIGNED',
    AssignedByStaffID INT,
    AssignedAt DATETIME DEFAULT GETDATE(),
    CONSTRAINT FK_Assignment_Template FOREIGN KEY (TemplateID) REFERENCES ProjectTemplates(TemplateID),
    CONSTRAINT FK_Assignment_Group FOREIGN KEY (GroupID) REFERENCES Groups(GroupID),
    CONSTRAINT FK_Assignment_Staff FOREIGN KEY (AssignedByStaffID) REFERENCES Staff(StaffID)
);

CREATE TABLE Projects (
    ProjectID INT PRIMARY KEY IDENTITY(1,1),
    AssignmentID INT NOT NULL,
    ProjectName NVARCHAR(200),
    Description NVARCHAR(MAX),
    RequirementFilePath NVARCHAR(500) NULL,
    RequirementFileName NVARCHAR(255) NULL,
    ApprovalStatus INT DEFAULT 0,
    RejectReason NVARCHAR(MAX),
    StudentCanEdit BIT DEFAULT 0,
    SourceCodeUrl NVARCHAR(500),
    DocumentUrl NVARCHAR(500),
    SubmissionDate DATETIME,
    CreatedAt DATETIME DEFAULT GETDATE(),
    CONSTRAINT FK_Project_Assignment FOREIGN KEY (AssignmentID) REFERENCES ProjectAssignments(AssignmentID)
);

CREATE TABLE ProjectAttachments (
    AttachmentID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    FileName NVARCHAR(255) NOT NULL,
    StoredName NVARCHAR(255) NOT NULL,
    FileUrl NVARCHAR(500) NOT NULL,
    ContentType NVARCHAR(100) NULL,
    FileSize BIGINT NULL,
    CreatedAt DATETIME DEFAULT GETDATE(),
    CONSTRAINT FK_ProjectAttachments_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID)
);
GO

-- 7. TẠO NHÓM LOG VÀ REQUEST
CREATE TABLE ProjectAssignmentLogs (
    LogID INT PRIMARY KEY IDENTITY(1,1),
    AssignmentID INT,
    Action NVARCHAR(50),
    PerformedByStaffID INT,
    PerformedAt DATETIME DEFAULT GETDATE(),
    Note NVARCHAR(MAX),
    CONSTRAINT FK_ProjectLogs_Staff FOREIGN KEY (PerformedByStaffID) REFERENCES Staff(StaffID),
    CONSTRAINT FK_ProjectLogs_Assignment FOREIGN KEY (AssignmentID) REFERENCES ProjectAssignments(AssignmentID)
);

CREATE TABLE Project_Edit_Requests (
    RequestID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    RequestedByStudentID INT NOT NULL,
    RequestNote NVARCHAR(MAX) NULL,
    Status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ResponseReason NVARCHAR(MAX) NULL,
    RespondedByStaffID INT NULL,
    RequestedDate DATETIME NOT NULL DEFAULT GETDATE(),
    RespondedDate DATETIME NULL,
    CONSTRAINT FK_ProjectEditReq_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    CONSTRAINT FK_ProjectEditReq_Student FOREIGN KEY (RequestedByStudentID) REFERENCES Students(StudentID),
    CONSTRAINT FK_ProjectEditReq_Staff FOREIGN KEY (RespondedByStaffID) REFERENCES Staff(StaffID)
);

CREATE TABLE Project_Change_Requests (
    RequestID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    RequestedByStudentID INT NOT NULL,
    ProposedProjectName NVARCHAR(200) NOT NULL,
    ProposedDescription NVARCHAR(MAX) NULL,
    ChangeReason NVARCHAR(MAX) NOT NULL,
    Status VARCHAR(30) NOT NULL DEFAULT 'PENDING_STAFF',
    StaffReviewedByStaffID INT NULL,
    StaffRejectReason NVARCHAR(MAX) NULL,
    StaffReviewedAt DATETIME NULL,
    LecturerReviewedByLecturerID INT NULL,
    LecturerRejectReason NVARCHAR(MAX) NULL,
    LecturerReviewedAt DATETIME NULL,
    RequestedDate DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_ProjectChangeReq_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    CONSTRAINT FK_ProjectChangeReq_Student FOREIGN KEY (RequestedByStudentID) REFERENCES Students(StudentID),
    CONSTRAINT FK_ProjectChangeReq_Staff FOREIGN KEY (StaffReviewedByStaffID) REFERENCES Staff(StaffID),
    CONSTRAINT FK_ProjectChangeReq_Lecturer FOREIGN KEY (LecturerReviewedByLecturerID) REFERENCES Lecturers(LecturerID)
);
GO

-- 8. TẠO NHÓM TIẾN ĐỘ (SPRINTS & TASKS)
CREATE TABLE Sprints (
    SprintID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    SprintName NVARCHAR(50) NULL,
    StartDate DATE NOT NULL,
    EndDate DATE NOT NULL,
    IsClosed BIT NOT NULL DEFAULT 0,
    IsCancelled BIT NOT NULL DEFAULT 0,
    CancelReason NVARCHAR(MAX) NULL,
    CancelledByStudentID INT NULL,
    CancelledAt DATETIME NULL,
    CONSTRAINT FK_Sprints_Projects FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID)
);

CREATE TABLE Tasks (
    TaskID INT PRIMARY KEY IDENTITY(1,1),
    SprintID INT NOT NULL,
    ProjectID INT NULL, -- Optional field added per your requirement
    TaskName NVARCHAR(200) NOT NULL,
    Description NVARCHAR(MAX) NULL,
    TaskImage NVARCHAR(MAX) NULL,
    EstimatedPoints FLOAT NOT NULL DEFAULT 0,
    AssigneeID INT NOT NULL,
    ReviewerID INT NULL,
    Status INT NOT NULL DEFAULT 0,
    SubmissionNote NVARCHAR(MAX) NULL,
    SubmissionUrl NVARCHAR(500) NULL,
    SubmissionFiles NVARCHAR(MAX) NULL,
    SubmissionCode NVARCHAR(MAX) NULL,
    SubmittedAt DATETIME NULL,
    ReviewComment NVARCHAR(MAX) NULL,
    ReviewedAt DATETIME NULL,
    ActualStartTime DATETIME NULL,
    ActualEndTime DATETIME NULL,
    CancelledReason NVARCHAR(MAX) NULL,
    CancelledByStudentID INT NULL,
    CancelledAt DATETIME NULL,
    CONSTRAINT FK_Tasks_Sprints FOREIGN KEY (SprintID) REFERENCES Sprints(SprintID),
    CONSTRAINT FK_Tasks_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    CONSTRAINT FK_Tasks_Assignee FOREIGN KEY (AssigneeID) REFERENCES Students(StudentID),
    CONSTRAINT FK_Tasks_Reviewer FOREIGN KEY (ReviewerID) REFERENCES Students(StudentID)
);
GO

-- 9. TẠO NHÓM TƯƠNG TÁC VÀ ĐIỂM SỐ
CREATE TABLE Project_Comments (
    CommentID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    LecturerID INT NOT NULL,
    CommentContent NVARCHAR(MAX) NOT NULL,
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_ProjectComments_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    CONSTRAINT FK_ProjectComments_Lecturer FOREIGN KEY (LecturerID) REFERENCES Lecturers(LecturerID)
);

CREATE TABLE Presentation_Schedules (
    ScheduleID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    PresentationTime DATETIME NOT NULL,
    Location NVARCHAR(100) NULL,
    CreatedByStaffID INT NULL,
    CONSTRAINT FK_PresentationSchedules_Projects FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    CONSTRAINT FK_PresentationSchedules_Staff FOREIGN KEY (CreatedByStaffID) REFERENCES Staff(StaffID)
);

CREATE TABLE Project_Scores (
    ScoreID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    StudentID INT NOT NULL,
    LecturerID INT NOT NULL,
    LecturerScore FLOAT NULL,
    LecturerComment NVARCHAR(MAX) NULL,
    StaffAdjustedScore FLOAT NULL,
    StaffNote NVARCHAR(MAX) NULL,
    IsPublished BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_ProjectScores_Projects FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    CONSTRAINT FK_ProjectScores_Students FOREIGN KEY (StudentID) REFERENCES Students(StudentID),
    CONSTRAINT FK_ProjectScores_Lecturers FOREIGN KEY (LecturerID) REFERENCES Lecturers(LecturerID)
);
GO

-- 10. INDEX HIỆU NĂNG
CREATE INDEX IX_GroupInv_Group_Status ON Group_Invitations(GroupID, Status);
CREATE INDEX IX_GroupInv_Student_Status ON Group_Invitations(StudentID, Status);
CREATE INDEX IX_ProjectEditReq_Status ON Project_Edit_Requests(Status, RequestedDate DESC);
CREATE INDEX IX_ProjectChangeReq_Status ON Project_Change_Requests(Status, RequestedDate DESC);
CREATE INDEX IX_ProjectChangeReq_Project ON Project_Change_Requests(ProjectID, RequestedDate DESC);
CREATE INDEX IX_ProjectComments_Project ON Project_Comments(ProjectID, CreatedAt DESC);
CREATE INDEX IX_Assignment_Group ON ProjectAssignments(GroupID);
CREATE INDEX IX_Assignment_Template ON ProjectAssignments(TemplateID);
CREATE INDEX IX_Sprints_Project ON Sprints(ProjectID);
CREATE INDEX IX_Tasks_Sprint ON Tasks(SprintID);
CREATE INDEX IX_Tasks_Assignee ON Tasks(AssigneeID);
GO

-- 11. CHÈN DỮ LIỆU MẪU BAN ĐẦU
INSERT INTO Semesters (SemesterName, StartDate, EndDate)
VALUES (N'Spring 2026', '2026-01-01', '2026-04-30');

INSERT INTO Classes (ClassName, StartDate, EndDate)
VALUES ('SE1701', '2023-01-01', '2027-12-31'),
       ('SE1702', '2023-01-01', '2027-12-31'),
       ('AI1801', '2024-01-01', '2028-12-31');
GO

-- 12. CHÈN NHÂN SỰ VÀ TÀI KHOẢN (LIÊN KẾT)
INSERT INTO Accounts (Username, PasswordHash, Role, IsActive, AuthProvider)
VALUES ('duc47xuan@gmail.com', NULL, 'Staff', 1, 'LOCAL'),
       ('dxuan191205@gmail.com', NULL, 'Lecturer', 1, 'LOCAL'),
       ('dic47xuan@gmail.com', NULL, 'Student', 1, 'LOCAL'),
       ('cunhocit05@gmail.com', NULL, 'Student', 1, 'LOCAL'),
       ('mcboon25@gmail.com', NULL, 'Staff', 1, 'LOCAL');

-- Cập nhật liên kết sau khi có AccountID
INSERT INTO Staff (StaffCode, FullName, SchoolEmail, PhoneNumber, AccountID)
SELECT 'STF001', N'Nhan Vien 1', 'duc47xuan@gmail.com', '0812559433', AccountID FROM Accounts WHERE Username = 'duc47xuan@gmail.com';

INSERT INTO Staff (StaffCode, FullName, SchoolEmail, PhoneNumber, AccountID)
SELECT 'STF002', N'Nhan Vien 2', 'mcboon25@gmail.com', '0123456789', AccountID FROM Accounts WHERE Username = 'mcboon25@gmail.com';

INSERT INTO Lecturers (LecturerCode, FullName, SchoolEmail, PhoneNumber, AccountID)
SELECT 'LEC001', N'Giang Vien 1', 'dxuan191205@gmail.com', '0812559433', AccountID FROM Accounts WHERE Username = 'dxuan191205@gmail.com';

DECLARE @ClassID_SE1701 INT = (SELECT TOP 1 ClassID FROM Classes WHERE ClassName = 'SE1701');

INSERT INTO Students (StudentCode, FullName, SchoolEmail, PhoneNumber, ClassID, AccountID)
SELECT 'HE123456', N'Duc Xuan', 'dic47xuan@gmail.com', '0812559433', @ClassID_SE1701, AccountID FROM Accounts WHERE Username = 'dic47xuan@gmail.com';

INSERT INTO Students (StudentCode, FullName, SchoolEmail, PhoneNumber, ClassID, AccountID)
SELECT 'HE123457', N'Tri Dung', 'cunhocit05@gmail.com', '0123456789', @ClassID_SE1701, AccountID FROM Accounts WHERE Username = 'cunhocit05@gmail.com';
GO

-- 13. CHÈN GIẢNG VIÊN VÀO LỚP
DECLARE @LecturerA INT = (SELECT TOP 1 LecturerID FROM Lecturers WHERE LecturerCode = 'LEC001');
DECLARE @ClassA INT = (SELECT TOP 1 ClassID FROM Classes WHERE ClassName = 'SE1701');
DECLARE @SemA INT = (SELECT TOP 1 SemesterID FROM Semesters WHERE SemesterName = N'Spring 2026');

IF @LecturerA IS NOT NULL AND @ClassA IS NOT NULL AND @SemA IS NOT NULL
BEGIN
    INSERT INTO Class_Lecturers (ClassID, LecturerID, RoleType, SemesterID)
    VALUES (@ClassA, @LecturerA, 1, @SemA), (@ClassA, @LecturerA, 2, @SemA);
END
GO

-- 14. VÒNG LẶP CHÈN 50 SINH VIÊN MẪU
DECLARE @counter INT = 1;
DECLARE @targetClass INT = (SELECT TOP 1 ClassID FROM Classes WHERE ClassName = 'SE1701');
DECLARE @defaultPass NVARCHAR(100) = '$2a$10$wWmjLRQveGYMzNu6KC54ZOJADuYgJnehxrMMr2cz3t47xyeOV5J56';

WHILE @counter <= 50
BEGIN
    DECLARE @sEmail VARCHAR(100) = 'student' + CAST(@counter AS VARCHAR(10)) + '@gmail.com';
    DECLARE @accID INT;

    INSERT INTO Accounts (Username, PasswordHash, Role, IsActive, AuthProvider)
    VALUES (@sEmail, @defaultPass, 'Student', 1, 'LOCAL');
    
    SET @accID = SCOPE_IDENTITY();

    INSERT INTO Students (StudentCode, FullName, SchoolEmail, PhoneNumber, ClassID, AccountID)
    VALUES (
        'HE12' + RIGHT('0000' + CAST(@counter AS VARCHAR(10)), 4),
        N'Student ' + CAST(@counter AS NVARCHAR(10)),
        @sEmail,
        '090' + RIGHT('0000000' + CAST(@counter AS VARCHAR(10)), 7),
        @targetClass,
        @accID
    );

    SET @counter = @counter + 1;
END
GO

-- HOÀN TẤT
PRINT 'Database PMS has been created and populated successfully.';
