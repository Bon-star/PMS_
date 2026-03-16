USE master;
GO

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

CREATE TABLE Accounts (
    AccountID INT PRIMARY KEY IDENTITY(1,1),
    Username VARCHAR(100) UNIQUE NOT NULL,
    PasswordHash NVARCHAR(MAX) NULL,
    Role NVARCHAR(20) NOT NULL,
    IsActive BIT NOT NULL DEFAULT 0,
    AuthProvider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
);
GO

CREATE TABLE Classes (
    ClassID INT PRIMARY KEY IDENTITY(1,1),
    ClassName VARCHAR(50) NOT NULL,
    CourseYear VARCHAR(20) NULL
);
GO

CREATE TABLE Students (
    StudentID INT PRIMARY KEY IDENTITY(1,1),
    StudentCode VARCHAR(20) UNIQUE NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    SchoolEmail VARCHAR(100) UNIQUE NOT NULL,
    PhoneNumber VARCHAR(15) UNIQUE NOT NULL,
    ClassID INT NULL,
    AccountID INT NULL,
    Avatar VARCHAR(255) NOT NULL DEFAULT 'user.png',
    CONSTRAINT FK_Students_Classes FOREIGN KEY (ClassID) REFERENCES Classes(ClassID),
    CONSTRAINT FK_Students_Accounts FOREIGN KEY (AccountID) REFERENCES Accounts(AccountID)
);
GO

CREATE TABLE Lecturers (
    LecturerID INT PRIMARY KEY IDENTITY(1,1),
    LecturerCode VARCHAR(20) UNIQUE NOT NULL,
    FullName NVARCHAR(100) NOT NULL,
    SchoolEmail VARCHAR(100) UNIQUE NOT NULL,
    PhoneNumber VARCHAR(15) UNIQUE NOT NULL,
    AccountID INT NULL,
    CONSTRAINT FK_Lecturers_Accounts FOREIGN KEY (AccountID) REFERENCES Accounts(AccountID)
);
GO

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

CREATE UNIQUE INDEX UX_Students_AccountID ON Students(AccountID) WHERE AccountID IS NOT NULL;
CREATE UNIQUE INDEX UX_Lecturers_AccountID ON Lecturers(AccountID) WHERE AccountID IS NOT NULL;
CREATE UNIQUE INDEX UX_Staff_AccountID ON Staff(AccountID) WHERE AccountID IS NOT NULL;
GO

CREATE TABLE OTP_Verifications (
    ID INT PRIMARY KEY IDENTITY(1,1),
    Email VARCHAR(100) NOT NULL,
    OTPCode VARCHAR(6) NOT NULL,
    ExpiredAt DATETIME NOT NULL,
    IsUsed BIT NOT NULL DEFAULT 0
);
GO

CREATE TABLE Semesters (
    SemesterID INT PRIMARY KEY IDENTITY(1,1),
    SemesterName NVARCHAR(50) NOT NULL,
    StartDate DATE NULL,
    EndDate DATE NULL
);
GO

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
GO

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
GO

CREATE TABLE Group_Members (
    ID INT PRIMARY KEY IDENTITY(1,1),
    GroupID INT NOT NULL,
    StudentID INT NOT NULL,
    IsActive BIT NOT NULL DEFAULT 1,
    JoinedDate DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_GroupMembers_Groups FOREIGN KEY (GroupID) REFERENCES Groups(GroupID),
    CONSTRAINT FK_GroupMembers_Students FOREIGN KEY (StudentID) REFERENCES Students(StudentID)
);
GO

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
GO

CREATE INDEX IX_GroupInv_Group_Status ON Group_Invitations(GroupID, Status);
CREATE INDEX IX_GroupInv_Student_Status ON Group_Invitations(StudentID, Status);
GO

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

CREATE TABLE Projects (
    ProjectID INT PRIMARY KEY IDENTITY(1,1),
    GroupID INT UNIQUE NOT NULL,
    ProjectName NVARCHAR(200) NULL,
    Description NVARCHAR(MAX) NULL,
    TopicSource NVARCHAR(50) NULL,
    ApprovalStatus INT NOT NULL DEFAULT 1,
    RejectReason NVARCHAR(MAX) NULL,
    SourceCodeUrl NVARCHAR(500) NULL,
    DocumentUrl NVARCHAR(500) NULL,
    SubmissionDate DATETIME NULL,
    StartDate DATETIME NULL,
    EndDate DATETIME NULL,
    StudentCanEdit BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Projects_Groups FOREIGN KEY (GroupID) REFERENCES Groups(GroupID)
);
GO

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
GO

CREATE INDEX IX_ProjectEditReq_Status ON Project_Edit_Requests(Status, RequestedDate DESC);
GO

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

CREATE INDEX IX_ProjectChangeReq_Status ON Project_Change_Requests(Status, RequestedDate DESC);
CREATE INDEX IX_ProjectChangeReq_Project ON Project_Change_Requests(ProjectID, RequestedDate DESC);
GO

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
GO

