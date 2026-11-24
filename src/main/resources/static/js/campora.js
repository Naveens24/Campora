/*
 * Campora front-end behaviour (no inline scripts — CSP is 'self' only).
 *  1. Confirm dialogs and double-submit protection for action forms
 *  2. Swap pickup ⇄ destination
 *  3. Live hotspot board for drivers (polls /api/v1/driver/hotspots)
 *  4. Live request status for students (reloads when the status changes)
 */
(function () {
  'use strict';

  const csrf = {
    name: document.querySelector('meta[name="_csrf_parameter"]')?.content,
    token: document.querySelector('meta[name="_csrf"]')?.content
  };

  // ── 1. forms ──────────────────────────────────────────────────
  function ask(question, onYes) {
    const dialog = el('dialog', 'ask');
    const acts = el('div', 'ask-acts');
    const no = el('button', 'btn btn-ghost', 'Cancel');
    const yes = el('button', 'btn btn-ink', 'Yes, continue');
    no.type = yes.type = 'button';
    no.addEventListener('click', () => dialog.close());
    yes.addEventListener('click', () => { dialog.close(); onYes(); });
    dialog.addEventListener('close', () => dialog.remove());
    acts.append(no, yes);
    dialog.append(el('p', null, question), acts);
    document.body.append(dialog);
    dialog.showModal();
  }

  document.addEventListener('submit', (event) => {
    const form = event.target;
    const question = form.getAttribute('data-confirm');
    if (question && form.dataset.confirmed !== '1') {
      event.preventDefault();
      const submitter = event.submitter;
      ask(question, () => {
        form.dataset.confirmed = '1';
        form.requestSubmit(submitter && form.contains(submitter) ? submitter : undefined);
      });
      return;
    }
    form.querySelectorAll('button[type="submit"]').forEach((btn) => {
      btn.disabled = true;
      btn.classList.add('disabled');
    });
  });

  // Re-enable buttons if the page is restored from the back/forward cache.
  window.addEventListener('pageshow', () => {
    document.querySelectorAll('form[data-confirmed]').forEach((form) => delete form.dataset.confirmed);
    document.querySelectorAll('button[type="submit"].disabled').forEach((btn) => {
      btn.disabled = false;
      btn.classList.remove('disabled');
    });
  });

  // Copy buttons: data-copy="id of the element whose text to copy"
  document.querySelectorAll('[data-copy]').forEach((button) => {
    button.addEventListener('click', async () => {
      const label = button.querySelector('span') || button;
      try {
        await navigator.clipboard.writeText(document.getElementById(button.dataset.copy).textContent.trim());
        label.textContent = 'Copied';
      } catch (e) {
        label.textContent = 'Select and copy';
      }
    });
  });

  // ── 2. swap route ends ────────────────────────────────────────
  document.querySelectorAll('[data-swap]').forEach((button) => {
    button.addEventListener('click', () => {
      const [a, b] = button.getAttribute('data-swap').split(',').map((id) => document.getElementById(id));
      if (a && b) {
        const value = a.value;
        a.value = b.value;
        b.value = value;
      }
    });
  });

  // Pickup and drop must differ: data-differs="id of the other select"
  document.querySelectorAll('select[data-differs]').forEach((drop) => {
    const pickup = document.getElementById(drop.dataset.differs);
    const check = () => drop.setCustomValidity(drop.value && drop.value === pickup.value ? 'Choose a different drop.' : '');
    drop.form.addEventListener('change', check);
    drop.form.addEventListener('click', () => setTimeout(check)); // after a swap
    check();
  });

  // ── helpers ───────────────────────────────────────────────────
  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function hidden(name, value) {
    const input = el('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    return input;
  }

  function routeStack(from, to) {
    const route = el('div', 'rs');
    const a = el('div');
    a.append(el('i', 'pin'), el('span', null, from));
    const b = el('div');
    b.append(el('i', 'pin end'), el('span', null, to));
    route.append(a, b);
    return route;
  }

  function stepButton(step, label, text) {
    const b = el('button', null, text);
    b.type = 'button';
    b.dataset.step = step;
    b.setAttribute('aria-label', label);
    return b;
  }

  // Seat steppers (delegated, so cards added later work too)
  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-step]');
    if (!button) return;
    const input = button.parentElement.querySelector('input');
    const min = parseInt(input.min || '1', 10);
    const max = parseInt(input.max || '99', 10);
    const next = (parseInt(input.value, 10) || min) + parseInt(button.dataset.step, 10);
    input.value = String(Math.min(max, Math.max(min, next)));
    input.dataset.touched = '1';
  });

  // ── 3. driver hotspot board ───────────────────────────────────
  const board = document.getElementById('hotspotBoard');
  if (board) {
    const endpoint = board.dataset.endpoint;
    const action = board.dataset.action;
    const refreshMs = Math.max(5, parseInt(board.dataset.refresh || '15', 10)) * 1000;

    function renderHotspot(h, capacity, canReserve, generatedAt) {
      const card = el('article', 'hs');
      card.dataset.key = h.pickupId + '|' + h.destinationId;
      const top = el('div', 'hs-top');
      const count = el('div', 'hs-count');
      count.append(el('b', null, String(h.seatsDemanded)), el('span', null, h.seatsDemanded === 1 ? 'seat' : 'seats'));
      top.append(routeStack(h.pickupName, h.destinationName), count);

      const waited = Math.max(0, Math.floor((new Date(generatedAt) - new Date(h.waitingSince)) / 60000));
      const waitText = waited < 1 ? 'Just now' : waited < 60 ? waited + ' min waiting' : Math.floor(waited / 60) + ' h waiting';
      const meta = el('div', 'hs-meta');
      const wait = el('span');
      wait.append(el('i', 'bi bi-clock'), el('span', null, waitText));
      meta.append(wait);
      if (h.seatsDemanded >= capacity) meta.append(el('span', 'hs-full', 'Fills your vehicle'));
      card.append(top, meta);

      if (canReserve) {
        const form = el('form', 'hs-form');
        form.method = 'post';
        form.action = action;
        form.append(hidden(csrf.name, csrf.token), hidden('pickupId', h.pickupId), hidden('destinationId', h.destinationId));
        const stepper = el('div', 'stepper');
        const seats = el('input');
        seats.type = 'number';
        seats.name = 'seats';
        seats.min = '1';
        seats.max = String(capacity);
        seats.value = String(Math.min(capacity, h.seatsDemanded));
        seats.setAttribute('aria-label', 'Seats to offer');
        stepper.append(stepButton('-1', 'Fewer seats', '\u2212'), seats, stepButton('1', 'More seats', '+'));
        const button = el('button', 'btn btn-route', 'Accept');
        button.type = 'submit';
        form.append(stepper, button);
        card.append(form);
      }
      return card;
    }

    function renderEmpty() {
      const box = el('div', 'card-c empty');
      box.append(el('i', 'bi bi-cup-hot'), el('div', 'fw-semibold text-body', 'No one is waiting right now'),
                 el('div', 'small', 'New routes appear here on their own.'));
      return box;
    }

    // Keep what the driver has typed or stepped when the board re-renders.
    board.addEventListener('input', (event) => { event.target.dataset.touched = '1'; });

    async function refresh() {
      if (document.hidden) return;
      try {
        const res = await fetch(endpoint, { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
        if (!res.ok || !(res.headers.get('content-type') || '').includes('json')) return;
        const data = await res.json();
        const nodes = data.hotspots.length
          ? data.hotspots.map((h) => renderHotspot(h, data.capacity, data.canReserve, data.generatedAt))
          : [renderEmpty()];
        if (board.contains(document.activeElement) && document.activeElement.matches('input')) return; // mid-edit
        const chosen = new Map();
        board.querySelectorAll('.hs input[name="seats"][data-touched]').forEach((i) => chosen.set(i.closest('.hs').dataset.key, i.value));
        const focused = board.contains(document.activeElement) ? document.activeElement : null;
        const focusKey = focused?.closest('.hs')?.dataset.key;
        const focusSel = focused?.dataset.step ? '[data-step="' + focused.dataset.step + '"]' : 'button[type="submit"]';
        board.replaceChildren(...nodes);
        board.querySelectorAll('.hs').forEach((card) => {
          const input = card.querySelector('input[name="seats"]');
          if (input && chosen.has(card.dataset.key)) {
            input.value = chosen.get(card.dataset.key);
            input.dataset.touched = '1';
          }
          if (focusKey && card.dataset.key === focusKey) card.querySelector(focusSel)?.focus({ preventScroll: true });
        });
      } catch (e) {
        /* network blip — next tick will retry */
      }
    }

    setInterval(refresh, refreshMs);
    document.addEventListener('visibilitychange', refresh);
  }

  // ── 5. landing polish: sticky nav shadow, scroll reveal, mode tabs
  const topbar = document.querySelector('.topbar');
  if (topbar) {
    const onScroll = () => topbar.classList.toggle('scrolled', window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  const reveals = document.querySelectorAll('.reveal');
  if (reveals.length && 'IntersectionObserver' in window) {
    // Anything already on screen is marked visible before hiding the rest,
    // so above-the-fold content never flickers.
    reveals.forEach((node) => {
      if (node.getBoundingClientRect().top < window.innerHeight) node.classList.add('in');
    });
    document.documentElement.classList.add('js');
    const io = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('in');
          io.unobserve(entry.target);
        }
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });
    reveals.forEach((node) => { if (!node.classList.contains('in')) io.observe(node); });
  }

  const tabs = document.querySelectorAll('.mode-switch [role="tab"]');
  tabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      tabs.forEach((t) => {
        const on = t === tab;
        t.setAttribute('aria-selected', String(on));
        t.tabIndex = on ? 0 : -1;
        const panel = document.getElementById(t.dataset.tab);
        if (panel) panel.hidden = !on;
      });
    });
    tab.addEventListener('keydown', (e) => {
      if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
      const list = Array.from(tabs);
      const next = list[(list.indexOf(tab) + (e.key === 'ArrowRight' ? 1 : list.length - 1)) % list.length];
      next.focus();
      next.click();
    });
  });

  // Deep link to a tab, e.g. /companion#panel-offer
  if (location.hash.length > 1) {
    Array.from(tabs).find((t) => '#' + t.dataset.tab === location.hash)?.click();
  }

  // ── 4. student live status ────────────────────────────────────
  const watch = document.querySelector('[data-watch-request]');
  if (watch) {
    const endpoint = watch.getAttribute('data-watch-request');
    const current = watch.getAttribute('data-status');
    setInterval(async () => {
      if (document.hidden) return;
      try {
        const res = await fetch(endpoint, { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
        const next = res.status === 204 ? 'NONE' : (res.ok ? (await res.json()).status : current);
        if (next !== current) window.location.reload();
      } catch (e) { /* ignore */ }
    }, 20000);
  }
})();
