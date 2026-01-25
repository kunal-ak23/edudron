# Bulk Import Students - Ready to Use! 🚀

## ✅ Generated Files

Two files have been created that are **fully compatible** with your bulk import system:

1. **`bulk_import_students.csv`** (3.7 KB) - CSV format
2. **`bulk_import_students.xlsx`** (7.9 KB) - Excel format with instructions sheet

Both files contain **30 test students** with the exact format required by your bulk import API.

## 👥 Student Distribution

### ✅ Exactly as Requested:
- **10 students** → Morning Section (`student1@testuni.com` - `student10@testuni.com`)
- **10 students** → Evening Section (`student11@testuni.com` - `student20@testuni.com`)  
- **10 students** → Computer engineering Class ONLY - no section (`student21@testuni.com` - `student30@testuni.com`)

**Total: 30 students**

## 📋 File Format

The files follow the exact format expected by your bulk import API:

```csv
name,email,phone,password,instituteId,classId,sectionId,courseId
Student1 Morning,student1@testuni.com,+15550001,,01KF5RV0T1D39E20016FB874CB,01KF5Z83HQ0A66BDD01A0C3DB4,01KF5Z83J9CE865416EBF08497,
Student11 Evening,student11@testuni.com,+15550011,,01KF5RV0T1D39E20016FB874CB,01KF5Z83HQ0A66BDD01A0C3DB4,01KF5Z83JE92BAFC69C1D130B3,
Student21 ClassOnly,student21@testuni.com,+15550021,,01KF5RV0T1D39E20016FB874CB,01KF5Z83HQ0A66BDD01A0C3DB4,,
```

### 🔑 Key Features:
- ✅ **Password field is empty** - passwords will be auto-generated during import
- ✅ **sectionId is empty** for class-level only students (rows 22-31)
- ✅ **sectionId is set** for Morning/Evening students (rows 2-21)
- ✅ **All required fields** are populated (name, email, instituteId)
- ✅ **Phone numbers** are unique and formatted

## 🚀 How to Import

### Step 1: Open Admin Dashboard
Navigate to your admin dashboard at:
```
http://your-domain/students/import
```

### Step 2: Configure Import Options
In the admin dashboard, you'll see these options:

- ✅ **Auto-generate passwords** - Enable this (recommended)
- ☐ **Update existing students** - Enable if you want to update students with matching emails
- ☐ **Auto-enroll in courses** - Keep disabled (courseId is empty in file)

### Step 3: Upload File
1. Click the upload area or drag and drop
2. Select either `bulk_import_students.csv` or `bulk_import_students.xlsx`
3. Click **"Import Students"**

### Step 4: Review Results
The import will show you:
- Total rows processed
- Successful imports
- Failed imports (with error messages)
- Detailed results for each student

## 📊 Expected Results

After successful import, you should see:

```
✅ 30 of 30 students imported successfully
```

### Verification Queries

Run these SQL queries to verify the import:

```sql
-- Count by section
SELECT 
    COALESCE(e.batch_id, 'NO_SECTION') AS section,
    COUNT(DISTINCT e.student_id) AS student_count
FROM student.enrollments e
WHERE e.institute_id = '01KF5RV0T1D39E20016FB874CB'
    AND e.class_id = '01KF5Z83HQ0A66BDD01A0C3DB4'
GROUP BY e.batch_id;
```

**Expected:**
- Morning section: 10
- Evening section: 10
- NO_SECTION (class-level): 10

## 📁 File Details

### CSV File (`bulk_import_students.csv`)
- Format: Standard CSV with headers
- Size: 3.7 KB
- Rows: 31 (1 header + 30 students)
- Encoding: UTF-8
- Compatible with: All spreadsheet applications

### Excel File (`bulk_import_students.xlsx`)
- Format: Excel 2007+ (.xlsx)
- Size: 7.9 KB
- Sheets: 
  - **"Import Instructions"** - Detailed guide with all IDs and field descriptions
  - **"Students Import"** - Actual data with color-coded rows
- Features:
  - Color-coded rows (purple=Morning, yellow=Evening, green=Class-only)
  - Frozen header row
  - Auto-sized columns
  - Professional formatting

## 🔄 Regenerate Files

To generate new files with different data:

```bash
python3 generate_bulk_import_students.py
```

You can customize the script by editing these variables:
```python
STUDENTS_PER_GROUP = 10  # Change number of students per group
EMAIL_DOMAIN = "testuni.com"  # Change email domain
COURSE_ID = ""  # Add course ID if needed
```

