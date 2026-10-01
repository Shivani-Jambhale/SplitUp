const CURRENCY = '₹'; // change to '$', '€', etc. if you prefer

let currentCode = null;
let currentGroup = null;
let splitMode = 'equal'; // 'equal' | 'custom'

const landingScreen = document.getElementById('landingScreen');
const groupScreen = document.getElementById('groupScreen');

const todayStr = new Date().toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
const todayDateEl = document.getElementById('todayDate');
const todayDate2El = document.getElementById('todayDate2');
if (todayDateEl) todayDateEl.textContent = todayStr;
if (todayDate2El) todayDate2El.textContent = todayStr;

function showScreen(screen) {
  landingScreen.classList.add('hidden');
  groupScreen.classList.add('hidden');
  screen.classList.remove('hidden');
}

// ---------- split mode toggle ----------

document.getElementById('modeEqualBtn').addEventListener('click', () => setSplitMode('equal'));
document.getElementById('modeCustomBtn').addEventListener('click', () => setSplitMode('custom'));

function setSplitMode(mode) {
  splitMode = mode;
  document.getElementById('modeEqualBtn').classList.toggle('active', mode === 'equal');
  document.getElementById('modeCustomBtn').classList.toggle('active', mode === 'custom');
  document.getElementById('equalSplitSection').classList.toggle('hidden', mode !== 'equal');
  document.getElementById('customSplitSection').classList.toggle('hidden', mode !== 'custom');
  document.getElementById('amountInput').style.display = mode === 'equal' ? '' : 'none';
  document.querySelector('label[for="amountInput"]').style.display = mode === 'equal' ? '' : 'none';
}

// ---------- landing screen: create ----------

document.getElementById('addMemberFieldBtn').addEventListener('click', () => {
  const container = document.getElementById('memberInputs');
  const input = document.createElement('input');
  input.type = 'text';
  input.className = 'member-input';
  input.placeholder = 'Another person';
  container.appendChild(input);
  input.focus();
});

document.getElementById('createGroupBtn').addEventListener('click', async () => {
  const errorEl = document.getElementById('createError');
  errorEl.textContent = '';

  const names = Array.from(document.querySelectorAll('.member-input'))
    .map(i => i.value.trim())
    .filter(v => v.length > 0);

  if (names.length === 0) {
    errorEl.textContent = 'Add at least one person.';
    return;
  }

  try {
    const res = await fetch('/api/group/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ members: names })
    });
    const data = await res.json();
    if (!res.ok) { errorEl.textContent = data.error || 'Could not create group.'; return; }
    await enterGroup(data.code);
  } catch (err) {
    errorEl.textContent = 'Network error — is the server running?';
  }
});

// ---------- landing screen: join ----------

document.getElementById('joinGroupBtn').addEventListener('click', async () => {
  const errorEl = document.getElementById('joinError');
  errorEl.textContent = '';
  const code = document.getElementById('joinCodeInput').value.trim().toUpperCase();
  if (!code) { errorEl.textContent = 'Enter a group code.'; return; }

  const ok = await enterGroup(code);
  if (!ok) errorEl.textContent = 'Group not found — check the code.';
});

// ---------- entering / loading a group ----------

async function enterGroup(code) {
  const data = await loadGroup(code);
  if (!data) return false;

  currentCode = code;
  currentGroup = data;

  const url = new URL(window.location);
  url.searchParams.set('code', code);
  window.history.replaceState({}, '', url);

  renderGroup(data);
  showScreen(groupScreen);
  return true;
}

async function loadGroup(code) {
  try {
    const res = await fetch('/api/group/get?code=' + encodeURIComponent(code));
    if (!res.ok) return null;
    return await res.json();
  } catch (err) {
    return null;
  }
}

// ---------- rendering ----------

