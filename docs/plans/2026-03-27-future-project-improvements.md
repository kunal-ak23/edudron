# Future Project Feature Improvements

These are documented improvements identified during the project feature implementation. They are not critical for launch but would add significant value.

---

## High Priority

### 1. Email Notification on Project Activate
When faculty activates a project, notify all students in enrolled sections with project details, events, and deadlines.
- Use the existing event service pattern
- Send via email service (if available) or in-app notifications

### 2. Rubric-Based Grading
Replace single marks field per event with structured rubric criteria.
- Example: "Code Quality: /10", "Presentation: /15", "Documentation: /5"
- Auto-calculate totals
- DB: `project_rubric_criteria` table linked to events
- UI: Grid of criteria x students for grading

### 3. Deadline Extensions Per Group
Allow faculty to set per-group deadline overrides for edge cases (medical, etc.).
- Add `deadline_override` column to `project_group` or `project_event_submission`
- UI: Override button per group in admin view

---

## Medium Priority

### 4. Peer Evaluation
Students rate their group members on participation/contribution after project completion.
- DB: `project_peer_evaluation` table (evaluator_id, evaluated_id, criteria, score, comment)
- Anonymous by default, faculty sees aggregated scores
- Weighting: faculty decides % of final grade from peer eval

### 5. Submission Similarity Check
Flag duplicate/similar submissions across groups.
- Compare submission URLs (exact match)
- Compare submission text (basic cosine similarity or Jaccard)
- Flag on admin dashboard: "Groups 3 and 7 have identical submission URLs"

### 6. Project Analytics Export
Export project data as Excel/PDF report.
- Attendance summary per student across all events
- Grade summary per student across all events
- Submission timeline per group
- Useful for academic records

### 7. Template Management Page
Dedicated page to browse, preview, and manage project templates.
- Currently: save-as-template button + list API exists
- Needed: `/projects/templates` page with template cards, preview, delete
- Apply template during bulk create (pre-populate events from template)

---

## Low Priority

### 8. Group Chat/Comments
Simple comment thread per group for team coordination.
- DB: `project_group_comment` table
- Lightweight — no real-time, just threaded comments
- Visible to group members + faculty

### 9. Progress Tracker (Student)
Visual checklist on student portal showing project progress.
- Problem statement assigned
- Events attended (3/4)
- Submissions made (2/3 events)
- Reviewed by faculty
- Gamification potential: completion percentage badge

### 10. Project Cloning
Clone an existing project (with all events, settings) to a new course/section.
- Different from templates: clones the full project including sections
- Useful for running same project across multiple semesters

### 11. Bulk Feedback
Faculty writes one feedback comment and applies to multiple groups at once.
- Useful for common issues: "All groups need to add ER diagrams"
- Select groups → write feedback → apply to all selected
