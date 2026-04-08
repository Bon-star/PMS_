# Frontend-First Migration Plan

## Goal

Make `frontend/` the only user interface while keeping **all existing backend features and screens** available during migration.

The backend should end up serving:

- REST APIs under `/api/**`
- file download/upload endpoints
- authentication/session handling
- business rules and database access

The frontend should end up owning:

- all user-facing pages
- page navigation
- form UX
- modal/dialog flows
- visual parity with the current Thymeleaf screens

## Non-Negotiable Rules

1. Do **not** delete any Thymeleaf page before the React page reaches feature parity.
2. Do **not** remove any MVC action before the equivalent API action is implemented and verified.
3. Treat backend MVC pages as the current source of truth for UX and behavior.
4. Move duplicated business rules out of controllers when needed so MVC and API use the same logic.
5. Every migrated page must preserve:
   - role permissions
   - validations
   - empty states
   - success/error messages
   - file upload/download behavior
   - timeline locks
   - status transitions

## Current State

The system is currently hybrid:

- `backend/` contains the most complete workflows in Spring MVC + Thymeleaf
- `frontend/` contains a partial React migration
- some React pages already exist, but many are thinner than the MVC originals
- several API endpoints are incomplete, missing, or inconsistent with the MVC flows

## Page Parity Matrix

| Area | Thymeleaf status | React status | API status | Migration status |
| --- | --- | --- | --- | --- |
| Auth: login/register/otp/forgot/reset | Present | Present | Present | Near parity |
| Student home | Present | Present | Present | Partial parity |
| Student group workspace | Present | Present | Present but thinner | Partial parity |
| Student project overview | Present | Present | Present but thinner | Partial parity |
| Student sprint and task board | Present | Missing | Missing/incomplete | Not migrated |
| Student project history list | Present | Missing | Missing | Not migrated |
| Student project history detail | Present | Missing | Missing | Not migrated |
| Student task code/file viewer | Present | Missing | Missing | Not migrated |
| Student notifications | Present | Present | Present | Good parity |
| Student profile/change password | Present | Present | Present | Good parity |
| Staff home | Present | Present | Placeholder metrics | Partial parity |
| Staff students management | Present | Present | Partial/inconsistent | Partial parity |
| Staff classroom list | Present | Present | Partial | Partial parity |
| Staff classroom detail | Present | Missing | Missing | Not migrated |
| Staff projects dashboard | Present | Present | Partial | Partial parity |
| Staff templates manager | Present | Present | Empty endpoint | Not migrated |
| Staff requests dashboard | Present | Present | Empty endpoint | Not migrated |
| Staff performance page | Present | Missing | Missing | Not migrated |
| Lecturer home | Present | Present | Present | Good parity |
| Lecturer projects list/review | Present | Present | Partial | Partial parity |
| Lecturer change-request approval | Present | Partial UI | Missing API actions | Not migrated |
| Lecturer progress detail | Present | Missing | Partial | Not migrated |

## High-Risk Gaps That Must Be Closed

### Student Project Workspace

The heaviest workflow still lives in MVC:

- request edit access
- update project content
- submit project for lecturer review
- create project change request
- create/update/delete/cancel sprint
- create/update/delete/cancel task
- start task
- submit task with files/folder/code/link/note
- unsubmit task
- approve/reject task review
- replan failed tasks
- finalize source/document links
- project history and score display
- code/file viewer

Main reference:

- `backend/src/main/java/com/example/pms/controller/StudentProjectController.java`
- `backend/src/main/resources/templates/student/project/home.html`
- `backend/src/main/resources/templates/student/project/tasks.html`
- `backend/src/main/resources/templates/student/project/history.html`
- `backend/src/main/resources/templates/student/project/history-detail.html`
- `backend/src/main/resources/templates/student/project/code-view.html`

### Staff Area

React pages exist, but backend API parity is not there yet:

- templates manager API returns an empty list
- requests API returns empty lists
- performance API route is not implemented in the API controller
- classroom detail/update flows are still MVC-first
- student create/update/import flows are inconsistent between MVC and API

Main reference:

- `backend/src/main/java/com/example/pms/controller/StaffHomeController.java`
- `backend/src/main/java/com/example/pms/controller/StaffClassController.java`
- `backend/src/main/java/com/example/pms/controller/StaffProjectController.java`
- `backend/src/main/java/com/example/pms/controller/api/ApiStaffController.java`

### Lecturer Area

The React layer does not cover the full lecturer workflow:

- progress detail page is missing in React
- change-request approve/reject API actions are missing in `ApiLecturerController`
- current progress API payload is too thin for full MVC parity

Main reference:

