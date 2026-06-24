const multer = require('multer');
const path = require('path');
const { MUSIC_DIR, COVERS_DIR, TMP_COVERS_DIR, generateUniqueFilename } = require('../utils/fileHelper');

const AUDIO_EXTENSIONS = ['.mp3', '.wav', '.aac', '.m4a', '.amr', '.mid', '.midi'];
const AUDIO_MIME_TYPES = [
    'audio/mpeg',
    'audio/wav',
    'audio/x-wav',
    'audio/aac',
    'audio/amr',
    'audio/midi',
    'audio/x-midi'
];

const combinedStorage = multer.diskStorage({
    destination: (req, file, cb) => {
        if (file.fieldname === 'audio') {
            cb(null, MUSIC_DIR);
        } else if (file.fieldname === 'cover') {
            cb(null, COVERS_DIR);
        } else {
            cb(new Error('Campo de archivo no permitido'), null);
        }
    },
    filename: (req, file, cb) => {
        const prefix = file.fieldname === 'audio' ? 'song' : 'cover';
        cb(null, generateUniqueFilename(prefix, file.originalname));
    }
});

const previewStorage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, TMP_COVERS_DIR);
    },
    filename: (req, file, cb) => {
        cb(null, generateUniqueFilename('preview', file.originalname));
    }
});

function audioFileFilter(req, file, cb) {
    const ext = path.extname(file.originalname).toLowerCase();
    const validMime = AUDIO_MIME_TYPES.includes(file.mimetype);
    const validExt = AUDIO_EXTENSIONS.includes(ext);

    if (validMime || validExt) {
        cb(null, true);
    } else {
        cb(new Error('Formato de audio no soportado. Usa: MP3, WAV, AAC, M4A, AMR, MIDI'), false);
    }
}

function combinedFileFilter(req, file, cb) {
    if (file.fieldname === 'audio') {
        audioFileFilter(req, file, cb);
    } else if (file.fieldname === 'cover') {
        const allowed = ['image/jpeg', 'image/png', 'image/jpg'];
        if (allowed.includes(file.mimetype)) {
            cb(null, true);
        } else {
            cb(new Error('Solo se permiten imágenes JPEG o PNG'), false);
        }
    } else {
        cb(new Error('Campo de archivo no permitido'), false);
    }
}

const coverStorage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, COVERS_DIR),
    filename: (req, file, cb) => cb(null, generateUniqueFilename('cover', file.originalname))
});

const uploadSongFiles = multer({
    storage: combinedStorage,
    fileFilter: combinedFileFilter,
    limits: { fileSize: 100 * 1024 * 1024 }
}).fields([
    { name: 'audio', maxCount: 1 },
    { name: 'cover', maxCount: 1 }
]);

const uploadPreviewAudio = multer({
    storage: previewStorage,
    fileFilter: audioFileFilter,
    limits: { fileSize: 100 * 1024 * 1024 }
}).single('audio');

const uploadCover = multer({
    storage: coverStorage,
    fileFilter: (req, file, cb) => {
        const allowed = ['image/jpeg', 'image/png', 'image/jpg'];
        if (allowed.includes(file.mimetype)) {
            cb(null, true);
        } else {
            cb(new Error('Solo se permiten imágenes JPEG o PNG'), false);
        }
    },
    limits: { fileSize: 5 * 1024 * 1024 }
}).single('cover');

module.exports = {
    uploadSongFiles,
    uploadPreviewAudio,
    uploadCover,
    AUDIO_EXTENSIONS
};
