# API Reference

## Base URL

```
http://localhost:63342/api/intellij-actions
```

## Endpoints

### Execute Action(s)

Execute one or more IntelliJ actions.

**Endpoint:** `GET /execute`

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `action` | string | No* | Single action ID to execute |
| `actions` | string | No* | Comma-separated list of action IDs |
| `delay` | integer | No | Delay in milliseconds between actions (default: 100) |

\* Either `action` or `actions` must be provided

**Examples:**

```bash
# Single action
GET /api/intellij-actions/execute?action=ReformatCode

# Multiple actions
GET /api/intellij-actions/execute?actions=SaveAll,ReformatCode,OptimizeImports

# With delay
GET /api/intellij-actions/execute?actions=SaveAll,ReformatCode&delay=500
```

**Response:**

```json
{
  "success": true,
  "action": "ReformatCode",
  "message": "Action triggered successfully",
  "error": null
}
```

For multiple actions:
```json
{
  "success": true,
  "actions": ["SaveAll", "ReformatCode", "OptimizeImports"],
  "results": [
    {
      "success": true,
      "action": "SaveAll",
      "message": "Action triggered successfully",
      "error": null
    },
    {
      "success": true,
      "action": "ReformatCode",
      "message": "Action triggered successfully",
      "error": null
    },
    {
      "success": true,
      "action": "OptimizeImports",
      "message": "Action triggered successfully",
      "error": null
    }
  ]
}
```

**Error Response:**

```json
{
  "success": false,
  "action": "InvalidAction",
  "message": null,
  "error": "Action not found: InvalidAction"
}
```

---

### Check Action

Check if an action exists and is available.

**Endpoint:** `GET /check`

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `action` | string | Yes | Action ID to check |

**Example:**

```bash
GET /api/intellij-actions/check?action=ReformatCode
```

**Response:**

```json
{
  "exists": true,
  "actionId": "ReformatCode",
  "available": true
}
```

**Error Response:**

```json
{
  "exists": false,
  "actionId": "InvalidAction",
  "available": false
}
```

---

### List Actions

Get a list of all available action IDs.

**Endpoint:** `GET /list`

**Parameters:** None

**Example:**

```bash
GET /api/intellij-actions/list
```

**Response:**

```json
{
  "actions": [
    "About",
    "ActivateProjectToolWindow",
    "ActiveToolwindowGroup",
    "AddFrameworkSupport",
    "AddToFavorites",
    "AnalyzeActions",
    "Annotate",
    "AnonymousToInner",
    "AutoIndentLines",
    "Back",
    "BuildArtifact",
    "BuildProject",
    // ... hundreds more actions
  ],
  "count": 2847
}
```

---

### Health Check

Check if the plugin is running and healthy.

**Endpoint:** `GET /health`

**Parameters:** None

**Example:**

```bash
GET /api/intellij-actions/health
```

**Response:**

```json
{
  "status": "healthy",
  "plugin": "intellij-action-executor",
  "version": "1.0.7",
  "timestamp": "2025-08-14T15:30:45.123Z"
}
```

## Response Format

All responses are JSON with the following structure:

### Success Response

```json
{
  "success": true,
  "action": "ActionId",
  "message": "Success message",
  "error": null
}
```

### Error Response

```json
{
  "success": false,
  "action": "ActionId",
  "message": null,
  "error": "Error description"
}
```

## Status Codes

| Code | Description |
|------|-------------|
| 200 | Success - Action executed or request processed |
| 400 | Bad Request - Invalid parameters or action not found |
| 500 | Internal Server Error - Plugin error |

Note: IntelliJ's REST API may wrap responses in HTML error pages with 400 status code even for successful requests. The actual JSON response is embedded in the HTML.

## Error Handling

### Common Errors

| Error | Description | Solution |
|-------|-------------|----------|
| `Action not found` | The specified action ID doesn't exist | Verify action ID using `/list` or `/check` |
| `Action is disabled` | Action is not available in current context | Ensure proper context (e.g., file open) |
| `No action or actions parameter` | Missing required parameter | Provide either `action` or `actions` |
| `Connection refused` | IntelliJ not running or plugin disabled | Start IntelliJ and enable plugin |

