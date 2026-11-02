let items = [];
let cloudUser = '';
let cloudFamily = '';
let requests = [];

const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = n => Number(n || 0).toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' zł';
const today = () => new Date().toLocaleDateString('sv-SE');
let editing = null;

function cloudAccount(u, f) {
  cloudUser = u;
  cloudFamily = f;
  render();
}

function cloudUpdated(j) {
  try {
    items = JSON.parse(j) || [];
  } catch (e) {
    items = [];
  }
  render();
}

function cloudRequests(j) {
  try {
    requests = JSON.parse(j);
  } catch (e) {
    requests = [];
  }
  render();
}

function cloudNotice(s) {
  $('notice').textContent = s;
}

function nativeResume() {}
function nativeDate(d) { $('date').value = d; }
function nativeTime(t) {}

function go(id) {
  document.querySelectorAll('section').forEach(e => e.hidden = e.id !== id);
  if (id === 'edit') $('name').focus();
}

function render() {
  const sum = items.filter(x => !x.done).reduce((s, x) => s + Number(x.amount || 0), 0);
  const paid = items.filter(x => x.done).reduce((s, x) => s + Number(x.amount || 0), 0);
  
  $('totals').innerHTML = `<div class="stat"><small>Do zapłaty</small><strong>${money(sum)}</strong></div><div class="stat"><small>Zapłacone</small><strong>${money(paid)}</strong></div>`;

  let list = items.slice().sort((a, b) => Number(a.done) - Number(b.done) || a.date.localeCompare(b.date));

  $('list').innerHTML = list.length
    ? list.map(x => `<article><div class="line"><b>${esc(x.name)}</b><span class="pill ${x.done ? 'ok' : 'wait'}">${x.done ? 'Zapłacone' : 'Do zapłaty'}</span></div><h2>${money(x.amount)}</h2><p>Termin: ${esc(x.date)} ${!x.done && x.date < today() ? ' · Po terminie' : ''}</p><p>🔔 ${x.reminder < 0 ? 'Bez przypomnienia' : x.reminder === 0 ? 'W dniu płatności' : x.reminder / 1440 + ' dni przed'}</p><div class="actions"><button onclick="toggle('${x.id}')">${x.done ? 'Cofnij zapłatę' : '✓ Zapłacone'}</button><button class="secondary" onclick="edit('${x.id}')">Edytuj</button><button class="danger" onclick="removeBill('${x.id}')">Usuń</button></div></article>`).join('')
    : '<p class="muted">Nie dodano jeszcze rachunków.</p>';

  $('who').textContent = cloudUser ? `Zalogowano: ${cloudUser}` : 'Nie zalogowano';
  $('family').textContent = cloudFamily ? `Kod rodziny: ${cloudFamily}` : 'Wpisz kod rodziny z aplikacji Rodzinne wizyty';
  $('requests').innerHTML = requests.map(r => `<p>${esc(r.name)} <button onclick="Android.approveMember('${r.uid}')">Zatwierdź</button></p>`).join('');
  $('locked').hidden = !!(cloudUser && cloudFamily);
  $('dashboard').hidden = !(cloudUser && cloudFamily);
}

function save() {
  const r = Android.save(JSON.stringify(items));
  if (r !== 'ok') alert(r);
  render();
}

function edit(id) {
  editing = id;
  const x = items.find(x => x.id === id);
  $('formTitle').textContent = x ? 'Edytuj rachunek' : 'Nowy rachunek';
  $('name').value = x?.name || '';
  $('amount').value = x?.amount || '';
  $('date').value = x?.date || today();
  $('reminder').value = String(x?.reminder ?? 1440);
  go('edit');
}

function submitBill() {
  const name = $('name').value.trim();
  const amount = Number($('amount').value);
  const date = $('date').value;

  if (!name || !Number.isFinite(amount) || amount <= 0 || !date) {
    alert('Podaj nazwę, dodatnią kwotę i termin.');
    return;
  }

  const x = {
    id: editing || ('r' + Date.now() + Math.random().toString(36).slice(2)),
    name,
    amount,
    date,
    reminder: Number($('reminder').value),
    done: editing ? !!items.find(v => v.id === editing)?.done : false
  };

  items = editing ? items.map(v => v.id === editing ? x : v) : [...items, x];
  save();
  go('home');
}

function toggle(id) {
  items = items.map(x => x.id === id ? { ...x, done: !x.done } : x);
  save();
}

function removeBill(id) {
  items = items.filter(x => x.id !== id);
  save();
}

function login() {
  Android.signIn($('email').value, $('password').value);
}

function join() {
  Android.joinFamily($('code').value.trim());
}

function logout() {
  Android.signOut();
  items = [];
  render();
}

function askNotifications() {
  Android.requestNotifications();
}

window.addEventListener('load', () => {
  render();
  go('home');
});
