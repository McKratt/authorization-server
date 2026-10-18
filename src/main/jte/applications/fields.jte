@import java.util.Map
@param Map<String, String> values
@param Map<String, String> errors
@param boolean renaming
<%-- Name, responsible and description of the application dialog, with their field errors. --%>
!{String nameDescribedBy = ((errors.containsKey("name") ? "name-error " : "") + (renaming ? "name-note" : "")).trim();}
<div class="field">
    <label for="name">Name</label>
    <input class="input" id="name" name="name" value="${values.get("name")}" maxlength="100" autocomplete="off" required aria-invalid="${errors.containsKey("name") ? "true" : null}" aria-describedby="${nameDescribedBy.isEmpty() ? null : nameDescribedBy}">
    @if(renaming)
        <p class="note" id="name-note">Renaming changes what client applications must send to the authorization API.</p>
    @endif
    @if(errors.containsKey("name"))
        <p class="field-error" id="name-error">${errors.get("name")}</p>
    @endif
</div>
<div class="field">
    <label for="responsible">Responsible (email)</label>
    <input class="input" id="responsible" name="responsible" type="email" value="${values.get("responsible")}" maxlength="254" autocomplete="off" required aria-invalid="${errors.containsKey("responsible") ? "true" : null}" aria-describedby="${errors.containsKey("responsible") ? "responsible-error" : null}">
    @if(errors.containsKey("responsible"))
        <p class="field-error" id="responsible-error">${errors.get("responsible")}</p>
    @endif
</div>
<div class="field">
    <label for="description">Description</label>
    <textarea class="input" id="description" name="description" rows="2" maxlength="2000" aria-invalid="${errors.containsKey("description") ? "true" : null}" aria-describedby="${errors.containsKey("description") ? "description-error" : null}">${values.get("description")}</textarea>
    @if(errors.containsKey("description"))
        <p class="field-error" id="description-error">${errors.get("description")}</p>
    @endif
</div>
