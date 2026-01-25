# UI Analytics Access Guide

## 🎨 How to Access Section and Class Analytics in the UI

This guide shows you all the ways to access the new section and class analytics in the Edudron admin dashboard.

## 📍 Access Points

### 1. **Main Analytics Dashboard**

Navigate to: **`http://localhost:3000/analytics`**

You'll see three main cards:
- **Course Analytics** - Select from dropdown, view quick stats
- **Section Analytics** - Quick dropdown to select any section
- **Class Analytics** - Button to go to classes list

**Features:**
- ✅ Quick section selector dropdown
- ✅ Shows count of available sections
- ✅ Direct navigation to section analytics
- ✅ Visual cards with color-coded borders

### 2. **From Section Detail Page**

Navigate to: **`/sections/{sectionId}`**

Look for the **"View Analytics"** button in the top-right corner (next to "Back to Sections" button).

**What you'll see:**
- Button with bar chart icon
- Clicking takes you to full section analytics page
- Shows all courses aggregated for that section

### 3. **From Class Detail Page**

Navigate to: **`/classes/{classId}`**

Look for the **"View Analytics"** button in the top-right corner (next to "Back to Classes" button).

**What you'll see:**
- Button with bar chart icon
- Clicking takes you to full class analytics page
- Shows all sections and courses aggregated for that class
- Includes section comparison table

### 4. **From Classes List Page**

Navigate to: **`http://localhost:3000/classes`** (NEW!)

See all classes across all institutes in one place.

**Features:**
- 📊 Stats cards showing total, active, and inactive classes
- 🔍 Search by name, code, institute, grade, or level
- 📈 Chart icon button for quick access to class analytics
- 🔗 Clickable institute names to view institute details
- View all classes from all institutes in one table

### 5. **Direct URL Navigation**

You can also navigate directly to analytics pages:

**Section Analytics:**
```
http://localhost:3000/analytics/sections/{sectionId}
```

**Class Analytics:**
```
http://localhost:3000/analytics/classes/{classId}
```

**All Classes List:**
```
http://localhost:3000/classes
```

Replace `{sectionId}` or `{classId}` with the actual ID.

## 📊 What's Available on Each Page

### Section Analytics Page
**URL:** `/analytics/sections/[sectionId]`

**Shows:**
- 📈 **5 Overview Cards:**
  - Total Courses (how many courses assigned)
  - Total Sessions (all viewing sessions)
  - Active Students (unique engaged students)
  - Avg Time/Lecture
  - Completion Rate

- 📚 **Course Breakdown Table:**
  - Performance metrics for each course in the section
  - Sessions, students, completion rate per course
  - Color-coded completion badges

- 📅 **Activity Timeline Chart:**
  - Daily engagement trends
  - Sessions and unique students per day

- 🎓 **Lecture Engagement Table:**
  - Top 10 lectures by views (across all courses)
  - Views, viewers, duration, completion, skip rate

- ⚠️ **Skipped Lectures Alert:**
  - Lectures with >50% skip rate
  - Reasons for skipping (duration threshold)

### Class Analytics Page
**URL:** `/analytics/classes/[classId]`

**Shows:**
- 📈 **6 Overview Cards:**
  - Total Sections
  - Total Courses
  - Total Sessions
  - Active Students
  - Avg Time/Lecture
  - Completion Rate

- 🔄 **Section Comparison Table:** (Unique to class analytics!)
  - Compare all sections in the class
  - Total students, active students, completion rate per section
  - Clickable rows - click to view individual section analytics

- 📚 **Course Breakdown Table:**
  - Performance across all sections for each course
  - Aggregated metrics

- 📅 **Activity Timeline Chart:**
  - Daily engagement across all sections

- 🎓 **Top Lectures Table:**
  - Most viewed lectures across all sections and courses

- ⚠️ **Skipped Lectures Alert:**
  - High skip rate lectures across the entire class