function renderGroup(data) {
  document.getElementById('groupCodeDisplay').textContent = data.code;

  const paidBySelect = document.getElementById('paidBySelect');
  paidBySelect.innerHTML = '';
  data.members.forEach(m => {
    const opt = document.createElement('option');
    opt.value = m; opt.textContent = m;
    paidBySelect.appendChild(opt);
  });

  const splitContainer = document.getElementById('splitCheckboxes');
  splitContainer.innerHTML = '';
  data.members.forEach(m => {
    const label = document.createElement('label');
    label.className = 'checkbox-label';
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.value = m;
    cb.checked = true;
    label.appendChild(cb);
    label.appendChild(document.createTextNode(m));
    splitContainer.appendChild(label);
  });

  const customContainer = document.getElementById('customShareInputs');
  customContainer.innerHTML = '';
  data.members.forEach(m => {
    const row = document.createElement('div');
    row.className = 'custom-share-row';
    const nameSpan = document.createElement('span');
    nameSpan.className = 'person-name';
    nameSpan.textContent = m;
    const input = document.createElement('input');
    input.type = 'number';
    input.step = '0.01';
    input.min = '0';
    input.placeholder = '0.00';
    input.dataset.person = m;
    input.addEventListener('input', updateCustomTotal);
    row.appendChild(nameSpan);
    row.appendChild(input);
    customContainer.appendChild(row);
  });

  const balancesList = document.getElementById('balancesList');
  balancesList.innerHTML = '';
  if (data.balances.length === 0) {
    balancesList.innerHTML = '<li class="empty-note">No expenses yet.</li>';
  }
  data.balances.forEach(b => {
    const li = document.createElement('li');
    const amt = b.amount;
    let statusHtml;
    if (amt > 0.001) statusHtml = `<span class="amount-positive">is owed ${CURRENCY}${amt.toFixed(2)}</span>`;
    else if (amt < -0.001) statusHtml = `<span class="amount-negative">owes ${CURRENCY}${Math.abs(amt).toFixed(2)}</span>`;
    else statusHtml = `<span>settled up</span>`;
    li.innerHTML = `<span>${escapeHtml(b.name)}</span>${statusHtml}`;
    balancesList.appendChild(li);
  });

  const expenseList = document.getElementById('expenseList');
  expenseList.innerHTML = '';
  if (data.expenses.length === 0) {
    expenseList.innerHTML = '<li class="empty-note">No expenses logged yet.</li>';
  }
  data.expenses.slice().reverse().forEach(e => {
    const li = document.createElement('li');
    const shareText = e.shares.map(s => `${escapeHtml(s.name)} ${CURRENCY}${s.amount.toFixed(2)}`).join(', ');
    li.innerHTML = `<div class="expense-row">
        <span>${escapeHtml(e.paidBy)} paid ${CURRENCY}${e.amount.toFixed(2)}<br>
        <span class="desc">${escapeHtml(e.description || 'no description')}<br>split: ${shareText}</span></span>
        <button type="button" class="delete-btn" data-id="${e.id}" title="Delete this expense">✕</button>
      </div>`;
    expenseList.appendChild(li);
  });

  document.querySelectorAll('.delete-btn').forEach(btn => {
    btn.addEventListener('click', () => deleteExpense(btn.dataset.id));
  });

  document.getElementById('settlementList').innerHTML = '';
}

function updateCustomTotal() {
  const inputs = document.querySelectorAll('#customShareInputs input');
  let total = 0;
  inputs.forEach(i => { const v = parseFloat(i.value); if (v > 0) total += v; });
  document.getElementById('customTotal').textContent = total.toFixed(2);
}

function escapeHtml(s) {
  const div = document.createElement('div');
  div.textContent = s;
  return div.innerHTML;
}

async function deleteExpense(id) {
  if (!confirm('Delete this expense? This will update everyone\'s balances.')) return;
  try {
    const res = await fetch('/api/group/expense?code=' + encodeURIComponent(currentCode) + '&id=' + encodeURIComponent(id), {
      method: 'DELETE'
    });
    const data = await res.json();
    if (!res.ok) { alert(data.error || 'Could not delete expense.'); return; }
    currentGroup = data;
    renderGroup(data);
  } catch (err) {
    alert('Network error — is the server running?');
  }
}

// ---------- add expense ----------

