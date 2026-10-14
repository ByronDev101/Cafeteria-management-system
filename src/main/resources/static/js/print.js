/* Print buttons. Inline click handlers are blocked by the site's security policy, so they are wired up here. */
(function () {
    'use strict';
    Array.prototype.slice.call(document.querySelectorAll('[data-print]')).forEach(function (button) {
        button.addEventListener('click', function () { window.print(); });
    });
}());
