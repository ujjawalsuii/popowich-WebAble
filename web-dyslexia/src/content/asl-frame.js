/**
 * ASL Frame — runs inside the extension iframe.
 * Gets webcam access, runs MediaPipe Hands detection,
 * and posts hand landmarks to the parent page (content script).
 *
 * All MediaPipe files are bundled locally in lib/mediapipe/
 * to avoid CSP restrictions on external scripts.
 */
(async function () {
    const video = document.getElementById('cam');

    // Resolve the extension base URL for locateFile
    // asl-frame.html is at content/asl-frame.html, inside chrome-extension:// context
    // MediaPipe files are at lib/mediapipe/
    const frameUrl = location.href; // chrome-extension://<id>/content/asl-frame.html
    const contentDir = frameUrl.substring(0, frameUrl.lastIndexOf('/'));
    const mpBase = contentDir.replace('/content', '/lib/mediapipe/');

    console.log('[ASL Frame] frameUrl:', frameUrl);
    console.log('[ASL Frame] mpBase:', mpBase);

    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            video: { width: 320, height: 240, facingMode: 'user' }
        });
        video.srcObject = stream;
        await video.play();
        console.log('[ASL Frame] Camera started');

        /* global Hands, Camera */
        if (typeof Hands === 'undefined') {
            console.error('[ASL Frame] Hands class not found! MediaPipe scripts may not have loaded.');
            return;
        }

        const hands = new Hands({
            locateFile: function (f) {
                const url = mpBase + f;
                console.log('[ASL Frame] locateFile:', f, '->', url);
                return url;
            }
        });
        hands.setOptions({
            maxNumHands: 1,
            modelComplexity: 0,
            minDetectionConfidence: 0.5,
            minTrackingConfidence: 0.4
        });

        let frameCount = 0;
        hands.onResults(function (results) {
            var lms = results.multiHandLandmarks || [];
            frameCount++;
            if (frameCount % 30 === 1) {
                console.log('[ASL Frame] onResults frame #' + frameCount + ', hands detected:', lms.length);
                if (lms.length > 0) {
                    console.log('[ASL Frame] First landmark (wrist):', JSON.stringify(lms[0][0]));
                }
            }
            // Post landmarks to the parent page (content script listens)
            window.parent.postMessage({
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
        console.log('[ASL Frame] MediaPipe Hands + Camera started successfully');
    } catch (err) {
        console.error('[ASL Frame] Failed:', err);
    }
})();
