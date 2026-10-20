/* Keeps the staff order board fresh: every few seconds it asks the server whether anything has changed and reloads
   the page if so. It never reloads while someone is typing a cancellation reason. No libraries, no inline script. */
(function () {
    'use strict';

    var board = document.querySelector('[data-board]');
    if (!board) { return; }

    var shown = board.getAttribute('data-token');
    var url = board.getAttribute('data-poll-url');
    var INTERVAL_MS = 5000;

    function someoneIsTyping() {
        var active = document.activeElement;
        return !!active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA');
    }

    function check() {
        if (document.hidden || someoneIsTyping()) { return; }
        fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
            .then(function (response) { return response.ok ? response.json() : null; })
            .then(function (data) {
                if (data && typeof data.token === 'string' && data.token !== shown) { window.location.reload(); }
            })
            .catch(function () { /* a brief network gap: try again next time */ });
    }

    window.setInterval(check, INTERVAL_MS);
    document.addEventListener('visibilitychange', check);
}());
