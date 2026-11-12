const $ = id => document.getElementById(id);
const video = $('video');
const statusEl = $('status');

// the model was trained on portrait 3:4 pictures of this size, mostly card: the window inside the guide box
const FRAME_WIDTH = 240;
const FRAME_HEIGHT = 320;
const DETECT_EVERY_MS = 600;

let cards = [];
let cameraReady = false;
let modelReady = false;
let detecting = false;
let polling = null;

const cardById = id => cards.find(c => c.id === id) || { id, title: id };

async function startCamera() {
    try {
        video.srcObject = await navigator.mediaDevices.getUserMedia({ video: { width: 1280, height: 720 } });
        cameraReady = true;
        statusEl.textContent = 'Camera ready.';
    } catch (err) {
        statusEl.textContent = 'No camera: ' + err.message;
    }
}

// ---------------------------------------------------------------- training

async function refreshStatus() {
    const res = await fetch('/api/jobs/status');
    const s = await res.json();
    const job = s.job;
    const working = job.state === 'running' || job.state === 'queued';

    modelReady = s.modelReady;
    $('trainPanel').classList.toggle('hidden', modelReady && !working && job.state !== 'failed');
    $('progressWrap').classList.toggle('hidden', !working);
    $('trainBtn').disabled = working;
    $('retrain').classList.toggle('hidden', !modelReady || working);

    if (working) {
        $('trainTitle').textContent = 'Training…';
        const fraction = job.total > 0 ? job.done / job.total : 0;
        $('progressBar').style.width = (fraction * 100).toFixed(0) + '%';
        $('phase').textContent = `${job.phase} (${job.done}/${job.total})`;
        $('elapsed').textContent = formatSeconds(job.elapsedMillis / 1000);
        startPolling();
    } else {
        stopPolling();
        if (job.state === 'failed') {
            $('trainTitle').textContent = 'Training failed';
            $('trainText').textContent = job.message || 'See the JobRunr dashboard for the log.';
            $('elapsed').textContent = '';
        } else if (!modelReady) {
            $('trainTitle').textContent = 'No model yet';
        }
    }

    if (modelReady && s.accuracy != null) {
        $('modelInfo').textContent =
            `Model: ${(s.accuracy * 100).toFixed(1)} % right on ${s.pictures ? 'drawn' : ''} pictures it never saw, ` +
            `trained in ${formatSeconds(s.totalSeconds)} ` +
            `(${formatSeconds(s.embedSeconds)} drawing and embedding, ${formatSeconds(s.trainSeconds)} training).`;
    }
    return s;
}

function formatSeconds(seconds) {
    if (seconds == null) return '';
    const s = Math.round(seconds);
    return s >= 60 ? `${Math.floor(s / 60)} min ${s % 60} s` : `${s} s`;
}

function startPolling() {
    if (!polling) polling = setInterval(refreshStatus, 1000);
}

function stopPolling() {
    clearInterval(polling);
    polling = null;
}

$('trainBtn').addEventListener('click', async () => {
    $('trainBtn').disabled = true;
    $('trainText').textContent = 'Drawing pictures of every card, then fitting one layer on top of ResNet18.';
    await fetch('/api/jobs/train', { method: 'POST' });
    await refreshStatus();
});

$('retrain').addEventListener('click', () => {
    $('trainPanel').classList.remove('hidden');
    $('trainTitle').textContent = 'Train again';
});

// ---------------------------------------------------------------- detection

async function detectLoop() {
    if (modelReady && cameraReady && !detecting && video.videoWidth > 0) {
        const canvas = document.createElement('canvas');
        // the central 3:4 window of the camera picture, the same one the guide box marks on the page
        const wide = video.videoWidth * 4 >= video.videoHeight * 3;
        const cropW = wide ? video.videoHeight * 3 / 4 : video.videoWidth;
        const cropH = wide ? video.videoHeight : video.videoWidth * 4 / 3;
        canvas.width = FRAME_WIDTH;
        canvas.height = FRAME_HEIGHT;
        canvas.getContext('2d').drawImage(video, (video.videoWidth - cropW) / 2, (video.videoHeight - cropH) / 2,
            cropW, cropH, 0, 0, FRAME_WIDTH, FRAME_HEIGHT);
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85));
        await detect(blob);
    }
    setTimeout(detectLoop, DETECT_EVERY_MS);
}

async function detect(blob) {
    detecting = true;
    const form = new FormData();
    form.append('image', blob, 'frame.jpg');
    try {
        const res = await fetch('/api/predict', { method: 'POST', body: form });
        if (res.status === 409) {
            modelReady = false;
            await refreshStatus();
            return;
        }
        if (!res.ok) throw new Error(await res.text());
        show(await res.json());
        statusEl.textContent = 'Looking…';
    } catch (err) {
        statusEl.textContent = 'Detection failed: ' + err.message;
    } finally {
        detecting = false;
    }
}

function show(prediction) {
    $('resultPanel').classList.remove('hidden');
    $('sure').classList.toggle('hidden', prediction.unsure);
    $('unsure').classList.toggle('hidden', !prediction.unsure);
    if (!prediction.unsure) {
        const winner = cardById(prediction.label);
        $('winnerImg').src = `/png/${winner.id}.png`;
        $('winnerTitle').textContent = winner.title;
        $('winnerProb').textContent = (prediction.probability * 100).toFixed(0) + ' % sure';
    }
    $('top3').innerHTML = prediction.top3.map(rank =>
        `<div class="bar-row"><span style="width:150px">${cardById(rank.label).title}</span>` +
        `<div class="bar"><div style="width:${(rank.probability * 100).toFixed(1)}%"></div></div>` +
        `<span>${(rank.probability * 100).toFixed(0)}%</span></div>`).join('');
}

$('file').addEventListener('change', async event => {
    const file = event.target.files[0];
    if (file && modelReady) await detect(file);
});

(async () => {
    cards = await (await fetch('/api/cards')).json();
    await refreshStatus();
    startCamera();
    detectLoop();
})();
