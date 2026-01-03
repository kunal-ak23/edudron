# CORS Fix for Frontend

## Issue
Frontend requests to backend are being blocked by CORS policy.

## Solution Applied

1. **Created GatewayConfig.java** - Added a Java configuration class for CORS in the Gateway service
2. **CORS Configuration** - Configured to allow all origins with credentials support

## Steps to Fix

### 1. Restart Gateway Service

The Gateway needs to be restarted to pick up the new CORS configuration:

```bash
cd /Users/kunalsharma/datagami/edudron

# Stop Gateway if running
./gradlew :gateway:bootStop

# Start Gateway
./gradlew :gateway:bootRun

# Or restart all services
./scripts/edudron.sh restart
```

### 2. Verify Gateway is Running

```bash
# Check Gateway health
curl http://localhost:8080/actuator/health

# Test CORS preflight
curl -X OPTIONS http://localhost:8080/auth/login \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -v
```

You should see `Access-Control-Allow-Origin` headers in the response.

### 3. Verify Frontend Configuration

Make sure your `.env.local` files are set correctly:

**apps/admin-dashboard/.env.local:**
```
NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
```

**apps/student-portal/.env.local:**
```
NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
```

### 4. Check Browser Console

After restarting the Gateway:
1. Open browser DevTools (F12)
2. Go to Network tab
3. Try making a request from the frontend
4. Check if CORS headers are present in the response

## CORS Configuration Details

The Gateway now has CORS configured to:
- Allow all origins (`*`)
- Allow all methods (GET, POST, PUT, DELETE, PATCH, OPTIONS)
- Allow all headers
- Support credentials (cookies, authorization headers)
- Cache preflight requests for 1 hour

## Troubleshooting

### If CORS errors persist:

1. **Clear browser cache** - Cached CORS responses might be blocking
2. **Check Gateway logs** - Look for CORS-related errors
3. **Verify Gateway is accessible** - `curl http://localhost:8080/actuator/health`
4. **Check firewall/proxy** - Ensure nothing is blocking port 8080
5. **Try different browser** - Rule out browser-specific issues

### Common Issues:

- **Gateway not running**: Start it with `./gradlew :gateway:bootRun`
- **Wrong port**: Verify Gateway is on port 8080
- **Cached response**: Hard refresh browser (Ctrl+Shift+R or Cmd+Shift+R)
- **Network issue**: Check if `localhost:8080` is accessible

## Testing

After restarting the Gateway, test the frontend:

1. Start frontend: `cd frontend && npm run dev`
2. Open Admin Dashboard: http://localhost:3000
3. Try to login
4. Check browser console for CORS errors

If you still see CORS errors, check:
- Gateway logs for errors
- Network tab in DevTools for actual request/response
- Verify the request is going to `http://localhost:8080`

