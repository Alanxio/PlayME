const bcrypt = require('bcryptjs');
const fs = require('fs');
const path = require('path');
const { getDb } = require('./db');
const Song = require('../models/Song');
const Artist = require('../models/Artist');
const Album = require('../models/Album');
const { ensureUploadDirs, MUSIC_DIR, COVERS_DIR, getMimeTypeFromFilename } = require('../utils/fileHelper');

function createAdminUser() {
    const db = getDb();
    const username = process.env.ADMIN_USER || 'admin';
    const password = process.env.ADMIN_PASS || 'admin123';

    const existing = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
    if (existing) return;

    const hash = bcrypt.hashSync(password, 10);
    db.prepare('INSERT INTO users (username, password_hash) VALUES (?, ?)').run(username, hash);
    console.log(`Usuario admin creado: ${username}`);
}

function migrateExistingSongs() {
    // Migración de versiones antiguas desactivada por cambio de esquema.
    // Si es necesario, se debe recrear la base de datos.
}

function migrateCatalogJson() {
    const catalogPath = path.join(__dirname, '..', '..', 'catalog.json');
    if (!fs.existsSync(catalogPath)) return;

    const existingSongs = Song.count();
    if (existingSongs > 0) {
        console.log('La base de datos ya contiene canciones. Saltando migración de catalog.json.');
        return;
    }

    try {
        const data = fs.readFileSync(catalogPath, 'utf8');
        const catalog = JSON.parse(data);
        if (!Array.isArray(catalog) || catalog.length === 0) return;

        console.log(`Migrando ${catalog.length} canciones desde catalog.json...`);

        for (const item of catalog) {
            const filename = item.url || `song${item.id}.mp3`;
            const mimeType = getMimeTypeFromFilename(filename);

            let artistId = null;
            let albumId = null;

            if (item.artist && item.artist.trim()) {
                const artist = Artist.findOrCreate(item.artist.trim());
                artistId = artist.id;
            }

            if (item.album && item.album.trim()) {
                const album = Album.findOrCreate(item.album.trim(), artistId);
                albumId = album.id;
            }

            Song.create({
                title: item.title || 'Sin título',
                artistId,
                albumId,
                duration: item.duration || 0,
                cover: item.cover || null,
                filename,
                mimeType
            });
        }

        const backupPath = path.join(__dirname, '..', '..', 'catalog.json.migrated');
        fs.renameSync(catalogPath, backupPath);
        console.log(`Migración completada. Backup: ${backupPath}`);
    } catch (err) {
        console.error('Error migrando catalog.json:', err.message);
    }
}

function init() {
    ensureUploadDirs();
    createAdminUser();
    migrateCatalogJson();
    migrateExistingSongs();
}

module.exports = { init };
