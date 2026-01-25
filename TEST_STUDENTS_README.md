# Test Student Generator

This script generates test students for all permutations (Institute → Class → Section) in your Test University.

## Overview

**Generated Structure:**
- **Institute:** Test Uni Inst (`01KF5RV0T1D39E20016FB874CB`)
- **Class:** Computer engineering (`01KF5Z83HQ0A66BDD01A0C3DB4`)
- **Sections:** 
  - Morning (`01KF5Z83J9CE865416EBF08497`) - 10 students
  - Evening (`01KF5Z83JE92BAFC69C1D130B3`) - 10 students
- **Total:** 20 students

## Email Format

All students follow the format: `student<no>@testuni.com`

Examples:
- `student1@testuni.com`
- `student2@testuni.com`
- ...
- `student20@testuni.com`

## Usage

### 1. Generate SQL File (Default)

```bash
python3 generate_test_students.py > test_students.sql
```

This generates a complete SQL file with:
- `INSERT` statements for the `student.students` table
- `INSERT` statements for the `student.enrollments` table
- Verification queries to check the data

### 2. Generate JSON File

```bash
python3 generate_test_students.py --json > test_students.json
```

This generates a JSON array with all student data including their associations.

### 3. Generate Python Dictionary

```bash
python3 generate_test_students.py --python
```

This outputs a Python dictionary that you can use programmatically.

## Before Running the SQL

**IMPORTANT:** You must replace these placeholders in the generated SQL:

1. **`YOUR_CLIENT_ID_HERE`** - Replace with your actual tenant/client UUID
2. **`YOUR_COURSE_ID_HERE`** - Replace with your actual course UUID (if applicable)

### Find Your Client ID

Run this query to find your client_id:

```sql
SELECT DISTINCT client_id FROM student.enrollments ORDER BY client_id;
```

Or from your application configuration/environment variables.

### Replace Placeholders

You can use a text editor or command line:

```bash
# Using sed (macOS/Linux)
sed -i '' 's/YOUR_CLIENT_ID_HERE/your-actual-uuid/g' test_students.sql
sed -i '' 's/YOUR_COURSE_ID_HERE/your-actual-course-uuid/g' test_students.sql

# Or manually in your favorite editor
```

## Execute the SQL

Once you've replaced the placeholders:

```bash
psql -h your-host -U your-user -d your-database -f test_students.sql
```

Or copy and paste the SQL into your database client.

## Verification

After running the SQL, verify the data with these queries:

### Count students by section

```sql
SELECT 
    e.batch_id AS section_id,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID'
    AND e.class_id = '01KF5Z83HQ0A66BDD01A0C3DB4'
GROUP BY e.batch_id
ORDER BY student_count DESC;
```

**Expected Result:**
- Morning section: 10 students
- Evening section: 10 students

### List all created students

```sql
SELECT 
    s.id AS student_id,
    s.email,
    s.first_name,
    s.last_name,
    e.batch_id AS section_id
FROM student.students s
INNER JOIN student.enrollments e ON e.student_id = s.id
WHERE s.client_id = 'YOUR_CLIENT_ID'
    AND s.email LIKE '%@testuni.com'
ORDER BY s.email;
```

## Customization

You can modify the script to change:

- `STUDENTS_PER_SECTION` - Number of students per section (default: 10)
- `EMAIL_DOMAIN` - Email domain (default: "testuni.com")
- Add more sections to the `SECTIONS` array
- Modify student name format in the `generate_students_sql()` function

## Student Data Structure

Each student has:
- **id:** ULID-style unique identifier
- **email:** student<no>@testuni.com
- **first_name:** Student<no>
- **last_name:** Section name (Morning/Evening)
- **phone:** +1555<4-digit-number>
- **is_active:** true

Each enrollment has:
- **student_id:** Links to student
- **institute_id:** Test Uni Inst
- **class_id:** Computer engineering
- **batch_id:** Section ID (Morning or Evening)
- **course_id:** Your course UUID

## Files Generated

- **`generate_test_students.py`** - The main script
- **`test_students.sql`** - SQL INSERT statements
- **`test_students.json`** - JSON data for reference
- **`TEST_STUDENTS_README.md`** - This file

## Troubleshooting

### Issue: Students not showing in sections

**Solution:** Check that the `batch_id` in enrollments matches the section IDs:
- Morning: `01KF5Z83J9CE865416EBF08497`
- Evening: `01KF5Z83JE92BAFC69C1D130B3`

### Issue: Duplicate key errors

**Solution:** The script generates new ULID-style IDs each time. If you run it multiple times, you'll get different IDs. Clear existing test data before re-running.

### Issue: Foreign key constraint violations

**Solution:** Ensure that:
1. The institute, class, and sections exist in the database
2. The client_id matches your tenant UUID
3. The course_id is valid (or set to NULL if not needed)

## Clean Up Test Data

To remove all test students:

```sql
-- Delete enrollments first (foreign key constraint)
DELETE FROM student.enrollments 
WHERE client_id = 'YOUR_CLIENT_ID'
    AND student_id IN (
        SELECT id FROM student.students 
        WHERE email LIKE '%@testuni.com'
    );

-- Then delete students
DELETE FROM student.students 
WHERE client_id = 'YOUR_CLIENT_ID'
    AND email LIKE '%@testuni.com';
```

## Support

If you need to generate students for different institutes, classes, or sections:

1. Update the constants at the top of `generate_test_students.py`
2. Re-run the script to generate new SQL/JSON files
