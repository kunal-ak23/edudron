# Test Students Excel Generator

## 📊 Generated File: `test_students.xlsx`

This Excel file contains **30 test students** organized into 3 groups with the email format `student<no>@testuni.com`.

## 👥 Student Distribution

### ✅ Generated Students:
- **10 students** → Morning Section (`01KF5Z83J9CE865416EBF08497`)
- **10 students** → Evening Section (`01KF5Z83JE92BAFC69C1D130B3`)
- **10 students** → Computer engineering Class only (no section)

**Total: 30 students** (student1@testuni.com to student30@testuni.com)

## 📑 Excel Worksheets

The Excel file contains 6 worksheets:

### 1. **Summary** 
Overview of the generated data with:
- Generation timestamp
- Institute, Class, and Section IDs
- Student counts by group
- Email format information

### 2. **All Students**
Complete list of all 30 students with full details:
- Student ID, Email, Name, Phone
- Institute, Class, Section associations
- Association type (Section Level or Class Level Only)
- Enrollment IDs

### 3. **Morning Section**
10 students enrolled in Morning section
- Email: student1@testuni.com to student10@testuni.com
- Last Name: "Morning"

### 4. **Evening Section**
10 students enrolled in Evening section
- Email: student11@testuni.com to student20@testuni.com
- Last Name: "Evening"

### 5. **Class Level Only**
10 students enrolled directly in Computer engineering class (no section)
- Email: student21@testuni.com to student30@testuni.com
- Last Name: "ClassOnly"
- Section ID: NULL

### 6. **SQL Inserts**
Ready-to-use SQL INSERT statements for:
- `student.students` table
- `student.enrollments` table

## 🔑 Important: Before Using SQL

Replace these placeholders in the SQL worksheet:
1. **`YOUR_CLIENT_ID_HERE`** → Your tenant/client UUID
2. **`YOUR_COURSE_ID_HERE`** → Your course UUID

## 🚀 Usage

### Option 1: Use Excel Data Directly
Open `test_students.xlsx` and use the data for:
- Manual data entry
- Importing via your application's import feature
- Reference for API calls

### Option 2: Execute SQL Statements
1. Open the **"SQL Inserts"** worksheet
2. Replace `YOUR_CLIENT_ID_HERE` and `YOUR_COURSE_ID_HERE`
3. Copy all SQL statements
4. Execute in your database client

```bash
# Example using psql
psql -h host -U user -d database -c "$(cat sql_from_excel.sql)"
```

## 📧 Email Format

All students follow the pattern:
- `student1@testuni.com` (Morning section, first student)
- `student10@testuni.com` (Morning section, last student)
- `student11@testuni.com` (Evening section, first student)
- `student20@testuni.com` (Evening section, last student)
- `student21@testuni.com` (Class level only, first student)
- `student30@testuni.com` (Class level only, last student)

## 🎨 Visual Features

The Excel file includes:
- **Color-coded rows** for easy identification:
  - Light purple: Morning section students
  - Light yellow: Evening section students
  - Light green: Class level only students
- **Header formatting** with blue background and white text
- **Frozen header row** for easy scrolling
- **Auto-sized columns** for readability

## 🔄 Regenerate Students

To generate a new Excel file with different data:

```bash
# Default filename (test_students.xlsx)
python3 generate_students_excel.py

# Custom filename
python3 generate_students_excel.py my_students.xlsx
```

Each run generates fresh ULID-style IDs for all students and enrollments.

## 🛠️ Customization

Edit `generate_students_excel.py` to modify:

```python
STUDENTS_PER_GROUP = 10  # Change to generate more/fewer students per group
EMAIL_DOMAIN = "testuni.com"  # Change email domain
CLIENT_ID = "YOUR_CLIENT_ID_HERE"  # Set your default client ID
COURSE_ID = "YOUR_COURSE_ID_HERE"  # Set your default course ID
```

## 📊 Data Structure

### Students Table Fields:
- `id` - ULID-style unique identifier
- `email` - student<no>@testuni.com
- `first_name` - Student<no>
- `last_name` - Morning/Evening/ClassOnly
- `phone` - +1555<4-digit-number>
- `is_active` - true

### Enrollments Table Fields:
- `id` - ULID-style unique identifier
- `student_id` - Links to student
- `institute_id` - Test Uni Inst
- `class_id` - Computer engineering
- `batch_id` - Section ID (or NULL for class-level only)
- `course_id` - Your course UUID

## ✅ Verification Queries

After importing, verify with these SQL queries:

### Count by Association Type
```sql
SELECT 
    CASE 
        WHEN e.batch_id IS NULL THEN 'Class Level Only'
        ELSE 'Section Level'
    END AS association_type,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID'
    AND e.class_id = '01KF5Z83HQ0A66BDD01A0C3DB4'
GROUP BY association_type;
```

**Expected:**
- Class Level Only: 10
- Section Level: 20

### Count by Section
```sql
SELECT 
    COALESCE(e.batch_id, 'NO_SECTION') AS section_id,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.enrollments e
WHERE e.client_id = 'YOUR_CLIENT_ID'
    AND e.class_id = '01KF5Z83HQ0A66BDD01A0C3DB4'
GROUP BY e.batch_id;
```

**Expected:**
- Morning section: 10
- Evening section: 10
- NO_SECTION (class-level): 10

## 🧹 Clean Up Test Data

To remove all test students:

```sql
-- Delete enrollments first (foreign key constraint)
DELETE FROM student.enrollments 
WHERE student_id IN (
    SELECT id FROM student.students 
    WHERE email LIKE '%@testuni.com'
);

-- Then delete students
DELETE FROM student.students 
WHERE email LIKE '%@testuni.com';
```

## 📝 Files

- **`generate_students_excel.py`** - Main generator script
- **`test_students.xlsx`** - Generated Excel file (30 students)
- **`EXCEL_STUDENTS_README.md`** - This documentation

## 🎯 Key Features

✅ 30 students with unique IDs and emails  
✅ 3 different association types (Morning/Evening sections + Class level)  
✅ Color-coded Excel worksheets for easy navigation  
✅ Ready-to-use SQL INSERT statements  
✅ Complete data validation and verification queries  
✅ Easy customization and regeneration  

---

**Need help?** Check the SQL Inserts worksheet in the Excel file or refer to the verification queries above.
