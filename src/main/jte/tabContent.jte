@param String activeTab
@param gg.jte.Content content
@param boolean oob
!{String url = "users".equals(activeTab) ? "/admin" : "/admin/" + activeTab;}
<main id="tab-content" class="main" hx-get="${url}" hx-trigger="authz:changed from:body" hx-swap="outerHTML">
${content}
</main>
@if(oob)
@template.tabs(activeTab = activeTab, oob = true)
@endif
