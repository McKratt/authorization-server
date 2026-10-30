@import net.bakaar.authz.adapter.in.web.admin.AdminSession
@param AdminSession session
<%-- Signed in, but with neither admin role: the only way out is signing out (to switch account). --%>
<!doctype html>
<html lang="en">
<head>
@template.head(csrfToken = session.csrfToken())
</head>
<body hx-headers='{"X-CSRF-Token":"${session.csrfToken()}"}'>
<header class="header">
    <div class="brand"><span class="logo" aria-hidden="true"></span>Authorization</div>
    <div class="who">
        <span>${session.username()}</span>
    </div>
</header>
<main class="notice">
    <h1>You do not have access to the admin UI</h1>
    <p class="muted">You are signed in as <strong>${session.username()}</strong>, who holds neither the authz-admin nor the authz-reader role. Sign out to use another account.</p>
    <button type="button" class="btn" hx-post="/admin/logout">Sign out</button>
</main>
</body>
</html>
