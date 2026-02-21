/**
 * ScreenShield ASL Bridge — runs in the PAGE's main world.
 * Loads MediaPipe Hands, processes webcam frames, and posts
 * hand landmark data back to the content script via postMessage.
 */
(async function () {
    const HOST_ID = 'screenshield-asl-host';

    function loadSrc(url) {
        return new Promise((res, rej) => {
            if (document.querySelector('script[src="' + url + '"]')) { res(); return; }
            const s = document.createElement('script');
            s.src = url;
            s.onload = res;
            s.onerror = rej;
            document.head.appendChild(s);
        });
    }

    try {
        await loadSrc('https://cdn.jsdelivr.net/npm/@mediapipe/hands@0.4/hands.min.js');
        await loadSrc('https://cdn.jsdelivr.net/npm/@mediapipe/camera_utils@0.3/camera_utils.min.js');

        const host = document.getElementById(HOST_ID);
        if (!host || !host.shadowRoot) { console.warn('[ASL Bridge] No host element'); return; }
        const video = host.shadowRoot.querySelector('.asl-video');
        if (!video) { console.warn('[ASL Bridge] No video element'); return; }

        /* global Hands, Camera */
        const hands = new Hands({
            locateFile: function (f) {
                return 'https://cdn.jsdelivr.net/npm/@mediapipe/hands@0.4/' + f;
            }
        });
        hands.setOptions({
            maxNumHands: 1,
            modelComplexity: 0,
            minDetectionConfidence: 0.5,
            minTrackingConfidence: 0.4
        });
        hands.onResults(function (results) {
            var lms = results.multiHandLandmarks || [];
            window.postMessage({
                type: 'screenshield-asl-landmarks',
                landmarks: lms
            }, '*');
        });

        var cam = new Camera(video, {
            onFrame: async function () {
                await hands.send({ image: video });
            },
            width: 320,
            height: 240
        });
        cam.start();
        console.log('[ScreenShield ASL] MediaPipe Hands started successfully');
    } catch (err) {
        console.error('[ScreenShield ASL] MediaPipe load failed:', err);
    }
})();
