/* Search, filter chips and the "today's special" banner on the student menu. No external libraries. */
(function () {
    'use strict';

    var cards = Array.prototype.slice.call(document.querySelectorAll('[data-menu-card]'));
    var sections = Array.prototype.slice.call(document.querySelectorAll('[data-menu-section]'));
    var chips = Array.prototype.slice.call(document.querySelectorAll('[data-filter]'));
    var clearButtons = Array.prototype.slice.call(document.querySelectorAll('[data-filter-clear]'));
    var search = document.getElementById('menu-search');
    var count = document.getElementById('menu-count');
    var empty = document.getElementById('menu-empty');
    var active = {};

    function apply() {
        var query = search ? search.value.trim().toLowerCase() : '';
        var wanted = Object.keys(active).filter(function (key) { return active[key]; });
        var visible = 0;

        cards.forEach(function (card) {
            var tags = (card.getAttribute('data-tags') || '').split(' ');
            var text = card.getAttribute('data-text') || '';
            var matchesText = !query || text.indexOf(query) !== -1;
            var matchesTags = wanted.every(function (tag) { return tags.indexOf(tag) !== -1; });
            var show = matchesText && matchesTags;
            card.hidden = !show;
            if (show) { visible++; }
        });

        sections.forEach(function (section) {
            section.hidden = !section.querySelector('[data-menu-card]:not([hidden])');
        });

        if (count) { count.textContent = visible + (visible === 1 ? ' choice' : ' choices'); }
        if (empty) { empty.hidden = visible !== 0 || cards.length === 0; }
        clearButtons.forEach(function (button) { button.hidden = wanted.length === 0; });
    }

    chips.forEach(function (chip) {
        chip.addEventListener('click', function () {
            var key = chip.getAttribute('data-filter');
            active[key] = !active[key];
            chips.forEach(function (other) {
                if (other.getAttribute('data-filter') === key) {
                    other.setAttribute('aria-pressed', active[key] ? 'true' : 'false');
                }
            });
            apply();
        });
    });

    clearButtons.forEach(function (button) {
        button.addEventListener('click', function () {
            active = {};
            chips.forEach(function (chip) { chip.setAttribute('aria-pressed', 'false'); });
            apply();
        });
    });

    if (search) { search.addEventListener('input', apply); }

    // Today's special: the counter button shows the next slide.
    var slides = Array.prototype.slice.call(document.querySelectorAll('[data-hero-slide]'));
    var next = document.querySelector('[data-hero-next]');
    var shown = 0;
    if (slides.length > 1 && next) {
        var total = String(slides.length).padStart(2, '0');
        var numberEl = next.querySelector('[data-hero-number]');
        var totalEl = next.querySelector('[data-hero-total]');
        if (totalEl) { totalEl.textContent = '/ ' + total; }
        next.addEventListener('click', function () {
            slides[shown].hidden = true;
            shown = (shown + 1) % slides.length;
            slides[shown].hidden = false;
            if (numberEl) { numberEl.textContent = String(shown + 1).padStart(2, '0'); }
        });
    }

    apply();
}());
