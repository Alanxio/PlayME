const fs = require('fs');
const path = require('path');

const UPLOADS_DIR = path.join(__dirname, '..', '..', 'uploads');
const MUSIC_DIR = path.join(UPLOADS_DIR, 'music');
const COVERS_DIR = path.join(UPLOADS_DIR, 'covers');
const TMP_COVERS_DIR = path.join(UPLOADS_DIR, 'tmp-covers');

function ensureUploadDirs() {
    [MUSIC_DIR, COVERS_DIR, TMP_COVERS_DIR].forEach(dir => {
        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }
    });
}

function generateUniqueFilename(prefix, originalName) {
    const ext = path.extname(originalName).toLowerCase() || '';
    const timestamp = Date.now();
    const random = Math.floor(Math.random() * 10000);
    return `${prefix}-${timestamp}-${random}${ext}`;
}

function safeDelete(filename, dir) {
    if (!filename) return;
    const filepath = path.join(dir, filename);
    if (fs.existsSync(filepath)) {
        try {
            fs.unlinkSync(filepath);
        } catch (err) {
            console.error(`Error borrando archivo ${filepath}:`, err.message);
        }
    }
}

function deleteSongFiles(song, qualityFiles) {
    safeDelete(song.filename, MUSIC_DIR);
    if (song.cover) {
        safeDelete(song.cover, COVERS_DIR);
    }
    if (qualityFiles) {
        for (let i = 0; i < qualityFiles.length; i++) {
            safeDelete(qualityFiles[i], MUSIC_DIR);
        }
    }
}

function getMimeTypeFromFilename(filename) {
    const ext = path.extname(filename).toLowerCase();
    const mimeTypes = {
        '.mp3': 'audio/mpeg',
        '.wav': 'audio/wav',
        '.aac': 'audio/aac',
        '.m4a': 'audio/aac',
        '.amr': 'audio/amr',
        '.mid': 'audio/midi',
        '.midi': 'audio/midi'
    };
    return mimeTypes[ext] || 'audio/mpeg';
}

module.exports = {
    UPLOADS_DIR,
    MUSIC_DIR,
    COVERS_DIR,
    TMP_COVERS_DIR,
    ensureUploadDirs,
    generateUniqueFilename,
    safeDelete,
    deleteSongFiles,
    getMimeTypeFromFilename
};
