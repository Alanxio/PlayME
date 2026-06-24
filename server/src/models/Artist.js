const { getDb } = require('../config/db');

class Artist {
    static getAll({ page = 0, size = 50, search = null } = {}) {
        const db = getDb();
        let where = '';
        let params = [];

        if (search) {
            where = 'WHERE LOWER(name) LIKE LOWER(?)';
            params.push(`%${search}%`);
        }

        const stmt = db.prepare(`
            SELECT a.*, COUNT(s.id) as song_count
            FROM artists a
            LEFT JOIN songs s ON s.artist_id = a.id
            ${where}
            GROUP BY a.id
            ORDER BY a.name COLLATE NOCASE
            LIMIT ? OFFSET ?
        `);

        return stmt.all(...params, size, page * size);
    }

    static count() {
        const db = getDb();
        const stmt = db.prepare('SELECT COUNT(*) as total FROM artists');
        return stmt.get().total;
    }

    static getById(id) {
        const db = getDb();
        const stmt = db.prepare('SELECT * FROM artists WHERE id = ?');
        return stmt.get(id);
    }

    static findByName(name) {
        const db = getDb();
        const stmt = db.prepare('SELECT * FROM artists WHERE LOWER(name) = LOWER(?)');
        return stmt.get(name.trim());
    }

    static findOrCreate(name) {
        const trimmed = name.trim();
        if (!trimmed) return null;

        const existing = this.findByName(trimmed);
        if (existing) return existing;

        const db = getDb();
        const stmt = db.prepare('INSERT INTO artists (name) VALUES (?)');
        const result = stmt.run(trimmed);
        return this.getById(result.lastInsertRowid);
    }

    static create(name) {
        return this.findOrCreate(name);
    }

    static update(id, name) {
        const artist = this.getById(id);
        if (!artist) return null;

        const trimmed = name.trim();
        if (!trimmed) return null;

        const db = getDb();
        const stmt = db.prepare('UPDATE artists SET name = ? WHERE id = ?');
        stmt.run(trimmed, id);
        return this.getById(id);
    }

    static delete(id) {
        const db = getDb();

        // Desvincular canciones
        db.prepare('UPDATE songs SET artist_id = NULL WHERE artist_id = ?').run(id);

        // Desvincular álbumes
        db.prepare('UPDATE albums SET artist_id = NULL WHERE artist_id = ?').run(id);

        const stmt = db.prepare('DELETE FROM artists WHERE id = ?');
        stmt.run(id);
        return true;
    }

    static getNames() {
        const db = getDb();
        const stmt = db.prepare('SELECT name FROM artists ORDER BY name COLLATE NOCASE');
        return stmt.all().map(r => r.name);
    }
}

module.exports = Artist;
