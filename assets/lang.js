// RU/EN switch: the head script already picked a language, this only handles the buttons
document.querySelectorAll('.lang').forEach(function (b) {
  b.addEventListener('click', function () {
    var l = b.dataset.to;
    document.documentElement.dataset.l = l;
    document.documentElement.lang = l;
    try { localStorage.setItem('graphite-lang', l); } catch (e) {}
  });
});
