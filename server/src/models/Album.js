const { getDb } = require('../config/db');

class Album {
    static getAll({ page = 0, size = 50, search = null, artistId = null } = {}) {
        const db = getDb();
        let where = [];
        let params = [];

        if (search) {
            where.push('LOWER(a.title) LIKE LOWER(?)');
            params.push(`%${search}%`);
        }
        if (artistId) {
            where.push('a.artist_id = ?');
            params.push(artistId);
        }

        const whereClause = where.length > 0 ? `WHERE ${where.join(' AND ')}` : '';

        const stmt = db.prepare(`
            SELECT a.*, ar.name as artist_name, COUNT(s.id) as song_count
            FROM albums a
            LEFT JOIN artists ar ON ar.id = a.artist_id
            LEFT JOIN songs s ON s.album_id = a.id
            ${whereClause}
            GROUP BY a.id
            ORDER BY a.title COLLATE NOCASE
            LIMIT ? OFFSET ?
        `);

        return stmt.all(...params, size, page * size);
    }

    static count() {
        const db = getDb();
        const stmt = db.prepare('SELECT COUNT(*) as total FROM albums');
        return stmt.get().total;
    }

    static getById(id) {
        const db = getDb();
        const stmt = db.prepare('SELECT * FROM albums WHERE id = ?');
        return stmt.get(id);
    }

    static findByTitleAndArtist(title, artistId) {
        const db = getDb();
        const stmt = db.prepare('SELECT * FROM albums WHERE LOWER(title) = LOWER(?) AND artist_id IS ?');
        return stmt.get(title.trim(), artistId || null);
    }

    static findOrCreate(title, artistId) {
        const trimmed = title.trim();
        if (!trimmed) return null;

        // Buscar álbum con el mismo título y artista específico
        const existing = this.findByTitleAndArtist(trimmed, artistId);
        if (existing) return existing;

        // Si no existe, buscar un álbum con el mismo título pero sin artista
        // y vincularlo al artista actual
        if (artistId) {
            const genericAlbum = this.findByTitleAndArtist(trimmed, null);
            if (genericAlbum) {
                const db = getDb();
                db.prepare('UPDATE albums SET artist_id = ? WHERE id = ?').run(artistId, genericAlbum.id);
                return this.getById(genericAlbum.id);
            }
        }

        const db = getDb();
        const stmt = db.prepare('INSERT INTO albums (title, artist_id) VALUES (?, ?)');
        const result = stmt.run(trimmed, artistId || null);
        return this.getById(result.lastInsertRowid);
    }

    static create(title, artistId) {
        return this.findOrCreate(title, artistId);
    }

    static update(id, title, artistId, cover) {
        const album = this.getById(id);
        if (!album) return null;

        const trimmed = title.trim();
        if (!trimmed) return null;

        const db = getDb();
        const stmt = db.prepare('UPDATE albums SET title = ?, artist_id = ?, cover = ? WHERE id = ?');
        stmt.run(trimmed, artistId || null, cover !== undefined ? cover : album.cover, id);
        return this.getById(id);
    }

    static delete(id) {
        const db = getDb();

        // Desvincular canciones
        db.prepare('UPDATE songs SET album_id = NULL WHERE album_id = ?').run(id);

        const stmt = db.prepare('DELETE FROM albums WHERE id = ?');
        stmt.run(id);
        return true;
    }

    static getTitles() {
        const db = getDb();
        const stmt = db.prepare('SELECT title FROM albums ORDER BY title COLLATE NOCASE');
        return stmt.all().map(r => r.title);
    }

    static getByArtist(artistId) {
        const db = getDb();
        const stmt = db.prepare('SELECT * FROM albums WHERE artist_id = ? ORDER BY title COLLATE NOCASE');
        return stmt.all(artistId);
    }
}

module.exports = Album;
