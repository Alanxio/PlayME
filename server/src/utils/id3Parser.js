const mm = require('music-metadata');
const fs = require('fs');
const path = require('path');
const { generateUniqueFilename, COVERS_DIR, getMimeTypeFromFilename } = require('./fileHelper');

async function parseAudioMetadata(filepath) {
    try {
        const metadata = await mm.parseFile(filepath);
        const common = metadata.common || {};
        const format = metadata.format || {};

        const result = {
            title: common.title || '',
            artist: common.artist || '',
            album: common.album || '',
            duration: format.duration ? Math.round(format.duration) : 0,
            mimeType: format.mimeType || getMimeTypeFromFilename(filepath),
            coverFilename: null
        };

        // Extraer carátula incrustada si existe
        if (common.picture && common.picture.length > 0) {
            const picture = common.picture[0];
            const ext = picture.format ? picture.format.replace('image/', '.') : '.jpg';
            const safeExt = ['.jpg', '.jpeg', '.png'].includes(ext.toLowerCase()) ? ext : '.jpg';
            const coverName = generateUniqueFilename('cover', `embedded${safeExt}`);
            const coverPath = path.join(COVERS_DIR, coverName);
            fs.writeFileSync(coverPath, picture.data);
            result.coverFilename = coverName;
        }

        return result;
    } catch (err) {
        console.error('Error parseando metadatos:', err.message);
        return {
            title: '',
            artist: '',
            album: '',
            duration: 0,
            mimeType: getMimeTypeFromFilename(filepath),
            coverFilename: null
        };
    }
}

module.exports = {
    parseAudioMetadata
};
