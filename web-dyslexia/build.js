/**
 * ScreenShield build script
 * Usage: node build.js [chrome|firefox]
 *
 * Outputs:
 *   dist/chrome/   + dist/chrome.zip
 *   dist/firefox/  + dist/firefox.zip
 */

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const archiver = require('archiver');

const TARGET = process.argv[2];
if (!['chrome', 'firefox'].includes(TARGET)) {
  console.error('Usage: node build.js [chrome|firefox]');
  process.exit(1);
}

const ROOT = __dirname;
const SRC  = path.join(ROOT, 'src');
const DIST = path.join(ROOT, 'dist', TARGET);

// Platform-specific manifest deltas merged on top of base
const PLATFORM_DELTAS = {
  chrome: {
    background: {
      service_worker: 'background/background.js'
    }
  },
  firefox: {
    background: {
      scripts: ['background/background.js']
    },
    browser_specific_settings: {
      gecko: {
        id: 'screenshield@hackathon.dev',
        strict_min_version: '109.0'
      }
    }
  }
};

async function copyDir(src, dest) {
  await fsp.mkdir(dest, { recursive: true });
  const entries = await fsp.readdir(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath  = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      await copyDir(srcPath, destPath);
    } else {
      await fsp.copyFile(srcPath, destPath);
    }
  }
}

function createZip(dir, outPath) {
  return new Promise((resolve, reject) => {
    const output  = fs.createWriteStream(outPath);
    const archive = archiver('zip', { zlib: { level: 9 } });
    output.on('close', resolve);
    archive.on('error', reject);
    archive.pipe(output);
    archive.directory(dir, false);
    archive.finalize();
  });
}

async function build() {
  // Clean and recreate dist/target
  await fsp.rm(DIST, { recursive: true, force: true });
  await fsp.mkdir(DIST, { recursive: true });

  // Copy all src files into dist/target
  await copyDir(SRC, DIST);

  // Merge base manifest with platform delta and write to dist/target/manifest.json
  const base  = JSON.parse(await fsp.readFile(path.join(ROOT, 'manifests', 'manifest.base.json'), 'utf8'));
  const delta = PLATFORM_DELTAS[TARGET];
  const manifest = Object.assign({}, base, delta);

  await fsp.writeFile(
    path.join(DIST, 'manifest.json'),
    JSON.stringify(manifest, null, 2)
  );

  // Also write the final manifest back to manifests/ for reference
  await fsp.writeFile(
    path.join(ROOT, 'manifests', `manifest.${TARGET}.json`),
    JSON.stringify(manifest, null, 2)
  );

  // Create ZIP
  const distRoot = path.join(ROOT, 'dist');
  await fsp.mkdir(distRoot, { recursive: true });
  const zipPath = path.join(distRoot, `${TARGET}.zip`);
  await createZip(DIST, zipPath);

  console.log(`\nScreenShield [${TARGET}] built successfully:`);
  console.log(`  Unpacked : dist/${TARGET}/`);
  console.log(`  Extension: dist/${TARGET}.zip\n`);
}

build().catch(err => {
  console.error(err);
  process.exit(1);
});