- `backend/src/main/java/com/example/pms/controller/LecturerProjectController.java`
- `backend/src/main/java/com/example/pms/controller/api/ApiLecturerController.java`
- `backend/src/main/resources/templates/lecturer/projects.html`
- `backend/src/main/resources/templates/lecturer/progress.html`

## API Backlog Required Before Full Cutover

### Student APIs

Must add or complete:

- `GET /api/student/project/tasks`
- `GET /api/student/project/history`
- `GET /api/student/project/history/{id}`
- `POST /api/student/project/request-edit`
- `POST /api/student/project/update-content` with multipart support
- `POST /api/student/project/submit-review`
- `POST /api/student/project/change-requests/create`
- `POST /api/student/project/sprints/create`
- `POST /api/student/project/sprints/{id}/update`
- `POST /api/student/project/sprints/{id}/delete`
- `POST /api/student/project/sprints/{id}/cancel`
- `POST /api/student/project/sprints/{id}/replan`
- `POST /api/student/project/tasks/create`
- `POST /api/student/project/tasks/{id}/update`
- `POST /api/student/project/tasks/{id}/delete`
- `POST /api/student/project/tasks/{id}/cancel`
- `POST /api/student/project/tasks/{id}/start`
- `POST /api/student/project/tasks/{id}/submit` with multipart support
- `POST /api/student/project/tasks/{id}/unsubmit`
- `POST /api/student/project/tasks/{id}/review`
- `GET /api/student/project/tasks/{id}/files/{fileId}`
- `GET /api/student/project/tasks/{id}/code`
- `POST /api/student/project/finalize-links`

### Staff APIs

Must add or complete:

- `GET /api/staff/classrooms/{id}`
- `POST /api/staff/classrooms/update`
- `POST /api/staff/students/update`
- `POST /api/staff/students/import`
- `GET /api/staff/projects/templates-manager`
- `POST /api/staff/projects/templates/create`
- `POST /api/staff/projects/templates/{id}/update`
- `POST /api/staff/projects/templates/{id}/delete`
- `POST /api/staff/projects/assign`
- `POST /api/staff/projects/create`
- `GET /api/staff/projects/requests`
- `POST /api/staff/projects/requests/{id}/approve`
- `POST /api/staff/projects/requests/{id}/reject`
- `POST /api/staff/projects/change-requests/{id}/approve`
- `POST /api/staff/projects/change-requests/{id}/reject`
- `GET /api/staff/projects/{id}/performance`

### Lecturer APIs

Must add or complete:

- `POST /api/lecturer/projects/change-requests/{id}/approve`
- `POST /api/lecturer/projects/change-requests/{id}/reject`
- richer `GET /api/lecturer/projects/{id}/progress` payload:
  - attachments
  - project files
  - overall member performance
  - sprint performance
  - task history

## Safe Migration Order

### Phase 1: Freeze and Inventory

- keep Thymeleaf pages untouched as reference
- build a page-by-page parity checklist
- document every missing API and payload difference

### Phase 2: Backend API Parity

- implement missing APIs without removing MVC pages
- reuse existing repositories/services/business rules
- refactor shared rules out of controllers where duplication is dangerous

### Phase 3: React Page Parity

- rebuild each missing page in React
- mirror the Thymeleaf UX first
- do not redesign yet unless requested after parity

### Phase 4: Verification

For each migrated page verify:

- same visible data
- same actions
- same role restrictions
- same validation messages
- same file behavior
- same status changes
- same locking rules

### Phase 5: Controlled Cutover

- route users to React pages only after parity is confirmed
- keep backend APIs and file endpoints
- keep Thymeleaf pages available behind a fallback path until final sign-off

### Phase 6: Thymeleaf Retirement

- remove MVC views only after all parity checks pass
- keep non-UI backend endpoints that still serve files or redirects

## Recommended First Implementation Slice

Start with the highest-risk gap first:

1. Student project task board
2. Student project history
3. Lecturer progress detail
4. Staff performance page
5. Staff templates and requests

Reason:

- these pages contain the most business rules
- they are the easiest places to lose functionality during migration
- once these are safe, the remaining dashboards become much easier

## Definition of Done for "Frontend Is The Only UI"

The migration is complete only when:

- every current Thymeleaf page has a React equivalent or an intentionally merged React flow
- every MVC action used by users has an API equivalent
- React covers all file upload/download flows
- React covers all request/review workflows
- role-based navigation works without broken routes
- Chat/widget visibility matches backend permissions
- no user-facing feature depends on opening a Thymeleaf page directly

## Immediate Next Step

Implement **student project API parity** first, then build the React versions of:

- task board
- history list
- history detail
- code/file viewer

This is the critical path for a no-loss migration.
