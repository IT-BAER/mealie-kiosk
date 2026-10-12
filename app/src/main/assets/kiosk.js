// Injected by MealieKiosk after each full page load. SPA route changes keep it alive.
(function () {
  if (window.mealieKiosk) return;
  var d = document, cur = -1, path = "";
  var css = d.createElement("style");
  css.textContent = ".mk-step{outline:4px solid #E58325!important;outline-offset:2px}";
  d.head.appendChild(css);
  kiosk();

  // Kiosk layout: no app bar or drawer, three recipe columns, a fixed button row at the bottom.
  // Skipped when a server-side copy of this script (mt-kiosk) already ran.
  function kiosk() {
    var h = d.documentElement;
    if (h.classList.contains("mt-kiosk")) return;
    h.classList.add("mt-kiosk");
    var s = d.createElement("style");
    s.textContent = "html.mt-kiosk .v-app-bar,html.mt-kiosk .v-navigation-drawer,html.mt-kiosk .v-navigation-drawer__scrim{display:none!important}"
      + "html.mt-kiosk .v-main{padding:0 0 88px 0!important;--v-layout-left:0px!important;--v-layout-top:0px!important}"
      + "html.mt-kiosk:not(.mt-search) .search-container,html.mt-kiosk:not(.mt-search) .search-container+.v-divider,html.mt-kiosk .search-container .search-row{display:none!important}"
      + "html.mt-kiosk:not(.mt-search) .search-container+.v-divider+.v-container{margin-top:0!important}"
      + "html.mt-kiosk{font-size:112%}"
      + "html.mt-kiosk .v-container{max-width:none!important}"
      + "html.mt-kiosk .v-col.v-col--cols-md-6.v-col--cols-lg-4{flex:0 0 calc((100% - 48px) / 3)!important;max-width:calc((100% - 48px) / 3)!important}"
      + "#mt-nav{position:fixed;left:50%;transform:translateX(-50%);bottom:16px;z-index:2000;display:flex;gap:12px}"
      + "#mt-nav button{min-height:56px;padding:0 24px;white-space:nowrap;border:0;border-radius:28px;background:#E58325;color:#fff;font:600 18px sans-serif;box-shadow:0 2px 8px rgba(0,0,0,.45)}";
    d.head.appendChild(s);
    if (d.getElementById("mt-nav")) return;
    var de = /^de/i.test(navigator.language);
    var n = d.createElement("div");
    n.id = "mt-nav";
    [[de ? "Zurück" : "Back", ""],
     [de ? "Wochenplan" : "Meal plan", "/household/mealplan/planner/view"],
     [de ? "Einkaufsliste" : "Shopping list", "/shopping-lists"]].forEach(function (x) {
      var b = d.createElement("button");
      b.type = "button";
      b.textContent = x[0];
      b.onclick = function () {
        if (!x[1]) return history.back();
        if (x[1] !== "/shopping-lists") return go(x[1]);
        // A single list opens directly: /shopping-lists redirects and traps history.back().
        fetch("/api/households/shopping/lists?perPage=2", { credentials: "include" })
          .then(function (r) { return r.json(); })
          .then(function (j) { go(j.items && j.items.length === 1 ? "/shopping-lists/" + j.items[0].id : x[1]); })
          .catch(function () { go(x[1]); });
      };
      n.appendChild(b);
    });
    var q = d.createElement("button");
    q.type = "button";
    q.textContent = "🔍";
    q.setAttribute("aria-label", de ? "Suche" : "Search");
    q.onclick = function () {
      var c = d.querySelector(".search-container");
      if (c && h.classList.toggle("mt-search") === false) {
        var x = c.querySelector(".v-field__clearable i");
        if (x && c.querySelector("input").value) x.dispatchEvent(new MouseEvent("click", { bubbles: true }));
        return;
      }
      h.classList.add("mt-search");
      if (!c) go("/g/home");
      setTimeout(function () { var i = d.querySelector(".search-container input"); if (i) i.focus(); }, 400);
    };
    n.appendChild(q);
    d.body.appendChild(n);
  }

  // Vue router navigation: a full load can hit a browser-cached HTML copy.
  function go(u) {
    var a = d.getElementById("__nuxt"), r = a && a.__vue_app__ && a.__vue_app__.config.globalProperties.$router;
    if (r) r.push(u); else location.assign(u);
  }

  // Mealie renders a hidden second copy of the steps; only visible cards count.
  function steps() {
    return Array.prototype.slice.call(d.querySelectorAll(".recipe-step-title"))
      .map(function (t) { return t.closest(".v-card"); })
      .filter(function (c) { return c && c.offsetParent; });
  }

  // Cook mode scrolls a column, the normal recipe page scrolls the document.
  function scroller() {
    var e = (steps()[0] || {}).parentElement;
    while (e && e !== d.body) {
      if (/auto|scroll/.test(getComputedStyle(e).overflowY) && e.scrollHeight > e.clientHeight + 20) return e;
      e = e.parentElement;
    }
    return d.scrollingElement;
  }

  function scroll(dir) {
    var e = scroller();
    e.scrollBy({ top: dir * e.clientHeight * 0.8, behavior: "smooth" });
  }

  function mark(s, i) {
    cur = i;
    path = location.pathname;
    s.forEach(function (c) { c.classList.remove("mk-step"); });
    s[i].classList.add("mk-step");
    s[i].scrollIntoView({ behavior: "smooth", block: "center" });
  }

  function checked(c) {
    return c.classList.contains("disabled-card");
  }

  function step(dir) {
    var s = steps();
    if (!s.length) return scroll(dir);
    if (path !== location.pathname || !s[cur] || !s[cur].classList.contains("mk-step")) {
      // No marked step yet: start at the first step that is not above the screen.
      var i = s.findIndex(function (c) { return c.getBoundingClientRect().bottom > 0; });
      return mark(s, i < 0 ? s.length - 1 : i);
    }
    mark(s, Math.max(0, Math.min(s.length - 1, cur + dir)));
  }

  function focus(n) {
    var s = steps(), c = s[n - 1];
    if (!c) return;
    if (checked(c)) c.click();
    mark(s, n - 1);
  }

  function done(n) {
    var s = steps(), i = n ? n - 1 : cur;
    if (i < 0 || !s[i]) return;
    if (!checked(s[i])) s[i].click();
    if (i + 1 < s.length) mark(s, i + 1); else mark(s, i);
  }

  window.mealieKiosk = {
    scroll: scroll,
    step: step,
    focus: focus,
    done: done,
  };
})();
