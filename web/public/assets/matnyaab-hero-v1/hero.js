(() => {
    'use strict';
    const hero = document.querySelector('[data-hero]');
    if (!hero) return;
    const slides = [...hero.querySelectorAll('[data-slide]')];
    const dots = [...hero.querySelectorAll('[data-go]')];
    const pauseButton = hero.querySelector('[data-pause]');
    const status = hero.querySelector('[data-slide-status]');
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    let index = 0, transition = false, timer, typing, paused = motion.matches, hovering = false, focused = false, visible = true;
    const isStopped = () => paused || hovering || focused || document.hidden || !visible;
    const completeText = slide => { const el = slide.querySelector('[data-type]'); el.textContent = el.dataset.type; };
    function type(slide) {
        clearTimeout(typing);
        const el = slide.querySelector('[data-type]');
        if (motion.matches || isStopped()) { completeText(slide); return; }
        const characters = Array.from(el.dataset.type);
        let cursor = 0;
        el.textContent = '';
        const tick = () => {
            el.textContent = characters.slice(0, ++cursor).join('');
            if (cursor < characters.length) typing = setTimeout(tick, 70);
        };
        typing = setTimeout(tick, 320);
    }
    function schedule() {
        clearTimeout(timer);
        hero.classList.toggle('is-paused', isStopped());
        pauseButton.setAttribute('aria-pressed', String(paused));
        pauseButton.setAttribute('aria-label', paused ? 'شروع پخش خودکار' : 'توقف پخش خودکار');
        pauseButton.textContent = paused ? '▷' : 'Ⅱ';
        if (isStopped()) { clearTimeout(typing); completeText(slides[index]); }
        if (!isStopped() && !motion.matches) timer = setTimeout(() => show(index + 1), 12000);
    }
    async function show(next, manual = false) {
        next = (next + slides.length) % slides.length;
        if (next === index || transition) return;
        transition = true;
        clearTimeout(timer); clearTimeout(typing);
        const incoming = slides[next], outgoing = slides[index];
        const img = incoming.querySelector('img');
        img.loading = 'eager';
        try { await img.decode(); } catch (_) { /* Text and controls remain usable if a photo is unavailable. */ }
        completeText(outgoing);
        incoming.removeAttribute('inert'); incoming.removeAttribute('aria-hidden');
        outgoing.setAttribute('inert', ''); outgoing.setAttribute('aria-hidden', 'true');
        outgoing.classList.add('is-leaving');
        incoming.classList.add('is-entering');
        index = next;
        dots.forEach((dot, i) => i === index ? dot.setAttribute('aria-current', 'true') : dot.removeAttribute('aria-current'));
        if (manual) status.textContent = incoming.getAttribute('aria-label') + '؛ ' + incoming.querySelector('.hero-kicker').textContent;
        type(incoming);
        setTimeout(() => {
            outgoing.classList.remove('is-current', 'is-leaving');
            incoming.classList.remove('is-entering'); incoming.classList.add('is-current');
            transition = false; schedule();
        }, motion.matches ? 0 : 1100);
    }
    hero.querySelector('[data-next]').addEventListener('click', () => show(index + 1, true));
    hero.querySelector('[data-prev]').addEventListener('click', () => show(index - 1, true));
    dots.forEach(dot => dot.addEventListener('click', () => show(Number(dot.dataset.go), true)));
    pauseButton.addEventListener('click', () => { paused = !paused; schedule(); });
    hero.addEventListener('keydown', event => {
        if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
            event.preventDefault(); show(index + (event.key === 'ArrowLeft' ? 1 : -1), true);
        }
    });
    hero.addEventListener('pointerenter', event => { if (event.pointerType === 'mouse') { hovering = true; schedule(); } });
    hero.addEventListener('pointerleave', () => { hovering = false; schedule(); });
    hero.addEventListener('focusin', () => { focused = true; schedule(); });
    hero.addEventListener('focusout', () => setTimeout(() => { focused = hero.contains(document.activeElement); schedule(); }, 0));
    let touch;
    hero.addEventListener('touchstart', event => { touch = [event.touches[0].clientX, event.touches[0].clientY]; }, { passive: true });
    hero.addEventListener('touchend', event => {
        if (!touch) return;
        const dx = event.changedTouches[0].clientX - touch[0], dy = event.changedTouches[0].clientY - touch[1];
        if (Math.abs(dx) > 55 && Math.abs(dx) > Math.abs(dy) * 1.5) show(index + (dx < 0 ? 1 : -1), true);
        touch = null;
    }, { passive: true });
    document.addEventListener('visibilitychange', schedule);
    motion.addEventListener('change', () => { paused = motion.matches; completeText(slides[index]); schedule(); });
    if ('IntersectionObserver' in window) new IntersectionObserver(entries => { visible = entries[0].isIntersecting; schedule(); }, { threshold: .1 }).observe(hero);
    type(slides[0]); schedule();
})();