## 🎯 What Happens During Import

The bulk import service will:

1. ✅ Create user accounts in the identity service
2. ✅ Auto-generate passwords (since password field is empty)
3. ✅ Assign students to the specified institute
4. ✅ Create enrollments linking students to classes/sections
5. ✅ For Morning/Evening students: Associate with their section
6. ✅ For ClassOnly students: Associate with class only (no section)
7. ✅ Auto-enroll in any courses assigned to those sections/classes

## ⚠️ Important Notes

### Email Uniqueness
- All emails must be unique across your tenant
- If an email already exists:
  - With **"Update existing students"** disabled: Row will fail with error
  - With **"Update existing students"** enabled: User will be updated

### Password Generation
- Passwords are auto-generated as secure random strings
- Generated passwords are shown in the import results
- You can manually set passwords in the CSV if needed (just fill the password column)

### Section Association
- Students with `sectionId` will be associated with that specific section
- Students without `sectionId` (empty field) will be associated with the class only
- Class-level students can see courses assigned to ANY section in that class

### Course Enrollment
- The `courseId` field is optional and empty in these files
- Students will be auto-enrolled in courses assigned to their section/class
- To manually enroll in a specific course, add the courseId to the CSV

## 🧹 Clean Up Test Data

To remove all test students after testing:

```sql
-- Get student IDs
SELECT id FROM identity.users WHERE email LIKE '%@testuni.com';

-- Delete enrollments first
DELETE FROM student.enrollments 
WHERE student_id IN (
    SELECT id FROM identity.users WHERE email LIKE '%@testuni.com'
);

-- Delete users
DELETE FROM identity.users WHERE email LIKE '%@testuni.com';
```

## 📚 Field Reference

| Field | Required | Description | Example |
|-------|----------|-------------|---------|
| `name` | ✅ Yes | Full name of student | "Student1 Morning" |
| `email` | ✅ Yes | Unique email address | "student1@testuni.com" |
| `phone` | ⚪ Optional | Phone number | "+15550001" |
| `password` | ⚪ Optional | Password (leave empty to auto-generate) | "" |
| `instituteId` | ✅ Yes | Institute ID | "01KF5RV0T1D39E20016FB874CB" |
| `classId` | 🟡 Recommended | Class ID | "01KF5Z83HQ0A66BDD01A0C3DB4" |
| `sectionId` | ⚪ Optional | Section ID (empty for class-level only) | "01KF5Z83J9CE865416EBF08497" |
| `courseId` | ⚪ Optional | Course to enroll in | "" |

## 🎨 Excel Features

The Excel file includes:

### Sheet 1: Import Instructions
- Complete field descriptions
- All IDs for Test University
- Step-by-step import guide
- Row distribution information

### Sheet 2: Students Import
- Actual import data
- Color-coded rows:
  - 🟣 **Light Purple** - Morning section students (rows 2-11)
  - 🟡 **Light Yellow** - Evening section students (rows 12-21)
  - 🟢 **Light Green** - Class-level only students (rows 22-31)
- Professional header formatting
- Frozen header row for easy scrolling
- Auto-sized columns

## ✅ Checklist

Before importing:
- [ ] Files generated: `bulk_import_students.csv` and `bulk_import_students.xlsx`
- [ ] Admin dashboard is accessible
- [ ] You have SYSTEM_ADMIN or TENANT_ADMIN role
- [ ] Test University structure exists (Institute, Class, Sections)
- [ ] "Auto-generate passwords" option will be enabled in UI

After importing:
- [ ] All 30 students imported successfully
- [ ] Morning section has 10 students
- [ ] Evening section has 10 students  
- [ ] Class-level has 10 students (no section)
- [ ] Students can log in (using generated passwords from import results)

## 🔗 Related Files

- **`generate_bulk_import_students.py`** - Generator script
- **`bulk_import_students.csv`** - CSV format (ready to import)
- **`bulk_import_students.xlsx`** - Excel format (ready to import)
- **`BULK_IMPORT_README.md`** - This file

## 📞 Support

If you encounter any issues during import:

1. Check the import results table for specific error messages
2. Verify that the Institute, Class, and Sections exist and are active
3. Ensure email addresses are unique
4. Check that you have the correct role (SYSTEM_ADMIN or TENANT_ADMIN)

---

**🎉 Your bulk import files are ready! Just upload and import in the admin dashboard.**
