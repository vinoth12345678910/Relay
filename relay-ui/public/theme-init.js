(function () {
  try {
    var t = window.localStorage.getItem('relay-ui.theme');
    var dark =
      t === 'dark' || (t !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('dark', dark);
  } catch (e) {
    /* storage blocked: fall back to the CSS media query default */
  }
})();