### Error Response Examples

Action not found:
```json
{
  "success": false,
  "action": "NonExistentAction",
  "message": null,
  "error": "Action not found: NonExistentAction"
}
```

Action disabled:
```json
{
  "success": false,
  "action": "RunClass",
  "message": null,
  "error": "Action is disabled in current context"
}
```

## Usage Examples

### cURL

```bash
# Execute action
curl -X GET "http://localhost:63342/api/intellij-actions/execute?action=ReformatCode"

# Check action
curl -X GET "http://localhost:63342/api/intellij-actions/check?action=ReformatCode"

# List all actions
curl -X GET "http://localhost:63342/api/intellij-actions/list"

# Health check
curl -X GET "http://localhost:63342/api/intellij-actions/health"
```

### JavaScript (fetch)

```javascript
// Execute action
fetch('http://localhost:63342/api/intellij-actions/execute?action=ReformatCode')
  .then(response => response.json())
  .then(data => console.log(data));

// Execute multiple actions
const actions = ['SaveAll', 'ReformatCode', 'OptimizeImports'];
fetch(`http://localhost:63342/api/intellij-actions/execute?actions=${actions.join(',')}`)
  .then(response => response.json())
  .then(data => console.log(data));
```

### Python

```python
import requests

# Execute action
response = requests.get('http://localhost:63342/api/intellij-actions/execute', 
                        params={'action': 'ReformatCode'})
print(response.json())

# Execute multiple actions
response = requests.get('http://localhost:63342/api/intellij-actions/execute',
                        params={'actions': 'SaveAll,ReformatCode,OptimizeImports',
                               'delay': 500})
print(response.json())
```

### Shell Script

```bash
#!/bin/bash

# Function to execute IntelliJ action
ij_action() {
    local action=$1
    curl -s "http://localhost:63342/api/intellij-actions/execute?action=$action" | \
        python3 -m json.tool
}

# Usage
ij_action "ReformatCode"
ij_action "SaveAll"
```

## Rate Limiting

### Built-in API (Port 63342)
IntelliJ's built-in REST API may impose rate limiting (429 Too Many Requests) when too many requests are sent in quick succession.

### Custom Server (Port 63343) - NO RATE LIMITING
Starting from version 1.0.8, the plugin includes a custom HTTP server on port 63343 that has NO rate limiting. The CLI tool automatically tries this server first.

**Benefits of Custom Server:**
- No rate limiting (429 errors)
- Faster response times for batch operations
- Same API endpoints as built-in server
- Automatic fallback to built-in API if custom server is unavailable

**Using the Custom Server:**
```bash
# The CLI tool automatically uses it
ij ReformatCode

# Force using built-in API
ij --force-builtin ReformatCode

# Direct HTTP access to custom server
curl "http://localhost:63343/api/intellij-actions/execute?action=ReformatCode"

# Direct HTTP access to built-in API
curl "http://localhost:63342/api/intellij-actions/execute?action=ReformatCode"
```

**Note:** Actions are executed asynchronously to prevent blocking. Complex actions may take time to complete.

## Security

⚠️ **Warning:** The API is exposed on localhost only and has no authentication. Do not expose port 63342 to external networks.

For additional security:
- The API only accepts GET requests
- Actions are executed in IntelliJ's security context
- No file system access beyond IntelliJ's scope

## Limitations

1. **Context-dependent actions**: Some actions require specific context (e.g., open file, selection)
2. **UI actions**: Actions that open dialogs will execute but may not be visible if IntelliJ is not in focus
3. **Synchronous response**: The API returns immediately, not waiting for action completion
4. **Single instance**: Only works with one IntelliJ instance at a time

## Versioning

API version is included in the health check response. The API is backward compatible within major versions.

Current version: 1.0.7

## Support

- [GitHub Issues](https://github.com/arabshapt/intellij-action-executor/issues)
- [Discussions](https://github.com/arabshapt/intellij-action-executor/discussions)