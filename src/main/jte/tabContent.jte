@param String activeTab
@param gg.jte.Content content
@param boolean oob
<%-- hx-disinherit: the outerHTML swap is for reloading the tab only; the dialog buttons inside must keep the default innerHTML swap into #dialog-slot. --%>
!{String url = "users".equals(activeTab) ? "/admin" : "/admin/" + activeTab;}
<main id="tab-content" class="main" hx-get="${url}" hx-trigger="authz:changed from:body" hx-swap="outerHTML" hx-disinherit="*">
${content}
</main>
@if(oob)
@template.tabs(activeTab = activeTab, oob = true)
@endif
