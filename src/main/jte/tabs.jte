@param String activeTab
@param boolean oob
<nav class="tabs" id="tabs" aria-label="Sections" hx-swap-oob="${oob ? "true" : null}">
    <a class="tab" href="/admin" hx-get="/admin" hx-target="#tab-content" hx-swap="outerHTML" hx-push-url="true" aria-current="${"users".equals(activeTab) ? "page" : null}">Users</a>
    <a class="tab" href="/admin/applications" hx-get="/admin/applications" hx-target="#tab-content" hx-swap="outerHTML" hx-push-url="true" aria-current="${"applications".equals(activeTab) ? "page" : null}">Applications</a>
</nav>
