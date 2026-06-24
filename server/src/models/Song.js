const { getDb } = require('../config/db');

class Song {
    static getAll({ page = 0, size = 20, artist = null, album = null, search = null } = {}) {
        const db = getDb();
        let where = [];
        let params = [];

        if (artist) {
            where.push('LOWER(ar.name) = LOWER(?)');
            params.push(artist);
        }
        if (album) {
            where.push('LOWER(al.title) = LOWER(?)');
            params.push(album);
        }
        if (search) {
            where.push('(LOWER(s.title) LIKE LOWER(?) OR LOWER(ar.name) LIKE LOWER(?) OR LOWER(al.title) LIKE LOWER(?))');
            const like = `%${search}%`;
            params.push(like, like, like);
        }

        const whereClause = where.length > 0 ? `WHERE ${where.join(' AND ')}` : '';
        const start = page * size;
        params.push(size, start);

        const stmt = db.prepare(`
            SELECT s.*, ar.name as artist, al.title as album
            FROM songs s
            LEFT JOIN artists ar ON ar.id = s.artist_id
            LEFT JOIN albums al ON al.id = s.album_id
            ${whereClause}
            ORDER BY s.id
            LIMIT ? OFFSET ?
        `);

        return stmt.all(...params);
    }

    static count({ artist = null, album = null, search = null } = {}) {
        const db = getDb();
        let where = [];
        let params = [];

        if (artist) {
            where.push('LOWER(ar.name) = LOWER(?)');
            params.push(artist);
        }
        if (album) {
            where.push('LOWER(al.title) = LOWER(?)');
            params.push(album);
        }
        if (search) {
            where.push('(LOWER(s.title) LIKE LOWER(?) OR LOWER(ar.name) LIKE LOWER(?) OR LOWER(al.title) LIKE LOWER(?))');
            const like = `%${search}%`;
            params.push(like, like, like);
        }

        const whereClause = where.length > 0 ? `WHERE ${where.join(' AND ')}` : '';
        const stmt = db.prepare(`
            SELECT COUNT(*) as total FROM songs s
            LEFT JOIN artists ar ON ar.id = s.artist_id
            LEFT JOIN albums al ON al.id = s.album_id
            ${whereClause}
        `);
        const row = stmt.get(...params);
        return row.total;
    }

    static getById(id) {
        const db = getDb();
        const stmt = db.prepare(`
            SELECT s.*, ar.name as artist, al.title as album
            FROM songs s
            LEFT JOIN artists ar ON ar.id = s.artist_id
            LEFT JOIN albums al ON al.id = s.album_id
            WHERE s.id = ?
        `);
        return stmt.get(id);
    }

    static getArtists() {
        const db = getDb();
        const stmt = db.prepare(`
            SELECT DISTINCT ar.name FROM songs s
            JOIN artists ar ON ar.id = s.artist_id
            WHERE ar.name IS NOT NULL
            ORDER BY ar.name COLLATE NOCASE
        `);
        return stmt.all().map(r => r.name);
    }

    static getAlbums() {
        const db = getDb();
        const stmt = db.prepare(`
            SELECT DISTINCT al.title FROM songs s
            JOIN albums al ON al.id = s.album_id
            WHERE al.title IS NOT NULL
            ORDER BY al.title COLLATE NOCASE
        `);
        return stmt.all().map(r => r.title);
    }

    static create({ title, artistId, albumId, duration, cover, filename, mimeType }) {
        const db = getDb();
        const stmt = db.prepare(`
            INSERT INTO songs (title, artist_id, album_id, duration, cover, filename, mime_type)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `);
        const result = stmt.run(title, artistId || null, albumId || null, duration || 0, cover || null, filename, mimeType || null);
        return this.getById(result.lastInsertRowid);
    }

    static update(id, { title, artistId, albumId, duration, cover, filename, mimeType }) {
        const song = this.getById(id);
        if (!song) return null;

        const db = getDb();
        const stmt = db.prepare(`
            UPDATE songs
            SET title = ?, artist_id = ?, album_id = ?, duration = ?, cover = ?, filename = ?, mime_type = ?
            WHERE id = ?
        `);
        stmt.run(
            title !== undefined ? title : song.title,
            artistId !== undefined ? artistId : song.artist_id,
            albumId !== undefined ? albumId : song.album_id,
            duration !== undefined ? duration : song.duration,
            cover !== undefined ? cover : song.cover,
            filename !== undefined ? filename : song.filename,
            mimeType !== undefined ? mimeType : song.mime_type,
            id
        );
        return this.getById(id);
    }

    static delete(id) {
        const db = getDb();
        const stmt = db.prepare('DELETE FROM songs WHERE id = ?');
        stmt.run(id);
        return true;
    }
}

module.exports = Song;
