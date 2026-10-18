<%-- Keycloak sent the user back with an error instead of a code (e.g. sign-in cancelled). Nothing from the query is shown. --%>
<!doctype html>
<html lang="en">
<head>
@template.head()
</head>
<body>
<header class="header">
    <div class="brand"><span class="logo" aria-hidden="true"></span>Authorization</div>
</header>
<main class="notice">
    <h1>Sign-in was cancelled</h1>
    <p class="muted">You are not signed in to the admin UI.</p>
    <a class="btn" href="/admin/login">Sign in again</a>
</main>
</body>
</html>
