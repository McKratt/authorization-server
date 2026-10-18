@param String csrfToken = null
<%--
    Shared <head> content of every admin page. The CSRF token is base64url (enforced by AdminSession); pages shown
    without a session (sign-in cancelled) have none.
--%>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
@if(csrfToken != null)
<meta name="csrf-token" content="${csrfToken}">
@endif
<meta name="htmx-config" content='{"includeIndicatorStyles":false,"historyCacheSize":0,"refreshOnHistoryMiss":true,"responseHandling":[{"code":"204","swap":false},{"code":"[23]..","swap":true},{"code":"(409|422)","swap":true},{"code":"[45]..","swap":false,"error":true}]}'>
<title>Authorizations</title>
<link rel="stylesheet" href="/admin/static/admin.css">
<script src="/admin/static/admin.js"></script>
<script src="/admin/static/htmx.min.js" defer></script>
