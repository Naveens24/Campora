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
  document.addEventListener('submit', (event) => {
    const form = event.target;
    const question = form.getAttribute('data-confirm');
    if (question && !window.confirm(question)) {
      event.preventDefault();
      return;
    }
    form.querySelectorAll('button[type="submit"]').forEach((btn) => {
      btn.disabled = true;
      btn.classList.add('disabled');
    });
  });

  // Re-enable buttons if the page is restored from the back/forward cache.
  window.addEventListener('pageshow', () => {
    document.querySelectorAll('button[type="submit"].disabled').forEach((btn) => {
      btn.disabled = false;
      btn.classList.remove('disabled');
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

  function routeLine(from, to) {
    const route = el('div', 'route flex-grow-1');
    const a = el('div', 'stop');
    a.append(el('i', 'pin'), el('span', null, from));
    const b = el('div', 'stop');
    b.append(el('i', 'pin end'), el('span', null, to));
    route.append(a, el('div', 'track'), b);
    return route;
  }

  function metric(value, caption) {
    const m = el('div', 'metric', String(value));
    m.append(el('small', null, caption));
    return m;
  }

  function seatBar(count) {
    const bar = el('div', 'seats mb-3');
    const shown = Math.min(count, 12);
    for (let i = 0; i < shown; i++) bar.append(el('span', 'seat on'));
    if (count > shown) bar.append(el('span', 'seat more', '+' + (count - shown)));
    return bar;
  }

  // ── 3. driver hotspot board ───────────────────────────────────
  const board = document.getElementById('hotspotBoard');
  if (board) {
    const endpoint = board.dataset.endpoint;
    const action = board.dataset.action;
    const refreshMs = Math.max(5, parseInt(board.dataset.refresh || '15', 10)) * 1000;
    const stamp = document.getElementById('boardStamp');

    function renderHotspot(h, capacity, canReserve, generatedAt) {
      const col = el('div', 'col-12 col-xl-6');
      const card = el('article', 'hotspot h-100');
      const head = el('div', 'd-flex align-items-start gap-3 mb-3');
      head.append(routeLine(h.pickupName, h.destinationName));
      card.append(head);

      const waited = Math.max(0, Math.round((new Date(generatedAt) - new Date(h.waitingSince)) / 60000));
      const metrics = el('div', 'd-flex gap-4 mb-3');
      metrics.append(metric(h.requests, h.requests === 1 ? 'request' : 'requests'),
                     metric(h.seatsDemanded, 'seats'),
                     metric(waited + 'm', 'waiting'));
      card.append(metrics, seatBar(h.seatsDemanded));

      if (canReserve) {
        const form = el('form');
        form.method = 'post';
        form.action = action;
        form.append(hidden(csrf.name, csrf.token), hidden('pickupId', h.pickupId), hidden('destinationId', h.destinationId));
        const seats = el('input', 'form-control');
        seats.type = 'number';
        seats.name = 'seats';
        seats.min = '1';
        seats.max = String(capacity);
        seats.value = String(Math.min(capacity, h.seatsDemanded));
        seats.setAttribute('aria-label', 'Seats to offer');
        const button = el('button', 'btn btn-route flex-grow-1');
        button.type = 'submit';
        button.append(el('i', 'bi bi-check2-circle me-1'), document.createTextNode('Reserve seats'));
        form.append(seats, button);
        card.append(form);
      }
      col.append(card);
      return col;
    }

    function renderEmpty() {
      const col = el('div', 'col-12');
      const box = el('div', 'card-c empty');
      box.append(el('i', 'bi bi-signpost-split'), el('div', 'fw-semibold text-body', 'No open demand right now'),
                 el('div', 'small', 'New hotspots appear here automatically.'));
      col.append(box);
      return col;
    }

    async function refresh() {
      if (document.hidden) return;
      try {
        const res = await fetch(endpoint, { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
        if (!res.ok || !(res.headers.get('content-type') || '').includes('json')) return;
        const data = await res.json();
        const nodes = data.hotspots.length
          ? data.hotspots.map((h) => renderHotspot(h, data.capacity, data.canReserve, data.generatedAt))
          : [renderEmpty()];
        board.replaceChildren(...nodes);
        if (stamp) stamp.textContent = 'Updated ' + new Date(data.generatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
      } catch (e) {
        /* network blip — next tick will retry */
      }
    }

    setInterval(refresh, refreshMs);
    document.addEventListener('visibilitychange', refresh);
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
