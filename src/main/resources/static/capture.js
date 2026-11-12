const video = document.getElementById('video');
const canvas = document.getElementById('canvas');
const captureBtn = document.getElementById('captureBtn');
const statusEl = document.getElementById('status');
const grid = document.getElementById('cardGrid');

let selectedCard = null;
const counts = {};

async function startCamera() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { width: 1280, height: 720 } });
        video.srcObject = stream;
        captureBtn.disabled = false;
        statusEl.textContent = 'Camera ready.';
    } catch (err) {
        statusEl.textContent = 'No camera: ' + err.message;
    }
}

async function loadCards() {
    const cards = await (await fetch('/api/cards')).json();
    for (const card of cards) {
        const div = document.createElement('div');
        div.className = 'card';
        div.dataset.id = card.id;
        const img = document.createElement('img');
        img.src = `/png/${card.id}.png`;
        img.alt = card.title;
        img.title = card.title;
        const count = document.createElement('span');
        count.className = 'count';
        count.textContent = '0';
        div.append(img, count);
        div.addEventListener('click', () => selectCard(div, card));
        grid.append(div);
    }
    const first = grid.querySelector('.card');
    if (first) selectCard(first, cards[0]);
}

function selectCard(div, card) {
    grid.querySelectorAll('.card').forEach(el => el.classList.remove('selected'));
    div.classList.add('selected');
    selectedCard = card;
    updateStatus();
}

function updateStatus() {
    if (selectedCard) {
        statusEl.textContent = `${selectedCard.title}: ${counts[selectedCard.id] || 0} captured`;
    }
}

captureBtn.addEventListener('click', async () => {
    if (!selectedCard) return;
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.9));
    const form = new FormData();
    form.append('image', blob, 'frame.jpg');
    try {
        const res = await fetch(`/api/captures/${selectedCard.id}`, { method: 'POST', body: form });
        if (!res.ok) throw new Error(await res.text());
        const data = await res.json();
        counts[selectedCard.id] = data.count;
        const badge = grid.querySelector(`.card[data-id="${selectedCard.id}"] .count`);
        if (badge) badge.textContent = data.count;
        updateStatus();
    } catch (err) {
        statusEl.textContent = 'Upload failed: ' + err.message;
    }
});

loadCards();
startCamera();
