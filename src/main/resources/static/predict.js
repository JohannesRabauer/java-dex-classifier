const video = document.getElementById('video');
const predictBtn = document.getElementById('predictBtn');
const statusEl = document.getElementById('status');
const resultEl = document.getElementById('result');
let cards = [];

async function startCamera() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { width: 1280, height: 720 } });
        video.srcObject = stream;
        predictBtn.disabled = false;
        statusEl.textContent = 'Camera ready.';
    } catch (err) {
        statusEl.textContent = 'No camera: ' + err.message;
    }
}

async function loadCards() {
    cards = await (await fetch('/api/cards')).json();
}

function cardById(id) {
    return cards.find(c => c.id === id) || { title: id, id };
}

predictBtn.addEventListener('click', async () => {
    predictBtn.disabled = true;
    statusEl.textContent = 'Classifying…';
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.9));
    const form = new FormData();
    form.append('image', blob, 'frame.jpg');
    try {
        const res = await fetch('/api/predict', { method: 'POST', body: form });
        if (res.status === 409) {
            statusEl.textContent = 'No model trained yet — run prepare and train first.';
            return;
        }
        if (!res.ok) throw new Error(await res.text());
        const prediction = await res.json();
        show(prediction);
        statusEl.textContent = '';
    } catch (err) {
        statusEl.textContent = 'Prediction failed: ' + err.message;
    } finally {
        predictBtn.disabled = false;
    }
});

function show(prediction) {
    const winner = cardById(prediction.label);
    resultEl.style.display = 'block';
    document.getElementById('winnerImg').src = `/png/${winner.id}.png`;
    document.getElementById('winnerTitle').textContent = winner.title;
    document.getElementById('winnerProb').textContent =
        (prediction.probability * 100).toFixed(1) + '% sure';
    document.getElementById('top3').innerHTML = prediction.top3.map(rank => {
        const card = cardById(rank.label);
        return `<div class="bar-row"><span style="width:130px">${card.title}</span>` +
            `<div class="bar"><div style="width:${(rank.probability * 100).toFixed(1)}%"></div></div>` +
            `<span>${(rank.probability * 100).toFixed(1)}%</span></div>`;
    }).join('');
}

loadCards();
startCamera();
