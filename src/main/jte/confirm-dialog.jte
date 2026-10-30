@import java.util.List
@import java.util.stream.Collectors
@import net.bakaar.authz.domain.UserSummary
@param String title
@param String message
@param List<UserSummary> removed
@param String deleteUrl
@param String hxTarget
@param String confirmLabel = "Remove"
<%--
    Confirms a removal. With users to remove, the message is a warning, the danger button names them, and it sends
    their IDs as confirmedUserIds: the server deletes them only if they are still exactly the users it would delete.
--%>
!{String names = removed.stream().map(user -> user.username().value()).collect(Collectors.joining(", "));}
!{String confirmedUserIds = removed.stream().map(user -> "\"" + user.id().asString() + "\"").collect(Collectors.joining(",", "{\"confirmedUserIds\":[", "]}"));}
<dialog aria-labelledby="dialog-title">
    <h2 id="dialog-title">${title}</h2>
    @if(removed.isEmpty())
        <p>${message}</p>
    @else
        <div class="warn">${message}</div>
    @endif
    <div class="row">
        <button type="button" class="btn ghost" data-close-dialog>Cancel</button>
        @if(removed.isEmpty())
            <button type="button" class="btn danger" hx-delete="${deleteUrl}" hx-target="${hxTarget}">${confirmLabel}</button>
        @else
            <button type="button" class="btn danger" hx-delete="${deleteUrl}" hx-target="${hxTarget}" hx-vals="${confirmedUserIds}">Remove and delete ${names}</button>
        @endif
    </div>
</dialog>
