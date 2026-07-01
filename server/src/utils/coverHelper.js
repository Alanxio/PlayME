const fs = require('fs');
const path = require('path');
const Album = require('../models/Album');
const { COVERS_DIR } = require('./fileHelper');

const DEFAULT_COVER = process.env.DEFAULT_COVER || 'default.jpg';

function coverFileExists(filename) {
    if (!filename) return false;
    return fs.existsSync(path.join(COVERS_DIR, filename));
}

function resolveSongCover(song) {
    // 1. Cover propio de la canción
    if (song.cover && coverFileExists(song.cover)) {
        return {
            filename: song.cover,
            origin: 'own',
            label: 'Cover propio de la canción'
        };
    }

    // 2. Cover del álbum
    if (song.album_id) {
        const album = Album.getById(song.album_id);
        if (album && album.cover && coverFileExists(album.cover)) {
            return {
                filename: album.cover,
                origin: 'album',
                label: `Cover heredado del álbum: ${album.title}`
            };
        }
    }

    return {
        filename: DEFAULT_COVER,
        origin: 'default',
        label: 'Cover por defecto'
    };
}

function getCoverIcon(origin) {
    switch (origin) {
        case 'own': return '🖼️';
        case 'album': return '📀';
        case 'default': return '❓';
        default: return '❌';
    }
}

module.exports = {
    resolveSongCover,
    getCoverIcon,
    DEFAULT_COVER
};
