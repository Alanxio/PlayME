const { getDb } = require('../config/db');

const MAX_RMS_SIZE = 1 * 1024 * 1024; // 1 MB límite del Nokia
const QUALITIES = [32, 48, 64, 96, 128];

class SongFile {
    static getQualities() {
        return QUALITIES;
    }

    static getMaxRmsSize() {
        return MAX_RMS_SIZE;
    }

    /**
     * Calcula el tamaño estimado en bytes para una calidad y duración.
     */
    static estimateSize(qualityKbps, durationSeconds) {
        return Math.round((qualityKbps * 1000 * durationSeconds) / 8);
    }

    /**
     * Devuelve las calidades que caben en el límite RMS para una duración dada.
     */
    static getFittingQualities(durationSeconds) {
        return QUALITIES.filter(q => this.estimateSize(q, durationSeconds) <= MAX_RMS_SIZE);
    }

    static create(songId, quality, filename, fileSize) {
        const db = getDb();
        const stmt = db.prepare(`
            INSERT OR REPLACE INTO song_files (song_id, quality, filename, file_size)
            VALUES (?, ?, ?, ?)
        `);
        return stmt.run(songId, quality, filename, fileSize || 0);
    }

    static getBySongAndQuality(songId, quality) {
        const db = getDb();
        const stmt = db.prepare('SELECT * FROM song_files WHERE song_id = ? AND quality = ?');
        return stmt.get(songId, quality);
    }

    static getAllBySong(songId) {
        const db = getDb();
        const stmt = db.prepare('SELECT * FROM song_files WHERE song_id = ? ORDER BY quality ASC');
        return stmt.all(songId);
    }

    /**
     * Devuelve la mejor calidad disponible para una canción:
     * - exacta si existe,
     * - si no, la mayor disponible menor o igual a la pedida,
     * - si no hay menor o igual, la menor disponible.
     */
    static getBestForSong(songId, requestedQuality) {
        const files = this.getAllBySong(songId);
        if (files.length === 0) return null;

        const qualities = files.map(f => f.quality);

        // Exacta
        const exact = files.find(f => f.quality === requestedQuality);
        if (exact) return exact;

        // Mayor disponible menor o igual a la pedida
        const lowerOrEqual = files
            .filter(f => f.quality <= requestedQuality)
            .sort((a, b) => b.quality - a.quality);
        if (lowerOrEqual.length > 0) return lowerOrEqual[0];

        // Fallback: la menor disponible
        return files.sort((a, b) => a.quality - b.quality)[0];
    }

    static deleteBySong(songId) {
        const db = getDb();
        const stmt = db.prepare('DELETE FROM song_files WHERE song_id = ?');
        return stmt.run(songId);
    }

    static delete(songId, quality) {
        const db = getDb();
        const stmt = db.prepare('DELETE FROM song_files WHERE song_id = ? AND quality = ?');
        return stmt.run(songId, quality);
    }
}

module.exports = SongFile;
