// Admin UI behaviour. Loaded in <head> without defer, so the stored theme applies before the first paint.
// The Content-Security-Policy forbids inline scripts: everything lives here, wired through event delegation
// because htmx replaces the elements.
(function () {
    'use strict';

    var THEME_KEY = 'authz-theme';
    var root = document.documentElement;

    function storedTheme() {
        try {
            return localStorage.getItem(THEME_KEY);
        } catch (e) {
            return null;
        }
    }

    function storeTheme(theme) {
        try {
            localStorage.setItem(THEME_KEY, theme);
        } catch (e) {
            // storage unavailable: the choice lasts until the next page load
        }
    }

    function effectiveTheme() {
        var chosen = root.getAttribute('data-theme');
        if (chosen) {
            return chosen;
        }
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    var initial = storedTheme();
    if (initial === 'light' || initial === 'dark') {
        root.setAttribute('data-theme', initial);
    }

    function dialogSlot() {
        return document.getElementById('dialog-slot');
    }

    function closeDialog() {
        var slot = dialogSlot();
        if (!slot) {
            return;
        }
        var dialog = slot.querySelector('dialog');
        if (dialog && dialog.open) {
            dialog.close();
        }
        slot.replaceChildren();
    }

    function filterRows(query) {
        var needle = query.trim().toLowerCase();
        document.querySelectorAll('#tab-content [data-filter]').forEach(function (row) {
            row.hidden = needle !== '' && row.getAttribute('data-filter').toLowerCase().indexOf(needle) < 0;
        });
    }

    // A form marked data-requires-role can be submitted only with a role ticked; the server checks again.
    function updateSubmit(form) {
        if (!form || !form.hasAttribute('data-requires-role')) {
            return;
        }
        var submit = form.querySelector('[type=submit]');
        if (submit) {
            submit.disabled = !form.querySelector('input[name=roleId]:checked');
        }
    }

    // Choosing an application loads its role checkboxes into the element named by data-role-options.
    function loadRoleOptions(select) {
        var target = document.getElementById(select.getAttribute('data-role-options'));
        if (!target) {
            return;
        }
        if (select.value === '') {
            target.replaceChildren();
            updateSubmit(select.form);
            return;
        }
        htmx.ajax('GET', '/admin/applications/' + encodeURIComponent(select.value) + '/role-options',
            {target: target, swap: 'innerHTML'});
    }

    // The new application dialog: "+ Add role" appends an empty row from the form's template, × removes a row.
    function addRoleRow(button) {
        var form = button.closest('form');
        var rows = form && form.querySelector('[data-role-rows]');
        var template = form && form.querySelector('template[data-role-row-template]');
        if (!rows || !template) {
            return;
        }
        var row = template.content.firstElementChild.cloneNode(true);
        rows.appendChild(row);
        row.querySelector('input').focus();
    }

    function removeRoleRow(button) {
        var row = button.closest('.role-row');
        var form = button.closest('form');
        if (row) {
            row.remove();
        }
        var add = form && form.querySelector('[data-add-role]');
        if (add) {
            add.focus();
        }
    }

    document.addEventListener('click', function (event) {
        if (event.target.closest('#theme-toggle')) {
            var next = effectiveTheme() === 'dark' ? 'light' : 'dark';
            root.setAttribute('data-theme', next);
            storeTheme(next);
        }
        if (event.target.closest('[data-close-dialog]')) {
            closeDialog();
        }
        var addRole = event.target.closest('[data-add-role]');
        if (addRole) {
            addRoleRow(addRole);
        }
        var removeRole = event.target.closest('[data-remove-role]');
        if (removeRole) {
            removeRoleRow(removeRole);
        }
    });

    document.addEventListener('input', function (event) {
        if (event.target.id === 'filter') {
            filterRows(event.target.value);
        }
    });

    document.addEventListener('change', function (event) {
        if (event.target.matches('select[data-role-options]')) {
            loadRoleOptions(event.target);
        } else if (event.target.matches('input[name=roleId]')) {
            updateSubmit(event.target.form);
        }
    });

    document.addEventListener('htmx:afterSwap', function (event) {
        var slot = dialogSlot();
        if (slot && event.detail.target === slot) {
            var dialog = slot.querySelector('dialog');
            if (dialog && !dialog.open) {
                dialog.showModal();
            }
        }
        // new role checkboxes: none ticked yet
        updateSubmit(event.detail.target.closest && event.detail.target.closest('form'));
    });

    document.addEventListener('authz:close-dialog', closeDialog);

    // Escape or a cancel button closes the dialog natively: drop it from the slot too.
    // The close event does not bubble, hence the capture listener.
    document.addEventListener('close', function (event) {
        var slot = dialogSlot();
        if (slot && event.target.tagName === 'DIALOG' && slot.contains(event.target)) {
            slot.replaceChildren();
        }
    }, true);
}());
