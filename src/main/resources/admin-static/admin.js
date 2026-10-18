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

    document.addEventListener('click', function (event) {
        if (event.target.closest('#theme-toggle')) {
            var next = effectiveTheme() === 'dark' ? 'light' : 'dark';
            root.setAttribute('data-theme', next);
            storeTheme(next);
        }
    });

    document.addEventListener('input', function (event) {
        if (event.target.id === 'filter') {
            filterRows(event.target.value);
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