## 🎯 Quick Start Guide

### Step 1: Start the Application

```bash
# Backend
cd /Users/kunalsharma/datagami/edudron
./scripts/edudron.sh

# Frontend (in another terminal)
cd frontend/apps/admin-dashboard
npm run dev
```

### Step 2: Navigate to Analytics

1. **Open browser:** `http://localhost:3000`
2. **Login** with your admin credentials
3. **Go to Analytics:** Click "Analytics" in the navigation menu or go to `/analytics`

### Step 3: Choose Your View

**Option A - Section Analytics:**
1. From analytics dashboard, use the "Section Analytics" card dropdown
2. Select a section from the list
3. View comprehensive analytics aggregated across all courses

**Option B - Class Analytics:**
1. Go to Classes page at `http://localhost:3000/classes`
2. Click the chart icon (📊) next to any class for instant analytics
   OR click "View" to see class details, then click "View Analytics" button
3. See aggregated analytics across all sections and courses

**Option C - Browse Sections/Classes:**
1. Navigate to any section detail page
2. Click "View Analytics" button
3. See full analytics for that section

## 🎨 UI Features

### Color Coding
- ✅ **Green badges:** Good performance (≥70% completion)
- 🟡 **Gray badges:** Lower performance (<70% completion)
- 🔴 **Red badges:** High skip rates (>50%)

### Interactive Elements
- **Section comparison rows:** Clickable to drill down to individual section
- **Dropdowns:** Quick navigation from analytics dashboard
- **Charts:** Interactive activity timeline charts
- **Loading states:** Smooth spinners while data loads

### Responsive Design
- Works on desktop and mobile
- Tables scroll horizontally on small screens
- Cards stack vertically on mobile

## 💡 Tips

1. **Multi-course aggregation:** Remember that section/class analytics aggregate ALL courses, not just one
2. **Course breakdown:** Use this to see which individual course is performing well
3. **Section comparison:** Great for identifying which section needs attention
4. **Cache clearing:** If data seems stale, use the cache clear endpoint (backend only for now)
5. **Performance:** First load may take 1-3 seconds, subsequent loads <50ms (cached)

## 🔗 Related Pages

- **Main Analytics Dashboard:** `/analytics`
- **Course Analytics:** `/analytics/courses/{courseId}`
- **Section Analytics:** `/analytics/sections/{sectionId}` ⭐ NEW
- **Class Analytics:** `/analytics/classes/{classId}` ⭐ NEW
- **All Classes List:** `/classes` ⭐ NEW (just created!)
- **Classes by Institute:** `/institutes/{instituteId}/classes`
- **Sections by Class:** `/classes/{classId}/sections`

## 📱 Navigation Flow Examples

### Flow 1: From Dashboard
```
Dashboard → Analytics → Section dropdown → Section Analytics Page
```

### Flow 2: From Class
```
Classes → Class Detail → "View Analytics" button → Class Analytics Page
  → Section Comparison table → Click section → Section Analytics Page
```

### Flow 3: From Section
```
Classes → Sections → Section Detail → "View Analytics" button → Section Analytics Page
```

## 🐛 Troubleshooting

**Q: No data showing?**
- Ensure students are enrolled in the section/class
- Verify courses are assigned to the section/class
- Check that students have watched some lectures (lecture view sessions exist)

**Q: Slow loading?**
- First load takes longer (1-3 seconds) - this is normal
- Subsequent loads should be fast (<50ms) due to caching
- Check network tab for any API errors

**Q: Analytics button not visible?**
- Refresh the page after deployment
- Clear browser cache if needed
- Check that you're on the class/section detail page (not list page)

## 🚀 What's Next?

You can now:
- ✅ View analytics for any section (batch)
- ✅ View analytics for any class
- ✅ Compare sections within a class
- ✅ See per-course breakdown
- ✅ Track daily engagement trends
- ✅ Identify skipped lectures

Enjoy your new analytics! 🎉
