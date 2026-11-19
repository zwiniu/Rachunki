let items = [];
let cloudUser = '';
let cloudFamily = '';
let requests = [];

const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = n => Number(n || 0).toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' zł';
const today = () => new Date().toLocaleDateString('sv-SE');
let editing = null;
let editReturn = 'home';
let paymentRequest = null;
let paymentRequestId = 0;

function cloudAccount(u, f) {
  if (u !== cloudUser || f !== cloudFamily) {
    paymentRequest = null;
    if (['edit', 'paid', 'unpaid'].includes(currentScreen())) go('home');
  }
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

function currentScreen() {
  return document.querySelector('main > section:not([hidden])').id;
}

function go(id) {
  paymentRequest = null;
  document.querySelectorAll('section').forEach(e => e.hidden = e.id !== id);
  if (id === 'edit') $('name').focus();
}

function goBack() {
  go(currentScreen() === 'edit' ? editReturn : 'home');
}

function paymentDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith('0000')) return '';
  const date = new Date(value + 'T00:00:00Z');
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value ? value : '';
}

function billCard(x) {
  const id = `data-id="${esc(x.id)}"`;
  const paidAt = paymentDate(x.paidAt);
  const payment = x.done
    ? `<p>Data zapłaty: ${paidAt ? paidAt.split('-').reverse().join('.') : 'Nie zapisano'}</p>`
    : '';
  const paymentButtons = x.done
    ? `<button ${id} onclick="restoreUnpaid(this.dataset.id)">Cofnij zapłatę</button>
       <button class="secondary" ${id} onclick="pickPaymentDate(this.dataset.id)">Zmień datę zapłaty</button>`
    : `<button ${id} onclick="pickPaymentDate(this.dataset.id)">✓ Zapłacone</button>`;
  return `<article>
    <div class="line"><b>${esc(x.name)}</b><span class="pill ${x.done ? 'ok' : 'wait'}">${x.done ? 'Zapłacone' : 'Do zapłaty'}</span></div>
    <h2>${money(x.amount)}</h2>
    <p>Termin: ${esc(x.date)}${!x.done && x.date < today() ? ' · Po terminie' : ''}</p>
    ${payment}
    <p>🔔 ${x.reminder < 0 ? 'Bez przypomnienia' : x.reminder === 0 ? 'W dniu płatności' : x.reminder / 1440 + ' dni przed'}</p>
    <div class="actions">
      ${paymentButtons}
      <button class="secondary" ${id} onclick="edit(this.dataset.id)">Edytuj</button>
      <button class="danger" ${id} onclick="removeBill(this.dataset.id)">Usuń</button>
    </div>
  </article>`;
}

function renderList(id, bills, emptyMessage) {
  $(id).innerHTML = bills.length ? bills.map(billCard).join('') : `<p class="muted">${emptyMessage}</p>`;
}

function render() {
  const byDueDate = (a, b) => a.date.localeCompare(b.date);
  const unpaid = items.filter(x => !x.done).sort(byDueDate);
  const paid = items.filter(x => x.done).sort(byDueDate);
  const total = bills => money(bills.reduce((sum, x) => sum + Number(x.amount || 0), 0));
  $('totals').innerHTML = `<button class="stat" onclick="go('unpaid')"><small>Do zapłaty</small><strong>${total(unpaid)}</strong></button>
    <button class="stat" onclick="go('paid')"><small>Zapłacone</small><strong>${total(paid)}</strong></button>`;

  renderList('list', [...unpaid, ...paid], 'Nie dodano jeszcze rachunków.');
  renderList('unpaidList', unpaid, 'Brak rachunków do zapłaty');
  renderList('paidList', paid.slice().sort((a, b) => paymentDate(b.paidAt).localeCompare(paymentDate(a.paidAt))), 'Brak zapłaconych rachunków');

  $('who').textContent = cloudUser ? `Zalogowano: ${cloudUser}` : 'Nie zalogowano';
  $('family').textContent = cloudFamily ? `Kod rodziny: ${cloudFamily}` : 'Wpisz kod rodziny z aplikacji Rodzinne wizyty';
  $('requests').innerHTML = requests.map(r => `<p>${esc(r.name)} <button onclick="Android.approveMember('${r.uid}')">Zatwierdź</button></p>`).join('');
  $('locked').hidden = !!(cloudUser && cloudFamily);
  $('dashboard').hidden = !(cloudUser && cloudFamily);
}

function save(bills) {
  const r = Android.save(JSON.stringify(bills));
  if (r !== 'ok') {
    alert(r);
    return false;
  }
  items = bills;
  render();
  return true;
}

function edit(id) {
  const screen = currentScreen();
  editReturn = screen === 'paid' || screen === 'unpaid' ? screen : 'home';
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

  const previous = items.find(x => x.id === editing);
  if (editing && !previous) {
    alert('Tego rachunku już nie ma.');
    goBack();
    return;
  }
  const x = {
    ...previous,
    id: editing || ('r' + Date.now() + Math.random().toString(36).slice(2)),
    name,
    amount,
    date,
    reminder: Number($('reminder').value),
    done: previous ? previous.done : false
  };

  if (save(editing ? items.map(v => v.id === editing ? x : v) : [...items, x])) goBack();
}

function pickPaymentDate(id) {
  const bill = items.find(x => x.id === id);
  if (!bill) return;
  const token = String(++paymentRequestId);
  paymentRequest = { id, token, wasPaid: !!bill.done };
  Android.pickPaymentDate(token, bill.done ? paymentDate(bill.paidAt) : '');
}

function nativePaymentDate(token, date) {
  if (paymentRequest?.token !== token) return;
  const { id, wasPaid } = paymentRequest;
  paymentRequest = null;
  const bill = items.find(x => x.id === id);
  if (!bill || !!bill.done !== wasPaid || !paymentDate(date) || date > today()) return;
  save(items.map(x => x.id === id ? { ...x, done: true, paidAt: date } : x));
}

function restoreUnpaid(id) {
  const bill = items.find(x => x.id === id);
  if (!bill?.done) return;
  const restored = { ...bill, done: false };
  delete restored.paidAt;
  save(items.map(x => x.id === id ? restored : x));
}

function removeBill(id) {
  const bill = items.find(x => x.id === id);
  if (!bill || (bill.done && !confirm(`Usunąć rachunek „${bill.name}”?`))) return;
  save(items.filter(x => x.id !== id));
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