document.getElementById('addExpenseBtn').addEventListener('click', async () => {
  const errorEl = document.getElementById('expenseError');
  errorEl.textContent = '';

  const paidBy = document.getElementById('paidBySelect').value;
  const description = document.getElementById('descInput').value.trim();

  let requestBody;

  if (splitMode === 'equal') {
    const amount = parseFloat(document.getElementById('amountInput').value);
    const splitAmong = Array.from(document.querySelectorAll('#splitCheckboxes input:checked')).map(cb => cb.value);
    if (!amount || amount <= 0) { errorEl.textContent = 'Enter a valid amount.'; return; }
    if (splitAmong.length === 0) { errorEl.textContent = 'Select at least one person to split among.'; return; }
    requestBody = { paidBy, amount, description, splitAmong };
  } else {
    const shares = {};
    document.querySelectorAll('#customShareInputs input').forEach(input => {
      const v = parseFloat(input.value);
      if (v > 0) shares[input.dataset.person] = v;
    });
    if (Object.keys(shares).length === 0) { errorEl.textContent = 'Enter at least one person\'s share.'; return; }
    requestBody = { paidBy, description, shares };
  }

  try {
    const res = await fetch('/api/group/expense?code=' + encodeURIComponent(currentCode), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
    });
    const data = await res.json();
    if (!res.ok) { errorEl.textContent = data.error || 'Could not add expense.'; return; }

    currentGroup = data;
    renderGroup(data);
    document.getElementById('amountInput').value = '';
    document.getElementById('descInput').value = '';
    updateCustomTotal();
  } catch (err) {
    errorEl.textContent = 'Network error — is the server running?';
  }
});

// ---------- settle up ----------

document.getElementById('simplifyBtn').addEventListener('click', async () => {
  const list = document.getElementById('settlementList');
  try {
    const res = await fetch('/api/group/simplify?code=' + encodeURIComponent(currentCode));
    const data = await res.json();
    list.innerHTML = '';
    if (!res.ok) {
      list.innerHTML = `<li class="empty-note">${escapeHtml(data.error || 'Could not compute settlement.')}</li>`;
      return;
    }
    if (data.transactions.length === 0) {
      list.innerHTML = '<li class="empty-note">Everyone is already settled up! 🎉</li>';
      return;
    }
    data.transactions.forEach(t => {
      const li = document.createElement('li');
      li.innerHTML = `<span>${escapeHtml(t.from)} → ${escapeHtml(t.to)}</span><span class="amount-negative">${CURRENCY}${t.amount.toFixed(2)}</span>`;
      list.appendChild(li);
    });
  } catch (err) {
    list.innerHTML = '<li class="empty-note">Network error — is the server running?</li>';
  }
});

// ---------- copy invite link ----------

document.getElementById('copyLinkBtn').addEventListener('click', async () => {
  const url = new URL(window.location);
  url.searchParams.set('code', currentCode);
  const linkText = url.toString();

  const copied = await copyToClipboard(linkText);
  const confirmEl = document.getElementById('copyConfirm');

  if (copied) {
    confirmEl.textContent = '✓ link copied — send it to your group';
    confirmEl.classList.remove('hidden');
    setTimeout(() => confirmEl.classList.add('hidden'), 2000);
  } else {
    // Clipboard access isn't available (common over http:// on a non-localhost
    // address, e.g. testing via a phone on the same wifi) -- be honest about
    // it instead of claiming success, and show the link so it can be copied by hand.
    confirmEl.innerHTML = `Couldn't auto-copy (needs https). Select and copy manually:<br>
      <input type="text" class="full-input mono manual-link" readonly value="${escapeHtml(linkText)}">`;
    confirmEl.classList.remove('hidden');
    const input = confirmEl.querySelector('.manual-link');
    input.focus();
    input.select();
  }
});

async function copyToClipboard(text) {
  // Modern API -- only works in a secure context (https:// or localhost).
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (err) {
      // fall through to the legacy method below
    }
  }
  // Legacy fallback -- works in more contexts (older browsers, some non-https
  // pages) even though it's a deprecated API.
  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(textarea);
    return ok;
  } catch (err) {
    return false;
  }
}

// ---------- boot: auto-join if URL has ?code= ----------

(async function boot() {
  const params = new URLSearchParams(window.location.search);
  const code = params.get('code');
  if (code) {
    const ok = await enterGroup(code.toUpperCase());
    if (!ok) showScreen(landingScreen);
  }
})();
