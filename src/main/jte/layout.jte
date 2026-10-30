@import net.bakaar.authz.adapter.in.web.admin.AdminSession
@param AdminSession session
@param String activeTab
@param gg.jte.Content content
<%-- The CSRF token is base64url (enforced by AdminSession), so it is safe inside the JSON of hx-headers. --%>
<!doctype html>
<html lang="en">
<head>
@template.head(csrfToken = session.csrfToken())
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
