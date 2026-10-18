/* Keeps an order page up to date: every few seconds it asks the server for the order's status and reloads the
   page when the status has changed. It stops once the order is finished. No libraries, no inline script. */
(function () {
    'use strict';

    var main = document.querySelector('[data-order-status]');
    if (!main || main.getAttribute('data-terminal') === 'true') { return; }

    var shown = main.getAttribute('data-order-status');
    var url = main.getAttribute('data-poll-url');
    var INTERVAL_MS = 5000;

    function check() {
        if (document.hidden) { return; }
        fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
            .then(function (response) { return response.ok ? response.json() : null; })
            .then(function (data) {
                if (data && data.status && data.status !== shown) { window.location.reload(); }
            })
            .catch(function () { /* offline for a moment: try again next time */ });
    }

    window.setInterval(check, INTERVAL_MS);
    document.addEventListener('visibilitychange', check);
}());
