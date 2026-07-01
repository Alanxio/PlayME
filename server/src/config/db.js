const fs = require('fs');
const path = require('path');
const initSqlJs = require('sql.js');

const dataDir = path.join(__dirname, '..', '..', 'data');
const dbPath = path.join(dataDir, 'database.sqlite');

let SQL = null;
let db = null;
let initialized = false;

async function initialize() {
    if (initialized) return;

    SQL = await initSqlJs();

    if (!fs.existsSync(dataDir)) {
        fs.mkdirSync(dataDir, { recursive: true });
    }

    if (fs.existsSync(dbPath)) {
        const fileBuffer = fs.readFileSync(dbPath);
        db = new SQL.Database(fileBuffer);
    } else {
        db = new SQL.Database();
    }

    db.run(`
        CREATE TABLE IF NOT EXISTS artists (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS albums (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            artist_id INTEGER,
            cover TEXT
        );

        CREATE TABLE IF NOT EXISTS songs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            artist_id INTEGER,
            album_id INTEGER,
            duration INTEGER DEFAULT 0,
            cover TEXT,
            filename TEXT NOT NULL,
            mime_type TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS song_files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            song_id INTEGER NOT NULL,
            quality INTEGER NOT NULL,
            filename TEXT NOT NULL,
            file_size INTEGER DEFAULT 0,
            UNIQUE(song_id, quality)
        );

        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL
        );
    `);

    // Migraciones progresivas
    addColumnIfNotExists('songs', 'artist_id', 'INTEGER');
    addColumnIfNotExists('songs', 'album_id', 'INTEGER');
    addColumnIfNotExists('songs', 'mime_type', 'TEXT');
    addColumnIfNotExists('albums', 'cover', 'TEXT');

    saveDatabase();
    initialized = true;
}

function addColumnIfNotExists(table, column, type) {
    try {
        const stmt = db.prepare(`PRAGMA table_info(${table})`);
        const columns = [];
        while (stmt.step()) {
            columns.push(stmt.getAsObject().name);
        }
        stmt.free();

        if (!columns.includes(column)) {
            db.run(`ALTER TABLE ${table} ADD COLUMN ${column} ${type}`);
        }
    } catch (err) {
        console.error(`Error verificando/añadiendo columna ${column}:`, err.message);
    }
}

function saveDatabase() {
    if (!db) return;
    const data = db.export();
    fs.writeFileSync(dbPath, Buffer.from(data));
}

function getDb() {
    if (!initialized || !db) {
        throw new Error('La base de datos no ha sido inicializada. Llama a initialize() primero.');
    }
    return {
        exec: (sql) => {
            db.run(sql);
            saveDatabase();
        },
        prepare: (sql) => createStatement(sql),
        save: saveDatabase
    };
}

function createStatement(sql) {
    const stmt = db.prepare(sql);

    return {
        run: (...params) => {
            stmt.bind(flattenParams(params));
            stmt.step();
            stmt.reset();
            const lastInsertRowid = getLastInsertRowid();
            saveDatabase();
            return { lastInsertRowid };
        },
        get: (...params) => {
            stmt.bind(flattenParams(params));
            const hasRow = stmt.step();
            let row = null;
            if (hasRow) {
                row = stmt.getAsObject();
            }
            stmt.reset();
            return row;
        },
        all: (...params) => {
            stmt.bind(flattenParams(params));
            const rows = [];
            while (stmt.step()) {
                rows.push(stmt.getAsObject());
            }
            stmt.reset();
            return rows;
        },
        free: () => {
            stmt.free();
        }
    };
}

function getLastInsertRowid() {
    const stmt = db.prepare('SELECT last_insert_rowid() as id');
    stmt.step();
    const row = stmt.getAsObject();
    stmt.free();
    return row.id;
}

function flattenParams(params) {
    if (params.length === 1 && Array.isArray(params[0])) {
        return params[0];
    }
    return params;
}

module.exports = {
    initialize,
    getDb
};
