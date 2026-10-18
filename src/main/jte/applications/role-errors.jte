@import java.util.Map
@param Map<String, String> errors
@if(errors.containsKey("roleName"))
    <p class="field-error" id="roleName-error">${errors.get("roleName")}</p>
@endif
@if(errors.containsKey("roleDescription"))
    <p class="field-error" id="roleDescription-error">${errors.get("roleDescription")}</p>
@endif
