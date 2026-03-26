# Bulk Project Setup — Design

**Date:** 2026-03-26
**Status:** Approved

## Goal

One-click project setup: select a course, pick sections, set group size, and create a fully configured project with student groups and problem statements assigned — all in one flow.

## Core Model

One **Project** = one course-level assignment spanning multiple sections.

- All students from selected sections are pooled and divided into groups
- Problem statements from the project question bank are distributed round-robin across all groups
- Each group makes one submission

Example: "BFSI Capstone Project"
- Course: "Comprehensive Industry-Ready Program in BFSI"
- 3 sections selected → 90 students → 30 groups of 3
- 45 problem statements → round-robin across 30 groups (no reuse needed)

## User Flow

Page: `/projects/bulk-create` (wizard/stepper UI)

### Step 1: Course Selection
- Dropdown of all courses
- On selection, fetch and display: "X problem statements available for this course"
- If 0 statements, show warning with link to add them

### Step 2: Section Selection
- Show only classes/sections that have students enrolled in the selected course
- Multi-select with checkboxes
- Display student count per section
- Show total: "X students across Y sections"
- If no sections found, show message with guidance

### Step 3: Group Configuration
- Input: group size (e.g., 3 students per group)
- Auto-calculate and display: "X students → Y groups"
- Show round-robin preview: "Y groups, Z problem statements available (reuse starts at group Z+1)" or "no reuse needed"

### Step 4: Project Details
- Title (required)
- Description (optional)
- Max Marks (default 100)
- Submission Cutoff (datetime)
- Allow Late Submission (toggle)

### Step 5: Review & Create
- Summary of everything
- Single "Create Project" button

### What happens on create:
1. Create one project (DRAFT status, linked to courseId, stores sectionIds)
2. Pull all enrolled students from selected sections
3. Generate groups of specified size (round-robin distribution across students)
4. Fetch problem statements for the course from question bank
5. Assign statements round-robin across all groups
6. Return to project detail page

## Backend Changes

### New endpoint
`POST /api/projects/bulk-setup` on ProjectController

Request:
```json
{
  "courseId": "string",
  "sectionIds": ["string"],
  "groupSize": 3,
  "title": "string",
  "description": "string (optional)",
  "maxMarks": 100,
  "submissionCutoff": "ISO datetime (optional)",
  "lateSubmissionAllowed": false
}
```

Response: `ProjectDTO` with groups and assignments populated.

Logic (single @Transactional method):
1. Create Project entity
2. Fetch enrolled studentIds from all sectionIds (via EnrollmentRepository)
3. Shuffle and divide into groups of `groupSize`
4. Save ProjectGroup + ProjectGroupMember entities
5. Fetch question bank entries for courseId (via RestTemplate to content service)
6. Assign problem statements round-robin to groups
7. Return full ProjectDTO

### New query
Find sections that have enrollments for a given courseId — needed for Step 2 of the wizard.

Option: query enrollments by courseId, extract distinct sectionIds (batchIds), then load those sections.

### No entity/migration changes
- Project.sectionId already exists — store comma-separated sectionIds or just the first one (groups track student membership regardless)
- All other entities (ProjectGroup, ProjectGroupMember, etc.) are unchanged

## Frontend Changes

### New page: `/projects/bulk-create`
- Stepper/wizard with 5 steps
- Uses existing API clients: coursesApi, classesApi, sectionsApi, enrollmentsApi, projectQuestionsApi, projectsApi

### Label changes
- "Question Bank" → "Problem Statements" in project-related UI (sidebar label can stay, but page headers/descriptions use "Problem Statements")

### Projects list page
- Add "Bulk Create" button alongside existing "Create Project"

## Adding Sections to Existing Project

Admin can add more sections to an existing project at any time:
- Project detail page → "Add Sections" action
- Shows sections not already included, filtered by course enrollment
- On confirm: pulls new students, generates new groups (appended, existing untouched), assigns problem statements continuing round-robin from last assignment
- Existing groups, assignments, submissions, and grades are never modified

### Backend
- `POST /api/projects/{id}/add-sections` — accepts `{ sectionIds: [], groupSize: int }`
- Fetches students from new sections only (excluding any already in a group for this project)
- Appends new groups with sequential group numbers
- Assigns statements round-robin starting after the last assigned index

## Existing single-create page fix
- Fix Section dropdown to show student sections (classes → sections), not course lectures
- Filter sections by course enrollment (same query as bulk flow)