CREATE TABLE Tasks (
    TaskID INT PRIMARY KEY IDENTITY(1,1),
    SprintID INT NOT NULL,
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
    CONSTRAINT FK_Tasks_Assignee FOREIGN KEY (AssigneeID) REFERENCES Students(StudentID),
    CONSTRAINT FK_Tasks_Reviewer FOREIGN KEY (ReviewerID) REFERENCES Students(StudentID)
);
GO

CREATE TABLE Project_Comments (
    CommentID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    LecturerID INT NOT NULL,
    CommentContent NVARCHAR(MAX) NOT NULL,
    CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_ProjectComments_Project FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    CONSTRAINT FK_ProjectComments_Lecturer FOREIGN KEY (LecturerID) REFERENCES Lecturers(LecturerID)
);
GO

CREATE INDEX IX_ProjectComments_Project ON Project_Comments(ProjectID, CreatedAt DESC);
GO

CREATE TABLE Presentation_Schedules (
    ScheduleID INT PRIMARY KEY IDENTITY(1,1),
    ProjectID INT NOT NULL,
    PresentationTime DATETIME NOT NULL,
    Location NVARCHAR(100) NULL,
    CreatedByStaffID INT NULL,
    CONSTRAINT FK_PresentationSchedules_Projects FOREIGN KEY (ProjectID) REFERENCES Projects(ProjectID),
    CONSTRAINT FK_PresentationSchedules_Staff FOREIGN KEY (CreatedByStaffID) REFERENCES Staff(StaffID)
);
GO

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

INSERT INTO Semesters (SemesterName, StartDate, EndDate)
VALUES (N'Spring 2026', '2026-01-01', '2026-04-30');
GO

INSERT INTO Classes (ClassName, CourseYear)
VALUES ('SE1701', '2023-2027'),
       ('SE1702', '2023-2027'),
       ('AI1801', '2024-2028');
GO

DECLARE @ClassA INT = (SELECT TOP 1 ClassID FROM Classes WHERE ClassName = 'SE1701');
DECLARE @SemesterSpring INT = (SELECT TOP 1 SemesterID FROM Semesters WHERE SemesterName = N'Spring 2026');

INSERT INTO Staff (StaffCode, FullName, SchoolEmail, PhoneNumber, AccountID)
VALUES ('STF001', N'Nhan Vien 1', 'duc47xuan@gmail.com', '0812559433', NULL);

INSERT INTO Lecturers (LecturerCode, FullName, SchoolEmail, PhoneNumber, AccountID)
VALUES ('LEC001', N'Giang Vien 1', 'dxuan191205@gmail.com', '0812559433', NULL);

INSERT INTO Students (StudentCode, FullName, SchoolEmail, PhoneNumber, ClassID, AccountID)
VALUES ('HE123456', N'Duc Xuan', 'dic47xuan@gmail.com', '0812559433', @ClassA, NULL),
       ('HE123457', N'Tri Dung', 'zooxoox3@gmail.com', '0123456789', @ClassA, NULL);
GO

INSERT INTO Accounts (Username, PasswordHash, Role, IsActive, AuthProvider)
VALUES ('duc47xuan@gmail.com', NULL, 'Staff', 0, 'LOCAL'),
       ('dxuan191205@gmail.com', NULL, 'Lecturer', 0, 'LOCAL'),
       ('dic47xuan@gmail.com', NULL, 'Student', 0, 'LOCAL'),
       ('cunhocit05@gmail.com', NULL, 'Student', 0, 'LOCAL');
GO

UPDATE st
SET st.AccountID = a.AccountID
FROM Staff st
INNER JOIN Accounts a ON a.Username = st.SchoolEmail AND a.Role = 'Staff';

UPDATE l
SET l.AccountID = a.AccountID
FROM Lecturers l
INNER JOIN Accounts a ON a.Username = l.SchoolEmail AND a.Role = 'Lecturer';

UPDATE s
SET s.AccountID = a.AccountID
FROM Students s
INNER JOIN Accounts a ON a.Username = s.SchoolEmail AND a.Role = 'Student';
GO

DECLARE @LecturerA INT = (SELECT TOP 1 LecturerID FROM Lecturers WHERE LecturerCode = 'LEC001');
DECLARE @ClassForLecturer INT = (SELECT TOP 1 ClassID FROM Classes WHERE ClassName = 'SE1701');
DECLARE @SemesterForLecturer INT = (SELECT TOP 1 SemesterID FROM Semesters WHERE SemesterName = N'Spring 2026');
IF @ClassForLecturer IS NOT NULL AND @SemesterForLecturer IS NOT NULL AND @LecturerA IS NOT NULL
BEGIN
    INSERT INTO Class_Lecturers (ClassID, LecturerID, RoleType, SemesterID)
    VALUES (@ClassForLecturer, @LecturerA, 1, @SemesterForLecturer),
           (@ClassForLecturer, @LecturerA, 2, @SemesterForLecturer);
END
GO
