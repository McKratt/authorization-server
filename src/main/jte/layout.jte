@import net.bakaar.authz.adapter.in.web.admin.auth.AdminSession
@param AdminSession session
@param String activeTab
@param gg.jte.Content content
<%-- The CSRF token is base64url (enforced by AdminSession), so it is safe inside the JSON of hx-headers. --%>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="csrf-token" content="${session.csrfToken()}">
<meta name="htmx-config" content='{"includeIndicatorStyles":false,"historyCacheSize":0,"refreshOnHistoryMiss":true,"responseHandling":[{"code":"204","swap":false},{"code":"[23]..","swap":true},{"code":"(409|422)","swap":true},{"code":"[45]..","swap":false,"error":true}]}'>
<title>Authorizations</title>
<link rel="stylesheet" href="/admin/static/admin.css">
<script src="/admin/static/admin.js"></script>
<script src="/admin/static/htmx.min.js" defer></script>
</head>
<body hx-headers='{"X-CSRF-Token":"${session.csrfToken()}"}'>
<header class="header">
    <div class="brand"><span class="logo" aria-hidden="true"></span>Authorization</div>
    @template.tabs(activeTab = activeTab, oob = false)
    <div class="who">
        <span class="badge">${session.canWrite() ? "admin" : "reader"}</span>
        <span>${session.username()}</span>
        <button type="button" class="toggle" hx-post="/admin/logout">Sign out</button>
        <button type="button" class="toggle" id="theme-toggle"><span class="when-light">☾ Dark</span><span class="when-dark">☀ Light</span></button>
    </div>
</header>
@template.tabContent(activeTab = activeTab, content = content, oob = false)
<div id="dialog-slot"></div>
</body>
</html>
